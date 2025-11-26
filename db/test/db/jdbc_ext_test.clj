(ns db.jdbc-ext-test
  "Tests for db.jdbc-ext enum handling functionality."
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [db.core :as db]
   [db.jdbc-ext :as jdbc-ext]
   [db.test-utils :as db.tu]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [ragtime.next-jdbc :as r.next-jdbc]))

(set! *warn-on-reflection* true)

(def ^:private db-url "jdbc:postgresql://localhost:5432/test_db?user=postgres&password=postgres")

(use-fixtures :once (db.tu/db-fixture {:db-url db-url
                                       :migrations (r.next-jdbc/load-resources "db-migrations")}))
(use-fixtures :each db.tu/rollback-fixture)

(defn- random-id
  "Generate a random ID for testing.

  Returns:
    String: Random ID"
  []
  (str (gensym "test-entity-")))

(deftest cache-contains-enum-types
  (let [cache (jdbc-ext/refresh-enum-cache! db/*datasource*)]
    (is (set? cache))
    (is (contains? cache "status_enum"))))

(deftest cache-updates-with-new-enum-types
  (jdbc-ext/refresh-enum-cache! db/*datasource*)
  (try
    (jdbc/execute! db/*datasource* ["CREATE TYPE test_enum AS ENUM ('foo', 'bar')"])
    (let [cache-after (jdbc-ext/refresh-enum-cache! db/*datasource*)]
      (is (contains? cache-after "test_enum")))
    (finally
      (jdbc/execute! db/*datasource* ["DROP TYPE test_enum"]))))

(deftest insert-enum-with-lift-keyword
  (jdbc-ext/refresh-enum-cache! db/*datasource*)
  (let [entity-id (random-id)
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "Keyword Test"
                                               :status [:lift :status-enum/active]}])))
        result    (db/execute-one! (-> (h/select :status)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= :status-enum/active (:status result)))))

(deftest query-with-lift-keyword-in-where-clause
  (jdbc-ext/refresh-enum-cache! db/*datasource*)
  (let [id1     (random-id)
        id2     (random-id)
        _       (db/execute! (-> (h/insert-into :entity)
                                 (h/values [{:id id1 :name "Active1" :status [:lift :status-enum/active]}
                                            {:id id2 :name "Inactive1" :status [:lift :status-enum/inactive]}])))
        results (db/execute! (-> (h/select :id :name)
                                 (h/from :entity)
                                 (h/where [:= :status [:lift :status-enum/active]])
                                 (h/where [:in :id [id1 id2]])))]
    (is (= 1 (count results)))
    (is (= "Active1" (:name (first results))))))

(deftest update-enum-with-lift-keyword
  (jdbc-ext/refresh-enum-cache! db/*datasource*)
  (let [entity-id (random-id)
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "Update Test"
                                               :status [:lift :status-enum/pending]}])))
        _         (db/execute! (-> (h/update :entity)
                                   (h/set {:status [:lift :status-enum/active]})
                                   (h/where [:= :id entity-id])))
        result    (db/execute-one! (-> (h/select :status)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= :status-enum/active (:status result)))))

(deftest all-enum-values-read-as-namespaced-keywords
  (jdbc-ext/refresh-enum-cache! db/*datasource*)
  (let [id1     (random-id)
        id2     (random-id)
        id3     (random-id)
        _       (db/execute! (-> (h/insert-into :entity)
                                 (h/values [{:id id1 :name "Active" :status [:lift :status-enum/active]}
                                            {:id id2 :name "Inactive" :status [:lift :status-enum/inactive]}
                                            {:id id3 :name "Pending" :status [:lift :status-enum/pending]}])))
        results (db/execute! (-> (h/select :id :status)
                                 (h/from :entity)
                                 (h/where [:in :id [id1 id2 id3]])))]
    (is (= 3 (count results)))
    (is (some #(= :status-enum/active (:status %)) results))
    (is (some #(= :status-enum/inactive (:status %)) results))
    (is (some #(= :status-enum/pending (:status %)) results))))

(deftest non-enum-string-columns-remain-strings
  (jdbc-ext/refresh-enum-cache! db/*datasource*)
  (let [entity-id (random-id)
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "Test Name"
                                               :status [:lift :status-enum/active]}])))
        result    (db/execute-one! (-> (h/select :name)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (string? (:name result)))))

(deftest non-enum-keyword-converts-to-string
  (jdbc-ext/refresh-enum-cache! db/*datasource*)
  (let [entity-id (random-id)
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name [:lift :not-enum/test]
                                               :status [:lift :status-enum/active]}])))
        result    (db/execute-one! (-> (h/select :name)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= "test" (:name result)))))

(deftest insert-jsonb-map
  (let [entity-id (random-id)
        metadata  {:user-id 123 :role "admin"}
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "JSONB Test"
                                               :status [:lift :status-enum/active]
                                               :metadata [:lift metadata]}])))
        result    (db/execute-one! (-> (h/select :metadata)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= {:user-id 123 :role "admin"} (:metadata result)))))

(deftest insert-json-map
  (let [entity-id (random-id)
        settings  {:theme "dark" :notifications true}
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "JSON Test"
                                               :status [:lift :status-enum/active]
                                               :settings [:lift settings]}])))
        result    (db/execute-one! (-> (h/select :settings)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= {:theme "dark" :notifications true} (:settings result)))))

(deftest insert-jsonb-vector
  (let [entity-id (random-id)
        metadata  ["tag1" "tag2" "tag3"]
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "JSONB Vector Test"
                                               :status [:lift :status-enum/active]
                                               :metadata [:lift metadata]}])))
        result    (db/execute-one! (-> (h/select :metadata)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= ["tag1" "tag2" "tag3"] (:metadata result)))))

(deftest insert-json-vector
  (let [entity-id (random-id)
        settings  [1 2 3 4 5]
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "JSON Vector Test"
                                               :status [:lift :status-enum/active]
                                               :settings [:lift settings]}])))
        result    (db/execute-one! (-> (h/select :settings)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= [1 2 3 4 5] (:settings result)))))

(deftest jsonb-preserves-nested-structures
  (let [entity-id (random-id)
        metadata  {:user {:id 123 :name "Alice"}
                   :permissions ["read" "write"]
                   :active true}
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "Nested Test"
                                               :status [:lift :status-enum/active]
                                               :metadata [:lift metadata]}])))
        result    (db/execute-one! (-> (h/select :metadata)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (= metadata (:metadata result)))))

(deftest json-null-values-handled-correctly
  (let [entity-id (random-id)
        _         (db/execute! (-> (h/insert-into :entity)
                                   (h/values [{:id entity-id
                                               :name "Null Test"
                                               :status [:lift :status-enum/active]
                                               :metadata nil
                                               :settings nil}])))
        result    (db/execute-one! (-> (h/select :metadata :settings)
                                       (h/from :entity)
                                       (h/where [:= :id entity-id])))]
    (is (nil? (:metadata result)))
    (is (nil? (:settings result)))))
