(ns repository.backend.memory-test
  (:require [clojure.test :refer [deftest is testing]]
            [repository.backend.memory :as mem]
            [repository.protocol :as repo]))

(deftest save-generates-id-test
  (testing "save! generates ID for new entity"
    (let [r      (mem/create)
          result (repo/save! r {:name "Alice"})]
      (is (some? (:id result)))
      (is (= "Alice" (:name result))))))

(deftest save-preserves-existing-id-test
  (testing "save! preserves existing ID"
    (let [r      (mem/create)
          id     #uuid "550e8400-e29b-41d4-a716-446655440000"
          result (repo/save! r {:id id :name "Bob"})]
      (is (= id (:id result))))))

(deftest find-one-by-where-test
  (testing "find-one returns entity matching where clause"
    (let [r (mem/create-with-data [{:id 1 :name "Alice"}
                                   {:id 2 :name "Bob"}])]
      (is (= "Alice" (:name (repo/find-one r {:where {:id 1}}))))
      (is (= "Bob" (:name (repo/find-one r {:where {:name "Bob"}})))))))

(deftest find-one-returns-nil-when-not-found-test
  (testing "find-one returns nil when no match"
    (let [r (mem/create-with-data [{:id 1 :name "Alice"}])]
      (is (nil? (repo/find-one r {:where {:id 999}}))))))

(deftest find-many-returns-all-matching-test
  (testing "find-many returns all matching entities"
    (let [r (mem/create-with-data [{:id 1 :status :active}
                                   {:id 2 :status :active}
                                   {:id 3 :status :inactive}])]
      (is (= 2 (count (:data (repo/find-many r {:where {:status :active}}))))))))

(deftest find-many-with-limit-test
  (testing "find-many respects limit"
    (let [r (mem/create-with-data [{:id 1} {:id 2} {:id 3}])]
      (is (= 2 (count (:data (repo/find-many r {:limit 2}))))))))

(deftest find-many-with-order-by-test
  (testing "find-many orders results"
    (let [r (mem/create-with-data [{:id 3 :name "Charlie"}
                                   {:id 1 :name "Alice"}
                                   {:id 2 :name "Bob"}])]
      (is (= [1 2 3] (mapv :id (:data (repo/find-many r {:order-by [[:id :asc]]}))))))))

(deftest find-many-with-order-by-desc-test
  (testing "find-many orders results descending"
    (let [r (mem/create-with-data [{:id 1} {:id 2} {:id 3}])]
      (is (= [3 2 1] (mapv :id (:data (repo/find-many r {:order-by [[:id :desc]]}))))))))

(deftest find-many-with-scope-test
  (testing "find-many applies scope predicate"
    (let [scopes {:active (fn [_] #(= (:status %) :active))}
          r      (mem/create-with-data [{:id 1 :status :active}
                                        {:id 2 :status :inactive}
                                        {:id 3 :status :active}]
                                       {:scopes scopes})]
      (is (= 2 (count (:data (repo/find-many r {:scope :active}))))))))

(deftest find-many-with-scope-params-test
  (testing "find-many passes scope-params to scope function"
    (let [scopes {:by-user (fn [{:keys [user-id]}]
                             #(= (:user-id %) user-id))}
          r      (mem/create-with-data [{:id 1 :user-id "a"}
                                        {:id 2 :user-id "b"}
                                        {:id 3 :user-id "a"}]
                                       {:scopes scopes})]
      (is (= 2 (count (:data (repo/find-many r {:scope :by-user
                                                :scope-params {:user-id "a"}}))))))))

(deftest find-many-unknown-scope-throws-test
  (testing "find-many throws for unknown scope"
    (let [r (mem/create)]
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error)
                            #"Unknown scope"
                            (repo/find-many r {:scope :nonexistent}))))))

(deftest delete-removes-matching-test
  (testing "delete! removes matching entities"
    (let [r (mem/create-with-data [{:id 1 :status :inactive}
                                   {:id 2 :status :active}])]
      (is (= 1 (repo/delete! r {:where {:status :inactive}})))
      (is (= 1 (count (:data (repo/find-many r {}))))))))

(deftest count-matching-test
  (testing "count-matching returns count of matching entities"
    (let [r (mem/create-with-data [{:id 1 :status :active}
                                   {:id 2 :status :active}
                                   {:id 3 :status :inactive}])]
      (is (= 2 (repo/count-matching r {:where {:status :active}})))
      (is (= 3 (repo/count-matching r {}))))))

(deftest pagination-with-cursor-test
  (testing "cursor-based pagination works correctly"
    (let [r      (mem/create-with-data [{:id 1} {:id 2} {:id 3} {:id 4} {:id 5}])
          page1  (repo/find-many r {:order-by [[:id :asc]] :limit 2})
          cursor (:end-cursor (:page-info page1))
          page2  (repo/find-many r {:order-by [[:id :asc]]
                                    :limit 2
                                    :cursor {:after cursor}})]
      (is (= [1 2] (mapv :id (:data page1))))
      (is (:has-next-page (:page-info page1)))
      (is (= [3 4] (mapv :id (:data page2))))
      (is (:has-next-page (:page-info page2))))))
