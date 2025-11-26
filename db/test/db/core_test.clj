(ns db.core-test
  "Tests for db.core functionality."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [db.core :as db]
   [db.test-utils :as db.tu]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.transaction]
   [ragtime.next-jdbc :as r.next-jdbc]))

(set! *warn-on-reflection* true)

;; --- Test Fixtures ---

;; Register fixtures: db-fixture runs once, rollback-fixture runs per-test
;; Use fixtures from the test-utils namespace
(def ^:private db-url "jdbc:postgresql://localhost:5432/test_db?user=postgres&password=postgres")
(use-fixtures :once (db.tu/db-fixture {:db-url db-url
                                       :migrations (r.next-jdbc/load-resources "db-migrations")}))
(use-fixtures :each db.tu/rollback-fixture)

;; --- Helper ---
(defn- random-email
  "Generate a random email address for testing.

  Returns:
    String: Random email address"
  []
  (str (gensym "test-user-") "@example.com"))

(defn- insert-actor!
  "Insert an actor record for testing.

  Args:
    conn-or-nil: Optional database connection, uses dynamic var if nil
    actor-data: Map with :id and :use-name keys

  Returns:
    Vector of update counts"
  [conn-or-nil {:keys [id use-name]}]
  (let [query (-> (h/insert-into :actor)
                  (h/values [{:id id :use-name use-name :enrollment-state "complete"}]))]
    (if conn-or-nil
      (db/execute! conn-or-nil query)
      (db/execute! query))))

(defn- get-actor-by-id
  "Retrieve an actor by ID for testing.

  Args:
    conn-or-nil: Optional database connection, uses dynamic var if nil
    actor-id: Actor ID to retrieve

  Returns:
    Map: Actor record or nil if not found"
  [conn-or-nil actor-id]
  (let [query (-> (h/select :id :use-name)
                  (h/from :actor)
                  (h/where [:= :id actor-id]))]
    (if conn-or-nil
      (db/execute-one! conn-or-nil query)
      (db/execute-one! query))))

;; --- Tests ---

(deftest execute!-test
  (testing "Insert using dynamic datasource"
    (let [actor-id (random-email)
          result   (insert-actor! nil {:id actor-id :use-name "pennyg"})]
      (is (= [1] (mapv :next.jdbc/update-count result)) "Should return update count of 1")
      (is (= {:id actor-id :use-name "pennyg"}
             (get-actor-by-id nil actor-id)))))

  (testing "Insert using explicit connection"
    (jdbc/with-transaction [tx db/*current-connection* {:rollback-only true}]
      (let [actor-id (random-email)
            result   (insert-actor! tx {:id actor-id :use-name "nickw"})]
        (is (= [1] (mapv :next.jdbc/update-count result)) "Should return update count of 1")
        (is (= {:id actor-id :use-name "nickw"}
               (get-actor-by-id tx actor-id)))))))

(deftest execute-one!-test
  (testing "Select one using dynamic datasource"
    (let [actor-id (random-email)]
      (insert-actor! nil {:id actor-id :use-name "edc"})
      (let [actor (get-actor-by-id nil actor-id)]
        (is (= {:id actor-id :use-name "edc"} actor)))))

  (testing "Select one using explicit connection"
    (jdbc/with-transaction [tx db/*current-connection* {:rollback-only true}]
      (let [actor-id (random-email)]
        (insert-actor! tx {:id actor-id :use-name "jend"})
        (let [actor (get-actor-by-id tx actor-id)]
          (is (= {:id actor-id :use-name "jend"} actor))))))

  (testing "Select non-existent returns nil"
    (is (nil? (get-actor-by-id nil (random-email))))))

(deftest plan-test
  (testing "Select multiple using dynamic datasource"
    (let [id5 (random-email)
          id6 (random-email)]
      (insert-actor! nil {:id id5 :use-name "johnnyl"})
      (insert-actor! nil {:id id6 :use-name "betten"})
      (let [query  (-> (h/select :use-name)
                       (h/from :actor)
                       (h/where [:in :id [id5 id6]]))
            actors (into #{} (map #(select-keys % [:use_name]))
                         (db/plan query))]
        (is (= #{{:use_name "johnnyl"} {:use_name "betten"}} actors)))))

  (testing "Select multiple using explicit connection"
    (jdbc/with-transaction [tx db/*current-connection* {:rollback-only true}]
      (let [id7 (random-email)
            id8 (random-email)]
        (insert-actor! tx {:id id7 :use-name "gracem"})
        (insert-actor! tx {:id id8 :use-name "mattj"})
        (let [query  (-> (h/select :use-name)
                         (h/from :actor)
                         (h/where [:in :id [id7 id8]]))
              actors (into #{} (map #(select-keys % [:use_name]))
                           (db/plan tx query))]
          (is (= #{{:use_name "gracem"} {:use_name "mattj"}} actors)))))))

(deftest with-connection-test
  (testing "Operations within with-connection use the same connection"
    (let [id9  (random-email)
          id10 (random-email)]
      (db/with-connection [_conn]
        (let [result1 (insert-actor! nil {:id id9 :use-name "joes"})
              actor1  (get-actor-by-id nil id9)
              result2 (insert-actor! nil {:id id10 :use-name "chrisg"})
              actor2  (get-actor-by-id nil id10)]
          (is (= [1] (mapv :next.jdbc/update-count result1)))
          (is (= {:id id9 :use-name "joes"} actor1))
          (is (= [1] (mapv :next.jdbc/update-count result2)))
          (is (= {:id id10 :use-name "chrisg"} actor2))))
      (is (= {:id id9 :use-name "joes"} (get-actor-by-id nil id9)))
      (is (= {:id id10 :use-name "chrisg"} (get-actor-by-id nil id10))))))

(deftest dynamic-binding-test
  (testing "Throws exception if *datasource* is not bound and no connection given"
    (binding [db/*datasource*         nil
              db/*current-connection* nil]
      (is (thrown? IllegalStateException (db/execute! (h/select :* (h/from :actor))))))))
