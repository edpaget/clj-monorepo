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
                       :stats      {:size      (:size p)
                                    :speed     (:speed p)
                                    :shooting  (:sht p)
                                    :passing   (:pss p)
                                    :dribbling (:pss p)
                                    :defense   (:def p)}
                       :abilities  (or (:abilities p) [])})
                    players)}))

(defn- user-team
  "Returns the team (:team/HOME or :team/AWAY) for a user in a game, or nil.

  Uses namespaced keywords to match the game engine's Team enum values."
  [game user-id]
  (cond
    (= user-id (:player-1-id game)) :team/HOME
    (= user-id (:player-2-id game)) :team/AWAY
    :else nil))

(defn- get-phase
  "Returns the current phase as a keyword."
  [game-state]
  (let [phase (or (:phase game-state) (get game-state "phase"))]
    (if (string? phase) (keyword phase) phase)))

(defn- user-can-act?
  "Returns true if the user can submit actions for this game.

  During TIP_OFF phase, both players can act simultaneously. In all other phases,
  only the active player can act."
  [game user-id]
  (let [game-state    (:game-state game)
        phase         (get-phase game-state)
        user-team-val (user-team game user-id)]
    (and (= :game-status/ACTIVE (:status game))
         (some? user-team-val)
         (or (= :phase/TIP_OFF phase)
             (let [active-player (or (:active-player game-state)
                                     (get game-state "active-player"))
                   active-kw     (if (string? active-player)
                                   (keyword active-player)
                                   active-player)]
               (= active-kw user-team-val))))))

(defn- game-over?
  "Returns true if the game state indicates game over."
  [game-state]
  (let [phase (or (:phase game-state) (get game-state "phase"))]
    (= :phase/GAME_OVER (if (string? phase) (keyword phase) phase))))

(defn- collect-deck-slugs
  "Extracts all unique card slugs from a deck state."
  [deck]
  (->> (concat (:draw-pile deck)
               (:hand deck)
               (:discard deck)
               (:removed deck))
       (map :card-slug)
       distinct))

(defn- collect-player-attachment-slugs
  "Extracts card slugs from player attachments."
  [team-roster]
  (->> (vals (:players team-roster))
       (mapcat :attachments)
       (keep :card-slug)))

(defn- collect-asset-slugs
  "Extracts card slugs from team assets."
  [assets]
  (keep :card-slug assets))

(defn- collect-extra-slugs
  "Collects card slugs from play area, assets, and attachments."
  [game-state]
  (let [home-assets (get-in game-state [:players :team/HOME :assets] [])
        away-assets (get-in game-state [:players :team/AWAY :assets] [])
        home-roster (get-in game-state [:players :team/HOME :team])
        away-roster (get-in game-state [:players :team/AWAY :team])
        play-area   (get game-state :play-area [])]
    (concat (collect-asset-slugs home-assets)
            (collect-asset-slugs away-assets)
            (collect-player-attachment-slugs home-roster)
            (collect-player-attachment-slugs away-roster)
            (map :card-slug play-area))))

