(ns bashketball-game-api.models.deck
  "Deck model and repository.

  Manages deck data stored in PostgreSQL. Decks contain card slugs and
  validation state."
  (:require
   [bashketball-game-api.models.protocol :as proto]
   [db.core :as db]
   [malli.core :as m]))

(def Deck
  "Malli schema for deck entity."
  [:map
   [:id {:optional true} :uuid]
   [:user-id :uuid]
   [:name :string]
   [:card-slugs {:optional true} [:vector :string]]
   [:is-valid {:optional true} :boolean]
   [:validation-errors {:optional true} [:maybe [:vector :string]]]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defn- build-where-clause
  "Builds a HoneySQL where clause from a criteria map."
  [criteria]
  (when (seq criteria)
    (into [:and]
          (map (fn [[k v]]
                 (if (#{:id :user-id} k)
                   [:= k [:cast v :uuid]]
                   [:= k v]))
               criteria))))

(defrecord DeckRepository []
  proto/Repository
  (find-by [_this criteria]
    (when-let [where-clause (build-where-clause criteria)]
      (db/execute-one!
       {:select [:*]
        :from [:decks]
        :where where-clause})))

  (find-all [_this opts]
    (let [query (cond-> {:select [:*]
                         :from [:decks]
                         :order-by (or (:order-by opts) [[:created-at :desc]])}
                  (:where opts)
                  (assoc :where (build-where-clause (:where opts)))

                  (:limit opts)
                  (assoc :limit (:limit opts))

                  (:offset opts)
                  (assoc :offset (:offset opts)))]
      (vec (db/execute! query))))

  (create! [_this data]
    {:pre [(m/validate Deck data)]}
    (let [now       (java.time.Instant/now)
          deck-data (cond-> {:user-id [:cast (:user-id data) :uuid]
                             :name (:name data)
                             :card-slugs [:lift (or (:card-slugs data) [])]
                             :is-valid (or (:is-valid data) false)
                             :updated-at now}
                      (not (:created-at data))
                      (assoc :created-at now)

                      (:validation-errors data)
                      (assoc :validation-errors [:lift (:validation-errors data)]))]
      (db/execute-one!
       {:insert-into :decks
        :values [deck-data]
        :returning [:*]})))

  (update! [_this id data]
    (let [now      (java.time.Instant/now)
          set-data (cond-> {:updated-at now}
                     (:name data)
                     (assoc :name (:name data))

                     (contains? data :card-slugs)
                     (assoc :card-slugs [:lift (:card-slugs data)])

                     (contains? data :is-valid)
                     (assoc :is-valid (:is-valid data))

                     (contains? data :validation-errors)
                     (assoc :validation-errors [:lift (:validation-errors data)]))]
      (db/execute-one!
       {:update :decks
        :set set-data
        :where [:= :id [:cast id :uuid]]
        :returning [:*]})))

  (delete! [_this id]
    (pos? (:next.jdbc/update-count
           (db/execute-one!
            {:delete-from :decks
             :where [:= :id [:cast id :uuid]]})))))

(defn create-deck-repository
  "Creates a new deck repository instance."
  []
  (->DeckRepository))

(defn find-by-user
  "Returns all decks for a given user ID."
  [repo user-id]
  (proto/find-all repo {:where {:user-id user-id}}))

(defn find-valid-by-user
  "Returns all valid decks for a given user ID."
  [_repo user-id]
  (vec (db/execute!
        {:select [:*]
         :from [:decks]
         :where [:and
                 [:= :user-id [:cast user-id :uuid]]
                 [:= :is-valid true]]
         :order-by [[:created-at :desc]]})))
