(ns bashketball-editor-api.graphql.resolvers.mutation-test
  (:require
   [bashketball-editor-api.graphql.resolvers.mutation :as mutation]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.test :refer [deftest is testing]]
   [com.walmartlabs.lacinia.resolve :as resolve]))

(def test-user-id (random-uuid))

(defn make-ctx
  ([authenticated?]
   (make-ctx authenticated? {}))
  ([authenticated? extra]
   (merge {:request {:authn/authenticated? authenticated?
                     :authn/user-id (str test-user-id)}}
          extra)))

(deftest pull-from-remote-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx false)
          [_schema resolver] (get mutation/resolvers [:Mutation :pullFromRemote])
          result             (resolver ctx {} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))))))

(deftest push-to-remote-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx false)
          [_schema resolver] (get mutation/resolvers [:Mutation :pushToRemote])
          result             (resolver ctx {} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))))))

;; Mock repositories for branch/changes tests
(defrecord MockBranchRepo [branches current-branch]
  proto/Repository
  (find-all [_this _opts]
    (mapv (fn [b] {:name b :current (= b current-branch)}) branches))
  (find-by [_this {:keys [name current]}]
    (if current
      {:name current-branch :current true}
      (when (some #(= name %) branches)
        {:name name :current (= name current-branch)})))
  (create! [_this {:keys [name]}]
    {:status "success" :message (str "Created " name) :branch name})
  (update! [_this {:keys [name]} _data]
    (if (some #(= name %) branches)
      {:status "success" :message (str "Switched to " name) :branch name}
      {:status "error" :message (str "Branch '" name "' not found")}))
  (delete! [_this _id]
    (throw (ex-info "Not implemented" {:operation :delete!}))))

(defrecord MockChangesRepo [dirty?]
  proto/Repository
  (find-all [_this _opts]
    {:added (if dirty? ["file.txt"] [])
     :modified []
     :deleted []
     :untracked []
     :is-dirty dirty?})
  (find-by [_this _criteria]
    (throw (ex-info "Not implemented" {:operation :find-by})))
  (create! [_this _data]
    (throw (ex-info "Not implemented" {:operation :create!})))
  (update! [_this _id _data]
    (throw (ex-info "Not implemented" {:operation :update!})))
  (delete! [_this _id]
    {:status "success" :message "Changes discarded"}))

(deftest switch-branch-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx false)
          [_schema resolver] (get mutation/resolvers [:Mutation :switchBranch])
          result             (resolver ctx {:branch "feature"} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value))))))

  (testing "blocks switch when dirty"
    (let [ctx                (make-ctx true {:branch-repo (->MockBranchRepo ["main" "feature"] "main")
                                             :changes-repo (->MockChangesRepo true)})
          [_schema resolver] (get mutation/resolvers [:Mutation :switchBranch])
          result             (resolver ctx {:branch "feature"} nil)]
      (is (= "error" (:status result)))
      (is (re-find #"[Cc]ommit or discard" (:message result)))))

  (testing "switches branch when clean"
    (let [ctx                (make-ctx true {:branch-repo (->MockBranchRepo ["main" "feature"] "main")
                                             :changes-repo (->MockChangesRepo false)})
          [_schema resolver] (get mutation/resolvers [:Mutation :switchBranch])
          result             (resolver ctx {:branch "feature"} nil)]
      (is (= "success" (:status result)))
      (is (= "feature" (:branch result))))))

(deftest create-branch-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx false)
          [_schema resolver] (get mutation/resolvers [:Mutation :createBranch])
          result             (resolver ctx {:branch "new-feature"} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value))))))

  (testing "blocks create when dirty"
    (let [ctx                (make-ctx true {:branch-repo (->MockBranchRepo ["main"] "main")
                                             :changes-repo (->MockChangesRepo true)})
          [_schema resolver] (get mutation/resolvers [:Mutation :createBranch])
          result             (resolver ctx {:branch "new-feature"} nil)]
      (is (= "error" (:status result)))
      (is (re-find #"[Cc]ommit or discard" (:message result)))))

  (testing "creates branch when clean"
    (let [ctx                (make-ctx true {:branch-repo (->MockBranchRepo ["main"] "main")
                                             :changes-repo (->MockChangesRepo false)})
          [_schema resolver] (get mutation/resolvers [:Mutation :createBranch])
          result             (resolver ctx {:branch "new-feature"} nil)]
      (is (= "success" (:status result)))
      (is (= "new-feature" (:branch result))))))

(deftest discard-changes-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx false)
          [_schema resolver] (get mutation/resolvers [:Mutation :discardChanges])
          result             (resolver ctx {} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value))))))

  (testing "discards changes"
    (let [ctx                (make-ctx true {:changes-repo (->MockChangesRepo true)})
          [_schema resolver] (get mutation/resolvers [:Mutation :discardChanges])
          result             (resolver ctx {} nil)]
      (is (= "success" (:status result))))))