(defn- hydrate-deck
  "Adds cards field to deck state with full card data from catalog."
  [deck card-catalog]
  (let [slugs (collect-deck-slugs deck)
        cards (->> slugs
                   (map #(catalog/get-card card-catalog %))
                   (filter some?)
                   vec)]
    (assoc deck :cards cards)))

(defn- hydrate-game-state
  "Hydrates deck cards in game state for action processing.

  The game engine needs card data (including card-type) to determine
  how to handle played cards (e.g., team assets vs regular cards).
  Includes cards from play area, assets, and player attachments."
  [game-state card-catalog]
  (let [extra-slugs (collect-extra-slugs game-state)
        extra-cards (->> extra-slugs
                         (map #(catalog/get-card card-catalog %))
                         (filter some?)
                         vec)
        home-deck   (get-in game-state [:players :team/HOME :deck])
        away-deck   (get-in game-state [:players :team/AWAY :deck])
        home-cards  (:cards (hydrate-deck home-deck card-catalog))
        away-cards  (:cards (hydrate-deck away-deck card-catalog))
        home-all    (->> (concat home-cards extra-cards)
                         (filter some?)
                         distinct
                         vec)
        away-all    (->> (concat away-cards extra-cards)
                         (filter some?)
                         distinct
                         vec)]
    (-> game-state
        (assoc-in [:players :team/HOME :deck :cards] home-all)
        (assoc-in [:players :team/AWAY :deck :cards] away-all))))

(defn- strip-hydrated-cards
  "Removes hydrated cards from game state before persisting.

  Card data is stored in the catalog, not duplicated in game state."
  [game-state]
  (-> game-state
      (update-in [:players :team/HOME :deck] dissoc :cards)
      (update-in [:players :team/AWAY :deck] dissoc :cards)))

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
                                   :status           :game-status/WAITING
                                   :game-state       {}})]
          (subs/publish! subscription-manager [:lobby]
                         {:type :game-created
                          :data {:game-id (str (:id game))}})
          game))))

  (join-game! [_this game-id user-id deck-id]
    (when-let [game (proto/find-by game-repo {:id game-id})]
      (when (and (= :game-status/WAITING (:status game))
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
    (if (= status :game-status/ACTIVE)
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
            (let [current-state  (:game-state game)
                  ;; Hydrate with card data for action processing (e.g., team asset detection)
                  hydrated-state (hydrate-game-state current-state card-catalog)
                  ;; For reveal-fate, capture the top card's fate before action
                  revealed-fate  (when (= :bashketball/reveal-fate (:type action))
                                   (let [player    (:player action)
                                         card-slug (get-in current-state
                                                           [:players player :deck :draw-pile 0 :card-slug])
                                         card      (when card-slug
                                                     (catalog/get-card card-catalog card-slug))]
                                     (:fate card)))
                  new-state      (-> (game-actions/apply-action hydrated-state action)
                                     strip-hydrated-cards)
                  seq-num        (game-model/get-next-sequence-num game-id)
                  _event         (game-model/create-event! game-id
                                                           user-id
                                                           (name (:type action))
                                                           action
                                                           seq-num)
                  updated-game   (if (game-over? new-state)
                                   (let [winner-team (if (> (get-in new-state [:score :home])
                                                            (get-in new-state [:score :away]))
                                                       :home :away)
                                         winner-id   (if (= winner-team :home)
                                                       (:player-1-id game)
                                                       (:player-2-id game))]
                                     (game-model/update-game-state! game-repo game-id new-state)
                                     (game-model/end-game! game-repo game-id
                                                           :game-status/COMPLETED winner-id))
                                   (game-model/update-game-state! game-repo game-id new-state))]
              (subs/publish! subscription-manager [:game game-id]
                             {:type :state-changed
                              :data {:game-id (str game-id)}})
              (cond-> {:success true :game updated-game}
                revealed-fate (assoc :revealed-fate revealed-fate)))
            (catch Exception e
              {:success false :error (ex-message e)}))))
      {:success false :error "Game not found or not a participant"}))

  (forfeit-game! [this game-id user-id]
    (when-let [game (get-game-for-player this game-id user-id)]
      (when (= :game-status/ACTIVE (:status game))
        (let [winner-id (if (= user-id (:player-1-id game))
                          (:player-2-id game)
                          (:player-1-id game))
              updated   (game-model/end-game! game-repo game-id
                                              :game-status/COMPLETED winner-id)]
          (subs/publish! subscription-manager [:game game-id]
                         {:type :game-ended
                          :data {:game-id   (str game-id)
                                 :winner-id (str winner-id)
                                 :reason    "forfeit"}})
          updated))))

  (leave-game! [this game-id user-id]
    (when-let [game (get-game-for-player this game-id user-id)]
      (when (and (= :game-status/WAITING (:status game))
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
