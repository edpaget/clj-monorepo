(ns bashketball-game-api.services.game
  "Game management service.

  Orchestrates game lifecycle including creation, joining, action submission,
  and completion. Integrates with the [[bashketball-game]] library for game
  state management and validation."
  (:require
   [bashketball-game-api.models.game :as game-model]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.services.catalog :as catalog]
   [bashketball-game-api.services.deck :as deck-svc]
   [bashketball-game.actions :as game-actions]
   [bashketball-game.schema :as game-schema]
   [bashketball-game.state :as game-state]
   [graphql-server.subscriptions :as subs]))

(defn- player-card?
  "Returns true if the card is a player card."
  [card]
  (= :card-type/PLAYER_CARD (:card-type card)))

(defn- size-keyword
  "Converts card size enum to game engine size keyword."
  [size]
  (case size
    :size/SM :small
    :size/MD :mid
    :size/LG :big
    :mid))

(defn deck->game-config
  "Converts a validated deck to bashketball-game config format.

  Takes a card catalog and deck record, returns a config map suitable for
  [[bashketball-game.state/create-game]] with `:deck` (action card slugs)
  and `:players` (player specs with stats)."
  [card-catalog deck]
  (let [card-slugs (:card-slugs deck)
        cards      (map #(catalog/get-card card-catalog %) card-slugs)
        players    (filter player-card? cards)
        actions    (remove player-card? cards)]
    {:deck    (mapv :slug actions)
     :players (mapv (fn [p]
                      {:card-slug  (:slug p)
                       :name       (:name p)
                       :stats      {:size      (size-keyword (:size p))
                                    :speed     (:speed p)
                                    :shooting  (:sht p)
                                    :passing   (:pss p)
                                    :dribbling (:pss p)
                                    :defense   (:def p)}
                       :abilities  (or (:abilities p) [])})
                    players)}))

(defn- user-team
  "Returns the team (:home or :away) for a user in a game, or nil."
  [game user-id]
  (cond
    (= user-id (:player-1-id game)) :home
    (= user-id (:player-2-id game)) :away
    :else nil))

(defn- user-can-act?
  "Returns true if the user can submit actions for this game."
  [game user-id]
  (let [game-state    (:game-state game)
        ;; Handle both keyword and string keys (JSON serialization)
        active-player (or (:active-player game-state)
                          (get game-state "active-player"))
        active-kw     (if (string? active-player)
                        (keyword active-player)
                        active-player)
        user-team-val (user-team game user-id)]
    (and (= :game-status/active (:status game))
         (= active-kw user-team-val))))

(defn- game-over?
  "Returns true if the game state indicates game over."
  [game-state]
  (let [phase (or (:phase game-state) (get game-state "phase"))]
    (= :game-over (if (string? phase) (keyword phase) phase))))

(def ^:private keyword-values
  "String values that should be converted to keywords when reading game state from JSON."
  #{"home" "away"                                          ; Team
    "setup" "upkeep" "actions" "resolution" "end-of-turn" "game-over" ; Phase
    "small" "mid" "big"                                    ; Size
    "speed" "shooting" "passing" "dribbling" "defense"     ; Stat
    "shot" "pass"                                          ; BallActionType
    "court" "three-point-line" "paint" "hoop"})            ; Terrain

(defn- keywordize-game-state
  "Recursively converts string keys and known enum values to keywords in game state from JSON."
  [m]
  (cond
    (map? m) (into {} (map (fn [[k v]]
                             [(if (string? k) (keyword k) k)
                              (keywordize-game-state v)])
                           m))
    (vector? m) (mapv keywordize-game-state m)
    (and (string? m) (keyword-values m)) (keyword m)
    :else m))

