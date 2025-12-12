(ns bashketball-editor-api.graphql.resolvers.query-test
  "Tests for GraphQL Query resolvers."
  (:require
   [bashketball-editor-api.graphql.resolvers.query :as query]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.test :refer [deftest is testing]]))

;; Mock branch repository for testing
(defrecord MockBranchRepo [branches current-branch]
  proto/Repository
  (find-all [_this _opts]
    (mapv (fn [b] {:name b :current (= b current-branch)}) branches))
  (find-by [_this {:keys [name current]}]
    (if current
      {:name current-branch :current true}
      (when (some #(= name %) branches)
        {:name name :current (= name current-branch)})))
  (create! [_this _data]
    (throw (ex-info "Not implemented" {:operation :create!})))
  (update! [_this _id _data]
    (throw (ex-info "Not implemented" {:operation :update!})))
  (delete! [_this _id]
    (throw (ex-info "Not implemented" {:operation :delete!}))))

(deftest branch-info-query-test
  (testing "returns current branch and list of branches"
    (let [ctx                {:branch-repo (->MockBranchRepo ["main" "feature" "develop"] "feature")}
          [_schema resolver] (get query/resolvers [:Query :branchInfo])
          result             (resolver ctx {} nil)]
      (is (= "feature" (:currentBranch result)))
      (is (= ["main" "feature" "develop"] (:branches result)))))

  (testing "returns nil current-branch when no branches exist"
    (let [ctx                {:branch-repo (->MockBranchRepo [] nil)}
          [_schema resolver] (get query/resolvers [:Query :branchInfo])
          result             (resolver ctx {} nil)]
      (is (nil? (:currentBranch result)))
      (is (= [] (:branches result))))))
