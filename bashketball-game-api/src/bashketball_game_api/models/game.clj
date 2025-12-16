(ns bashketball-game-api.models.game
  "Game model and repository.

  Manages game data stored in PostgreSQL including game state and events.
  Games go through status transitions: WAITING -> ACTIVE -> COMPLETED/ABANDONED."
  (:require
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game.schema :as game-schema]
   [bashketball-schemas.enums :as enums]
   [db.core :as db]
   [db.transform :as db-transform]
   [malli.core :as m]))

(def GameStatus
  "Valid game status values. Re-exported from [[bashketball-schemas.enums]]."
  enums/GameStatus)

(def Game
  "Malli schema for game entity."
  [:map
   [:id {:optional true} :uuid]
   [:player-1-id :uuid]
   [:player-2-id {:optional true} [:maybe :uuid]]
   [:player-1-deck-id :uuid]
   [:player-2-deck-id {:optional true} [:maybe :uuid]]
   [:status {:optional true} GameStatus]
   [:current-player-id {:optional true} [:maybe :uuid]]
   [:game-state {:optional true} :map]
   [:winner-id {:optional true} [:maybe :uuid]]
   [:created-at {:optional true} inst?]
   [:started-at {:optional true} [:maybe inst?]]
   [:ended-at {:optional true} [:maybe inst?]]])

(defn- transform-game-state
  "Transforms game-state JSON from DB format to application format.

  Uses Malli schema-driven decoding to handle nested structures,
  multi dispatch values, and enum values."
  [game-state]
  (when game-state
    (db-transform/decode game-state game-schema/GameState)))

(defn- transform-game
  "Transforms a game record from DB format to application format.

  Applies Malli schema transforms to the game-state JSON column."
  [game]
  (when game
    (if (:game-state game)
      (update game :game-state transform-game-state)
      game)))

(defn- transform-games
  "Transforms a collection of game records from DB format."
  [games]
  (mapv transform-game games))

(defn- build-where-clause
  "Builds a HoneySQL where clause from a criteria map.
   Maps input keys (with hyphens before numbers) to DB column names (without)."
  [criteria]
  (let [;; Map from input key to HoneySQL column key
        key-mapping {:player-1-id :player1-id
                     :player-2-id :player2-id
                     :player-1-deck-id :player1-deck-id
                     :player-2-deck-id :player2-deck-id}
        uuid-keys   #{:id :player-1-id :player-2-id :player-1-deck-id
                      :player-2-deck-id :current-player-id :winner-id}]
    (when (seq criteria)
      (into [:and]
            (map (fn [[k v]]
                   (let [col-key (get key-mapping k k)]
                     (if (uuid-keys k)
                       [:= col-key [:cast v :uuid]]
                       [:= col-key v])))
                 criteria)))))

(defn- build-scope-query
  "Builds a HoneySQL where clause for a named scope.

  Supported scopes:
  - `:waiting` - Games with WAITING status
  - `:by-player` - Games where `:player-id` is player1 or player2, optional `:status`
  - `:active-by-player` - Active games where `:player-id` is player1 or player2"
  [scope opts]
  (case scope
    :waiting
    [:= :status [:lift :game-status/WAITING]]

    :by-player
    (let [player-id  (:player-id opts)
          base-where [:or
                      [:= :player1-id [:cast player-id :uuid]]
                      [:= :player2-id [:cast player-id :uuid]]]]
      (if-let [status (:status opts)]
        [:and base-where [:= :status [:lift status]]]
        base-where))

    :active-by-player
    (let [player-id (:player-id opts)]
      [:and
       [:= :status [:lift :game-status/ACTIVE]]
       [:or
        [:= :player1-id [:cast player-id :uuid]]
        [:= :player2-id [:cast player-id :uuid]]]])

    nil))

(defrecord GameRepository []
  proto/Repository
  (find-by [_this criteria]
    (when-let [where-clause (build-where-clause criteria)]
      (-> (db/execute-one!
           {:select [:*]
            :from [:games]
            :where where-clause})
          transform-game)))

  (find-all [_this opts]
    (let [scope-where (when (:scope opts)
                        (build-scope-query (:scope opts) opts))
          query       (cond-> {:select [:*]
                               :from [:games]
                               :order-by (or (:order-by opts) [[:created-at :desc]])}
                        scope-where
                        (assoc :where scope-where)

                        (and (not (:scope opts)) (:where opts))
                        (assoc :where (build-where-clause (:where opts)))

                        (:limit opts)
                        (assoc :limit (:limit opts))

                        (:offset opts)
                        (assoc :offset (:offset opts)))]
      (transform-games (db/execute! query))))

  (create! [_this data]
    {:pre [(m/validate Game data)]}
    (let [now       (java.time.Instant/now)
          ;; Use :player1-id (no hyphen) for HoneySQL to generate correct column name player1_id
          game-data {:player1-id [:cast (:player-1-id data) :uuid]
                     :player1-deck-id [:cast (:player-1-deck-id data) :uuid]
                     :status [:lift (or (:status data) :game-status/WAITING)]
                     :game-state [:lift (or (:game-state data) {})]
                     :created-at now}]
      (-> (db/execute-one!
           {:insert-into :games
            :values [game-data]
            :returning [:*]})
          transform-game)))

  (update! [_this id data]
    ;; Use :player2-id (no hyphen before 2) for HoneySQL column names
    (let [set-data (cond-> {}
                     (:player-2-id data)
                     (assoc :player2-id [:cast (:player-2-id data) :uuid])

                     (:player-2-deck-id data)
                     (assoc :player2-deck-id [:cast (:player-2-deck-id data) :uuid])

                     (:status data)
                     (assoc :status [:lift (:status data)])

                     (:current-player-id data)
                     (assoc :current-player-id [:cast (:current-player-id data) :uuid])

                     (contains? data :game-state)
                     (assoc :game-state [:lift (:game-state data)])

                     (:winner-id data)
                     (assoc :winner-id [:cast (:winner-id data) :uuid])

                     (:started-at data)
                     (assoc :started-at (:started-at data))

                     (:ended-at data)
                     (assoc :ended-at (:ended-at data)))]
      (when (seq set-data)
        (-> (db/execute-one!
             {:update :games
              :set set-data
              :where [:= :id [:cast id :uuid]]
              :returning [:*]})
            transform-game))))

  (delete! [_this id]
    (pos? (:next.jdbc/update-count
           (db/execute-one!
            {:delete-from :games
             :where [:= :id [:cast id :uuid]]})))))

(defn create-game-repository
  "Creates a new game repository instance."
  []
  (->GameRepository))

(defprotocol GameRepositoryExt
  "Extended repository operations specific to games."
  (count-all [this opts]
    "Returns count of games matching the given options.

    Supports the same `:scope` and `:where` options as `find-all`."))

(extend-type GameRepository
  GameRepositoryExt
  (count-all [_this opts]
    (let [scope-where  (when (:scope opts)
                         (build-scope-query (:scope opts) opts))
          where-clause (or scope-where
                           (when (:where opts)
                             (build-where-clause (:where opts))))]
      (:count (db/execute-one!
               (cond-> {:select [[[:count :*] :count]]
                        :from [:games]}
                 where-clause (assoc :where where-clause)))))))

