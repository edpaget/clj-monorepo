(ns bashketball-game-api.models.game
  "Game model and repository.

  Manages game data stored in PostgreSQL including game state and events.
  Games go through status transitions: waiting -> active -> completed/abandoned."
  (:require
   [bashketball-game-api.models.protocol :as proto]
   [db.core :as db]
   [malli.core :as m]))

(def GameStatus
  "Valid game status values as namespaced keywords for PostgreSQL enum support."
  [:enum :game-status/waiting :game-status/active :game-status/completed :game-status/abandoned])

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

(def GameEvent
  "Malli schema for game event entity."
  [:map
   [:id {:optional true} :uuid]
   [:game-id :uuid]
   [:player-id {:optional true} [:maybe :uuid]]
   [:event-type :string]
   [:event-data {:optional true} :map]
   [:sequence-num :int]
   [:created-at {:optional true} inst?]])

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

(defrecord GameRepository []
  proto/Repository
  (find-by [_this criteria]
    (when-let [where-clause (build-where-clause criteria)]
      (db/execute-one!
       {:select [:*]
        :from [:games]
        :where where-clause})))

  (find-all [_this opts]
    (let [query (cond-> {:select [:*]
                         :from [:games]
                         :order-by (or (:order-by opts) [[:created-at :desc]])}
                  (:where opts)
                  (assoc :where (build-where-clause (:where opts)))

                  (:limit opts)
                  (assoc :limit (:limit opts))

                  (:offset opts)
                  (assoc :offset (:offset opts)))]
      (vec (db/execute! query))))

  (create! [_this data]
    {:pre [(m/validate Game data)]}
    (let [now       (java.time.Instant/now)
          ;; Use :player1-id (no hyphen) for HoneySQL to generate correct column name player1_id
          game-data {:player1-id [:cast (:player-1-id data) :uuid]
                     :player1-deck-id [:cast (:player-1-deck-id data) :uuid]
                     :status [:lift (or (:status data) :game-status/waiting)]
                     :game-state [:lift (or (:game-state data) {})]
                     :created-at now}]
      (db/execute-one!
       {:insert-into :games
        :values [game-data]
        :returning [:*]})))

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
        (db/execute-one!
         {:update :games
          :set set-data
          :where [:= :id [:cast id :uuid]]
          :returning [:*]}))))

  (delete! [_this id]
    (pos? (:next.jdbc/update-count
           (db/execute-one!
            {:delete-from :games
             :where [:= :id [:cast id :uuid]]})))))

(defn create-game-repository
  "Creates a new game repository instance."
  []
  (->GameRepository))

(defn find-waiting-games
  "Returns games in 'waiting' status, available to join."
  [_repo]
  (vec (db/execute!
        {:select [:*]
         :from [:games]
         :where [:= :status [:lift :game-status/waiting]]
         :order-by [[:created-at :desc]]})))

(defn find-by-player
  "Returns games where the user is either player1 or player2.

  Accepts optional `opts` map with `:status`, `:limit`, and `:offset` keys."
  ([_repo user-id]
   (find-by-player _repo user-id {}))
  ([_repo user-id {:keys [status limit offset]}]
   (let [base-where   [:or
                       [:= :player1-id [:cast user-id :uuid]]
                       [:= :player2-id [:cast user-id :uuid]]]
         where-clause (if status
                        [:and base-where [:= :status [:lift status]]]
                        base-where)
         query        (cond-> {:select [:*]
                               :from [:games]
                               :where where-clause
                               :order-by [[:created-at :desc]]}
                        limit (assoc :limit limit)
                        offset (assoc :offset offset))]
     (vec (db/execute! query)))))

(defn count-by-player
  "Returns count of games where the user is either player1 or player2.

  Accepts optional `opts` map with `:status` key for filtering."
  ([_repo user-id]
   (count-by-player _repo user-id {}))
  ([_repo user-id {:keys [status]}]
   (let [base-where   [:or
                       [:= :player1-id [:cast user-id :uuid]]
                       [:= :player2-id [:cast user-id :uuid]]]
         where-clause (if status
                        [:and base-where [:= :status [:lift status]]]
                        base-where)]
     (:count (db/execute-one!
              {:select [[[:count :*] :count]]
               :from [:games]
               :where where-clause})))))

(defn find-active-by-player
  "Returns active games where the user is either player1 or player2."
  [_repo user-id]
  (vec (db/execute!
        {:select [:*]
         :from [:games]
         :where [:and
                 [:= :status [:lift :game-status/active]]
                 [:or
                  [:= :player1-id [:cast user-id :uuid]]
                  [:= :player2-id [:cast user-id :uuid]]]]
         :order-by [[:created-at :desc]]})))

(defn update-game-state!
  "Updates only the game state JSON field."
  [_repo game-id game-state]
  (db/execute-one!
   {:update :games
    :set {:game-state [:lift game-state]}
    :where [:= :id [:cast game-id :uuid]]
    :returning [:*]}))

(defn start-game!
  "Transitions a game to 'active' status with player2 joining."
  [repo game-id player2-id player2-deck-id initial-state current-player-id]
  (proto/update! repo game-id
                 {:player-2-id player2-id
                  :player-2-deck-id player2-deck-id
                  :status :game-status/active
                  :game-state initial-state
                  :current-player-id current-player-id
                  :started-at (java.time.Instant/now)}))

(defn end-game!
  "Transitions a game to 'completed' or 'abandoned' status."
  [repo game-id status winner-id]
  (proto/update! repo game-id
                 {:status status
                  :winner-id winner-id
                  :ended-at (java.time.Instant/now)}))

(defn create-event!
  "Creates a game event record."
  [game-id player-id event-type event-data sequence-num]
  {:pre [(m/validate GameEvent {:game-id game-id
                                :player-id player-id
                                :event-type event-type
                                :event-data event-data
                                :sequence-num sequence-num})]}
  (db/execute-one!
   {:insert-into :game-events
    :values [(cond-> {:game-id game-id
                      :event-type event-type
                      :event-data [:lift (or event-data {})]
                      :sequence-num sequence-num
                      :created-at (java.time.Instant/now)}
               player-id
               (assoc :player-id player-id))]
    :returning [:*]}))

(defn find-events-by-game
  "Returns all events for a game, ordered by sequence number."
  [game-id]
  (vec (db/execute!
        {:select [:*]
         :from [:game-events]
         :where [:= :game-id [:cast game-id :uuid]]
         :order-by [[:sequence-num :asc]]})))

(defn find-events-since
  "Returns events for a game after a given sequence number."
  [game-id since-sequence]
  (vec (db/execute!
        {:select [:*]
         :from [:game-events]
         :where [:and
                 [:= :game-id [:cast game-id :uuid]]
                 [:> :sequence-num since-sequence]]
         :order-by [[:sequence-num :asc]]})))

(defn get-next-sequence-num
  "Returns the next sequence number for a game's events."
  [game-id]
  (let [result (db/execute-one!
                {:select [[[:coalesce [:max :sequence-num] 0] :max-seq]]
                 :from [:game-events]
                 :where [:= :game-id [:cast game-id :uuid]]})]
    (inc (:max-seq result))))
