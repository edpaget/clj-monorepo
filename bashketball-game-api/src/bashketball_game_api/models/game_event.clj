(ns bashketball-game-api.models.game-event
  "Game event model and repository.

  Manages game event records stored in PostgreSQL. Events track all actions
  taken during a game for replay and auditing purposes. Implements the
  standard [[bashketball-game-api.models.protocol/Repository]] protocol."
  (:require
   [bashketball-game-api.models.protocol :as proto]
   [db.core :as db]
   [db.transform :as db-transform]
   [malli.core :as m]))

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

(defn- transform-event
  "Transforms a game event record from DB format to application format."
  [event]
  (when event
    (if (:event-data event)
      (update event :event-data db-transform/keywordize-keys)
      event)))

(defn- transform-events
  "Transforms a collection of event records from DB format."
  [events]
  (mapv transform-event events))

(defn- build-scope-query
  "Builds a HoneySQL where clause for a named scope.

  Supported scopes:
  - `:by-game` with `:game-id` - all events for a game
  - `:since` with `:game-id` and `:sequence-num` - events after sequence"
  [scope opts]
  (case scope
    :by-game
    [:= :game-id [:cast (:game-id opts) :uuid]]

    :since
    [:and
     [:= :game-id [:cast (:game-id opts) :uuid]]
     [:> :sequence-num (:sequence-num opts)]]

    nil))

(defrecord GameEventRepository []
  proto/Repository

  (find-by [_this criteria]
    (let [where-clauses (cond-> []
                          (:id criteria)
                          (conj [:= :id [:cast (:id criteria) :uuid]])

                          (:game-id criteria)
                          (conj [:= :game-id [:cast (:game-id criteria) :uuid]])

                          (:sequence-num criteria)
                          (conj [:= :sequence-num (:sequence-num criteria)]))]
      (when (seq where-clauses)
        (-> (db/execute-one!
             {:select [:*]
              :from [:game-events]
              :where (if (= 1 (count where-clauses))
                       (first where-clauses)
                       (into [:and] where-clauses))})
            transform-event))))

  (find-all [_this opts]
    (let [scope-where (when (:scope opts)
                        (build-scope-query (:scope opts) opts))
          query       (cond-> {:select [:*]
                               :from [:game-events]
                               :order-by (or (:order-by opts) [[:sequence-num :asc]])}
                        scope-where
                        (assoc :where scope-where)

                        (:limit opts)
                        (assoc :limit (:limit opts))

                        (:offset opts)
                        (assoc :offset (:offset opts)))]
      (transform-events (db/execute! query))))

  (create! [_this data]
    {:pre [(m/validate GameEvent data)]}
    (-> (db/execute-one!
         {:insert-into :game-events
          :values [(cond-> {:game-id (:game-id data)
                            :event-type (:event-type data)
                            :event-data [:lift (or (:event-data data) {})]
                            :sequence-num (:sequence-num data)
                            :created-at (java.time.Instant/now)}
                     (:player-id data)
                     (assoc :player-id (:player-id data)))]
          :returning [:*]})
        transform-event))

  (update! [_this id data]
    (let [set-data (cond-> {}
                     (:event-type data)
                     (assoc :event-type (:event-type data))

                     (contains? data :event-data)
                     (assoc :event-data [:lift (:event-data data)])

                     (:player-id data)
                     (assoc :player-id (:player-id data)))]
      (when (seq set-data)
        (-> (db/execute-one!
             {:update :game-events
              :set set-data
              :where [:= :id [:cast id :uuid]]
              :returning [:*]})
            transform-event))))

  (delete! [_this id]
    (pos? (:next.jdbc/update-count
           (db/execute-one!
            {:delete-from :game-events
             :where [:= :id [:cast id :uuid]]})))))

(defn create-game-event-repository
  "Creates a new game event repository instance."
  []
  (->GameEventRepository))

(defprotocol GameEventRepositoryExt
  "Extended operations specific to game events."
  (next-sequence-num [this game-id]
    "Returns the next sequence number for a game's events."))

(extend-type GameEventRepository
  GameEventRepositoryExt
  (next-sequence-num [_this game-id]
    (let [result (db/execute-one!
                  {:select [[[:coalesce [:max :sequence-num] 0] :max-seq]]
                   :from [:game-events]
                   :where [:= :game-id [:cast game-id :uuid]]})]
      (inc (:max-seq result)))))
