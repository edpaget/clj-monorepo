(ns repository.test-test
  (:require [clojure.test :refer [deftest is testing]]
            [repository.protocol :as repo]
            [repository.test :as rt]))

(deftest mock-repository-with-data-test
  (testing "creates repository with initial data"
    (let [r (rt/mock-repository [{:id 1 :name "Alice"}
                                 {:id 2 :name "Bob"}])]
      (is (= "Alice" (:name (repo/find-one r {:where {:id 1}}))))
      (is (= 2 (count (:data (repo/find-many r {}))))))))

(deftest mock-repository-with-scopes-test
  (testing "creates repository with scopes"
    (let [r (rt/mock-repository [{:id 1 :status :active}
                                 {:id 2 :status :inactive}]
                                {:scopes {:active (fn [_] #(= (:status %) :active))}})]
      (is (= 1 (count (:data (repo/find-many r {:scope :active}))))))))

(deftest tracking-repository-records-calls-test
  (testing "tracking-repository records all operations"
    (let [{:keys [repo calls]} (rt/tracking-repository
                                (rt/mock-repository [{:id 1 :name "Alice"}]))]
      (repo/find-one repo {:where {:id 1}})
      (repo/save! repo {:name "Bob"})
      (is (= 2 (count @calls)))
      (is (= :find-one (:op (first @calls))))
      (is (= :save! (:op (second @calls)))))))

(deftest tracking-calls-for-filters-test
  (testing "calls-for filters by operation"
    (let [{:keys [repo calls]} (rt/tracking-repository (rt/mock-repository []))]
      (repo/save! repo {:name "Alice"})
      (repo/save! repo {:name "Bob"})
      (repo/find-many repo {})
      (is (= 2 (count (rt/calls-for @calls :save!))))
      (is (= 1 (count (rt/calls-for @calls :find-many)))))))

(deftest saved-entities-returns-results-test
  (testing "saved-entities returns saved entity results"
    (let [{:keys [repo calls]} (rt/tracking-repository (rt/mock-repository []))]
      (repo/save! repo {:name "Alice"})
      (repo/save! repo {:name "Bob"})
      (is (= 2 (count (rt/saved-entities @calls))))
      (is (= #{"Alice" "Bob"} (set (map :name (rt/saved-entities @calls))))))))

(deftest deleted-count-sums-deletes-test
  (testing "deleted-count sums all delete results"
    (let [{:keys [repo calls]} (rt/tracking-repository
                                (rt/mock-repository [{:id 1} {:id 2} {:id 3}]))]
      (repo/delete! repo {:where {:id 1}})
      (repo/delete! repo {:where {:id 2}})
      (is (= 2 (rt/deleted-count @calls))))))

#?(:clj
   (deftest with-repository-macro-test
     (testing "with-repository macro creates scoped repository"
       (rt/with-repository [r [{:id 1 :name "Test"}]]
         (is (= "Test" (:name (repo/find-one r {:where {:id 1}}))))))))
