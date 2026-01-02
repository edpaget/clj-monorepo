(ns repository.backend.sql-test
  "Unit tests for SQL repository backend.

  Tests verify HoneySQL generation and query building logic using
  mocked database calls."
  (:require [clojure.string]
            [clojure.test :refer [deftest is testing]]
            [repository.backend.sql :as sql]
            [repository.protocol :as repo]))

(def ^:dynamic *executed-queries* nil)
(def ^:dynamic *mock-result-one* nil)
(def ^:dynamic *mock-result-many* nil)

(defn- capture-query-one [query]
  (when *executed-queries*
    (swap! *executed-queries* conj query))
  @*mock-result-one*)

(defn- capture-query-many [query]
  (when *executed-queries*
    (swap! *executed-queries* conj query))
  @*mock-result-many*)

(defmacro with-mock-db
  "Executes body with mocked db/execute-one! and db/execute! calls.

  Results can be a vector (used for both execute! and first element for execute-one!)
  or a map (used directly for execute-one!)."
  [results & body]
  `(let [r#           ~results
         one-result#  (if (vector? r#) (first r#) r#)
         many-result# (if (vector? r#) r# [r#])]
     (binding [*executed-queries* (atom [])
               *mock-result-one*  (atom one-result#)
               *mock-result-many* (atom many-result#)]
       (with-redefs [db.core/execute-one! capture-query-one
                     db.core/execute!     capture-query-many]
         ~@body))))

(deftest create-requires-table-name-test
  (testing "create throws without table-name"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"table-name is required"
                          (sql/create {})))))

(deftest create-with-defaults-test
  (testing "create uses sensible defaults"
    (let [repo (sql/create {:table-name :users})]
      (is (some? repo)))))

(deftest find-one-basic-where-test
  (testing "find-one with simple where clause"
    (with-mock-db [{:id 1 :name "Alice"}]
      (let [repo   (sql/create {:table-name :users})
            result (repo/find-one repo {:where {:name "Alice"}})]
        (is (= {:id 1 :name "Alice"} result))
        (let [query (first @*executed-queries*)]
          (is (= :users (first (:from query))))
          (is (= [:and [:= :name "Alice"]] (:where query)))
          (is (= 1 (:limit query))))))))

(deftest find-one-with-uuid-coercion-test
  (testing "find-one coerces UUID fields"
    (with-mock-db [{:id #uuid "123e4567-e89b-12d3-a456-426614174000"}]
      (let [repo     (sql/create {:table-name :users
                                  :field-types {:id :uuid}})
            uuid-str "123e4567-e89b-12d3-a456-426614174000"]
        (repo/find-one repo {:where {:id uuid-str}})
        (let [query (first @*executed-queries*)]
          (is (= [:and [:= :id [:cast uuid-str :uuid]]] (:where query))))))))

(deftest find-one-with-scope-test
  (testing "find-one uses scope function"
    (with-mock-db [{:id 1 :status :active}]
      (let [repo   (sql/create {:table-name :users
                                :scopes {:active (fn [_] [:= :status [:lift :active]])}})
            result (repo/find-one repo {:scope :active})]
        (is (= {:id 1 :status :active} result))
        (let [query (first @*executed-queries*)]
          (is (= [:= :status [:lift :active]] (:where query))))))))

(deftest find-one-unknown-scope-throws-test
  (testing "find-one throws for unknown scope"
    (with-mock-db []
      (let [repo (sql/create {:table-name :users})]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown scope"
                              (repo/find-one repo {:scope :nonexistent})))))))

(deftest find-one-with-transform-test
  (testing "find-one applies from-db transform"
    (with-mock-db [{:id 1 :name "alice"}]
      (let [repo   (sql/create {:table-name :users
                                :transforms {:from-db #(update % :name clojure.string/upper-case)}})
            result (repo/find-one repo {:where {:id 1}})]
        (is (= "ALICE" (:name result)))))))

(deftest find-one-with-column-mapping-test
  (testing "find-one maps entity fields to DB columns"
    (with-mock-db [{:id 1 :player1-id #uuid "123e4567-e89b-12d3-a456-426614174000"}]
      (let [repo      (sql/create {:table-name :games
                                   :column-mapping {:player-1-id :player1-id}})
            player-id "123e4567-e89b-12d3-a456-426614174000"]
        (repo/find-one repo {:where {:player-1-id player-id}})
        (let [query (first @*executed-queries*)]
          (is (= [:and [:= :player1-id player-id]] (:where query))))))))

(deftest find-many-basic-test
  (testing "find-many returns data with page-info"
    (with-mock-db [{:id 1} {:id 2}]
      (let [repo   (sql/create {:table-name :users})
            result (repo/find-many repo {})]
        (is (vector? (:data result)))
        (is (map? (:page-info result)))
        (is (contains? (:page-info result) :has-next-page))))))

(deftest find-many-with-order-by-test
  (testing "find-many applies order-by"
    (with-mock-db [{:id 2} {:id 1}]
      (let [repo (sql/create {:table-name :users})]
        (repo/find-many repo {:order-by [[:created-at :desc]]})
        (let [query (first @*executed-queries*)]
          (is (= [[:created-at :desc]] (:order-by query))))))))

(deftest find-many-with-limit-test
  (testing "find-many applies limit"
    (with-mock-db [{:id 1}]
      (let [repo (sql/create {:table-name :users})]
        (repo/find-many repo {:limit 10})
        (let [query (first @*executed-queries*)]
          (is (= 10 (:limit query))))))))

(deftest find-many-with-scope-and-params-test
  (testing "find-many uses scope with params"
    (with-mock-db [{:id 1}]
      (let [repo (sql/create {:table-name :games
                              :scopes {:by-player (fn [{:keys [player-id]}]
                                                    [:or
                                                     [:= :player1-id player-id]
                                                     [:= :player2-id player-id]])}})]
        (repo/find-many repo {:scope :by-player
                              :scope-params {:player-id "user-123"}})
        (let [query (first @*executed-queries*)]
          (is (= [:or
                  [:= :player1-id "user-123"]
                  [:= :player2-id "user-123"]]
                 (:where query))))))))

(deftest save-insert-test
  (testing "save! without id performs INSERT"
    (with-mock-db [{:id 1 :name "Alice"}]
      (let [repo   (sql/create {:table-name :users})
            result (repo/save! repo {:name "Alice"})]
        (is (= {:id 1 :name "Alice"} result))
        (let [query (first @*executed-queries*)]
          (is (= :users (:insert-into query)))
          (is (= [{:name "Alice"}] (:values query)))
          (is (= [:*] (:returning query))))))))

(deftest save-update-test
  (testing "save! with id performs UPDATE"
    (with-mock-db [{:id 1 :name "Bob"}]
      (let [repo   (sql/create {:table-name :users})
            result (repo/save! repo {:id 1 :name "Bob"})]
        (is (= {:id 1 :name "Bob"} result))
        (let [query (first @*executed-queries*)]
          (is (= :users (:update query)))
          (is (= {:name "Bob"} (:set query)))
          (is (= [:= :id 1] (:where query))))))))

(deftest save-with-uuid-coercion-test
  (testing "save! coerces UUID fields"
    (with-mock-db [{:id #uuid "123e4567-e89b-12d3-a456-426614174000"}]
      (let [repo     (sql/create {:table-name :users
                                  :field-types {:id :uuid}})
            uuid-str "123e4567-e89b-12d3-a456-426614174000"]
        (repo/save! repo {:id uuid-str :name "Alice"})
        (let [query (first @*executed-queries*)]
          (is (= [:= :id [:cast uuid-str :uuid]] (:where query))))))))

(deftest save-with-transform-test
  (testing "save! applies to-db and from-db transforms"
    (with-mock-db [{:id 1 :name "ALICE"}]
      (let [repo   (sql/create {:table-name :users
                                :transforms {:to-db #(update % :name clojure.string/upper-case)
                                             :from-db #(update % :name clojure.string/lower-case)}})
            result (repo/save! repo {:name "Alice"})]
        (is (= "alice" (:name result)))
        (let [query (first @*executed-queries*)]
          (is (= [{:name "ALICE"}] (:values query))))))))

(deftest delete-with-where-test
  (testing "delete! with where clause"
    (with-mock-db {:next.jdbc/update-count 1}
      (let [repo   (sql/create {:table-name :users})
            result (repo/delete! repo {:where {:id 1}})]
        (is (= 1 result))
        (let [query (first @*executed-queries*)]
          (is (= :users (:delete-from query)))
          (is (= [:and [:= :id 1]] (:where query))))))))

(deftest delete-with-scope-test
  (testing "delete! with scope"
    (with-mock-db {:next.jdbc/update-count 5}
      (let [repo   (sql/create {:table-name :sessions
                                :scopes {:expired (fn [_] [:< :expires-at [:now]])}})
            result (repo/delete! repo {:scope :expired})]
        (is (= 5 result))
        (let [query (first @*executed-queries*)]
          (is (= [:< :expires-at [:now]] (:where query))))))))

(deftest delete-requires-where-or-scope-test
  (testing "delete! throws without where or scope"
    (with-mock-db []
      (let [repo (sql/create {:table-name :users})]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Delete requires"
                              (repo/delete! repo {})))))))

(deftest count-matching-test
  (testing "count-matching returns count"
    (with-mock-db {:count 42}
      (let [repo   (sql/create {:table-name :users})
            result (repo/count-matching repo {:where {:status :active}})]
        (is (= 42 result))
        (let [query (first @*executed-queries*)]
          (is (= [[[:count :*] :count]] (:select query)))
          (is (= [:and [:= :status :active]] (:where query))))))))

(deftest count-matching-with-scope-test
  (testing "count-matching with scope"
    (with-mock-db {:count 10}
      (let [repo   (sql/create {:table-name :games
                                :scopes {:waiting (fn [_] [:= :status [:lift :waiting]])}})
            result (repo/count-matching repo {:scope :waiting})]
        (is (= 10 result))))))
