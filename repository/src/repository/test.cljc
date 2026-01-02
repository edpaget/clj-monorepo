(ns repository.test
  "Testing utilities for repository implementations.

  Provides helpers for creating mock repositories, tracking operations,
  and writing concise tests.

  ## Example Usage

      (require '[repository.test :as rt]
               '[repository.protocol :as repo])

      ;; Create mock with initial data
      (let [repo (rt/mock-repository [{:id 1 :name \"Alice\"}
                                       {:id 2 :name \"Bob\"}])]
        (repo/find-one repo {:where {:id 1}}))
      ;; => {:id 1 :name \"Alice\"}

      ;; With scopes
      (let [repo (rt/mock-repository
                   [{:id 1 :status :active}
                    {:id 2 :status :inactive}]
                   {:scopes {:active (fn [_] #(= (:status %) :active))}})]
        (:data (repo/find-many repo {:scope :active})))
      ;; => [{:id 1 :status :active}]

      ;; Track operations
      (let [{:keys [repo calls]} (rt/tracking-repository
                                   (rt/mock-repository []))]
        (repo/save! repo {:name \"New\"})
        @calls)
      ;; => [{:op :save! :args [{:name \"New\"}] :result {...}}]"
  (:require [repository.backend.memory :as mem]
            [repository.protocol :as proto]))

(defn mock-repository
  "Creates a mock repository pre-populated with data.

  This is a convenience wrapper around [[repository.backend.memory/create-with-data]].

  Options:
  - `:id-field` - Field to use as primary key (default `:id`)
  - `:id-generator` - Function to generate new IDs (default `random-uuid`)
  - `:scopes` - Map of scope name to scope function"
  ([initial-data]
   (mock-repository initial-data {}))
  ([initial-data opts]
   (mem/create-with-data initial-data opts)))

(defn tracking-repository
  "Wraps a repository to track all operations.

  Returns a map with:
  - `:repo` - The wrapped repository
  - `:calls` - Atom containing a vector of operation records

  Each operation record is a map with:
  - `:op` - The operation name (`:find-one`, `:find-many`, `:save!`, `:delete!`)
  - `:args` - The arguments passed to the operation
  - `:result` - The result returned by the operation"
  [base-repo]
  (let [calls (atom [])]
    {:repo (reify
             proto/Repository
             (find-one [_ query]
               (let [result (proto/find-one base-repo query)]
                 (swap! calls conj {:op :find-one :args [query] :result result})
                 result))
             (find-many [_ query]
               (let [result (proto/find-many base-repo query)]
                 (swap! calls conj {:op :find-many :args [query] :result result})
                 result))
             (save! [_ entity]
               (let [result (proto/save! base-repo entity)]
                 (swap! calls conj {:op :save! :args [entity] :result result})
                 result))
             (delete! [_ query]
               (let [result (proto/delete! base-repo query)]
                 (swap! calls conj {:op :delete! :args [query] :result result})
                 result))

             proto/Countable
             (count-matching [_ query]
               (let [result (proto/count-matching base-repo query)]
                 (swap! calls conj {:op :count-matching :args [query] :result result})
                 result)))
     :calls calls}))

(defn calls-for
  "Filters tracked calls by operation name.

  Usage:
      (calls-for @calls :save!)
      ;; => [{:op :save! :args [...] :result {...}}]"
  [calls op]
  (filter #(= (:op %) op) calls))

(defn saved-entities
  "Returns all entities that were saved, from tracking calls.

  Usage:
      (saved-entities @calls)
      ;; => [{:id 1 :name \"Alice\"} {:id 2 :name \"Bob\"}]"
  [calls]
  (mapv :result (calls-for calls :save!)))

(defn deleted-count
  "Returns total count of deleted entities, from tracking calls."
  [calls]
  (reduce + 0 (map :result (calls-for calls :delete!))))

#?(:clj
   (defmacro with-repository
     "Executes body with a fresh mock repository bound to sym.

  Usage:
      (with-repository [repo [{:id 1 :name \"Test\"}]]
        (repo/find-one repo {:where {:id 1}}))

      ;; With options
      (with-repository [repo [{:id 1}] {:id-field :id}]
        ...)"
     [[sym initial-data & [opts]] & body]
     `(let [~sym (mock-repository ~initial-data ~(or opts {}))]
        ~@body)))