(defprotocol GameService
  "Protocol for game management operations."

  (create-game! [this user-id deck-id]
    "Creates a new game in WAITING status with user as player 1.
     Returns the created game or nil if deck is invalid.")

  (join-game! [this game-id user-id deck-id]
    "Joins an existing WAITING game as player 2, starts the game.
     Returns the updated game or nil on failure.")

  (get-game [this game-id]
    "Returns a game by ID, or nil if not found.")

  (get-game-for-player [this game-id user-id]
    "Returns game if user is a participant, or nil.")

  (list-user-games [this user-id]
    "Returns all games where user is a player.")

  (list-user-games-paginated [this user-id opts]
    "Returns paginated games where user is a player.
     Opts: {:status keyword, :limit int, :offset int}
     Returns: {:data [games] :total-count int}")

  (list-user-games-by-status [this user-id status]
    "Returns games where user is a player with the given status.")

  (list-available-games [this user-id]
    "Returns games in WAITING status available for the user to join.")

  (submit-action! [this game-id user-id action]
    "Validates and applies an action, publishes update to subscribers.
     Returns {:success true :game game} or {:success false :error msg}.")

  (forfeit-game! [this game-id user-id]
    "Forfeits game, opponent wins. Returns updated game or nil.")

  (leave-game! [this game-id user-id]
    "Leaves a WAITING game (cancels it). Returns true if successful."))

(defrecord GameServiceImpl [game-repo deck-service card-catalog subscription-manager]
  GameService

  (create-game! [_this user-id deck-id]
    (when-let [deck (deck-svc/get-deck-for-user deck-service deck-id user-id)]
      (when (:is-valid deck)
        (let [game (proto/create! game-repo
                                  {:player-1-id      user-id
                                   :player-1-deck-id deck-id
                                   :status           :game-status/waiting
                                   :game-state       {}})]
          (subs/publish! subscription-manager [:lobby]
                         {:type :game-created
                          :data {:game-id (str (:id game))}})
          game))))

  (join-game! [_this game-id user-id deck-id]
    (when-let [game (proto/find-by game-repo {:id game-id})]
      (when (and (= :game-status/waiting (:status game))
                 (not= user-id (:player-1-id game)))
        (when-let [deck2 (deck-svc/get-deck-for-user deck-service deck-id user-id)]
          (when (:is-valid deck2)
            (when-let [deck1 (deck-svc/get-deck deck-service (:player-1-deck-id game))]
              (let [home-config   (deck->game-config card-catalog deck1)
                    away-config   (deck->game-config card-catalog deck2)
                    initial-state (game-state/create-game {:home home-config
                                                           :away away-config})
                    updated-game  (game-model/start-game! game-repo
                                                          game-id
                                                          user-id
                                                          deck-id
                                                          initial-state
                                                          (:player-1-id game))]
                (subs/publish! subscription-manager [:game game-id]
                               {:type :player-joined
                                :data {:player-id (str user-id)}})
                (subs/publish! subscription-manager [:lobby]
                               {:type :game-started
                                :data {:game-id (str game-id)}})
                updated-game)))))))

  (get-game [_this game-id]
    (proto/find-by game-repo {:id game-id}))

  (get-game-for-player [_this game-id user-id]
    (when-let [game (proto/find-by game-repo {:id game-id})]
      (when (or (= user-id (:player-1-id game))
                (= user-id (:player-2-id game)))
        game)))

  (list-user-games [_this user-id]
    (game-model/find-by-player game-repo user-id))

  (list-user-games-paginated [_this user-id opts]
    (let [games (game-model/find-by-player game-repo user-id opts)
          total (game-model/count-by-player game-repo user-id
                                            (select-keys opts [:status]))]
      {:data games
       :total-count total}))

  (list-user-games-by-status [_this user-id status]
    (if (= status :game-status/active)
      (game-model/find-active-by-player game-repo user-id)
      (->> (game-model/find-by-player game-repo user-id)
           (filter #(= status (:status %)))
           vec)))

  (list-available-games [_this user-id]
    (->> (game-model/find-waiting-games game-repo)
         (remove #(= user-id (:player-1-id %)))
         vec))

  (submit-action! [this game-id user-id action]
    (if-let [game (get-game-for-player this game-id user-id)]
      (if-not (user-can-act? game user-id)
        {:success false :error "Not your turn"}
        (if-not (game-schema/valid-action? action)
          {:success false :error (str "Invalid action: "
                                      (pr-str (game-schema/explain-action action)))}
          (try
            (let [;; Convert JSON state (string keys) to Clojure state (keyword keys)
                  current-state (keywordize-game-state (:game-state game))
                  new-state     (game-actions/apply-action current-state action)
                  seq-num       (game-model/get-next-sequence-num game-id)
                  _event        (game-model/create-event! game-id
                                                          user-id
                                                          (name (:type action))
                                                          action
                                                          seq-num)
                  updated-game  (if (game-over? new-state)
                                  (let [winner-team (if (> (get-in new-state [:score :home])
                                                           (get-in new-state [:score :away]))
                                                      :home :away)
                                        winner-id   (if (= winner-team :home)
                                                      (:player-1-id game)
                                                      (:player-2-id game))]
                                    (game-model/update-game-state! game-repo game-id new-state)
                                    (game-model/end-game! game-repo game-id
                                                          :game-status/completed winner-id))
                                  (game-model/update-game-state! game-repo game-id new-state))]
              (subs/publish! subscription-manager [:game game-id]
                             {:type :state-changed
                              :data {:game-id (str game-id)}})
              {:success true :game updated-game})
            (catch Exception e
              {:success false :error (ex-message e)}))))
      {:success false :error "Game not found or not a participant"}))

  (forfeit-game! [this game-id user-id]
    (when-let [game (get-game-for-player this game-id user-id)]
      (when (= :game-status/active (:status game))
        (let [winner-id (if (= user-id (:player-1-id game))
                          (:player-2-id game)
                          (:player-1-id game))
              updated   (game-model/end-game! game-repo game-id
                                              :game-status/completed winner-id)]
          (subs/publish! subscription-manager [:game game-id]
                         {:type :game-ended
                          :data {:game-id   (str game-id)
                                 :winner-id (str winner-id)
                                 :reason    "forfeit"}})
          updated))))

  (leave-game! [this game-id user-id]
    (when-let [game (get-game-for-player this game-id user-id)]
      (when (and (= :game-status/waiting (:status game))
                 (= user-id (:player-1-id game)))
        (let [deleted (proto/delete! game-repo game-id)]
          (when deleted
            (subs/publish! subscription-manager [:lobby]
                           {:type :game-cancelled
                            :data {:game-id (str game-id)}}))
          deleted)))))

(defn create-game-service
  "Creates a new GameService instance."
  [game-repo deck-service card-catalog subscription-manager]
  (->GameServiceImpl game-repo deck-service card-catalog subscription-manager))
