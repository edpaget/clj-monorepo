(ns bashketball-editor-api.graphql.resolvers.user-test
  (:require
   [bashketball-editor-api.graphql.resolvers.user :as user]
   [bashketball-editor-api.models.protocol :as repo]
   [clojure.test :refer [deftest is testing]]
   [com.walmartlabs.lacinia.resolve :as resolve]
   [malli.core :as m]))

(def test-user-id (random-uuid))

(def mock-user
  {:id test-user-id
   :github-login "testuser"
   :email "test@example.com"
   :avatar-url "https://example.com/avatar.png"
   :name "Test User"})

(defrecord MockUserRepo []
  repo/Repository
  (find-by [_ criteria]
    (when (or (= (:id criteria) test-user-id)
              (= (:github-login criteria) "testuser"))
      mock-user))
  (find-all [_ _opts] [mock-user])
  (create! [_ _data] mock-user)
  (update! [_ _id _data] mock-user)
  (delete! [_ _id] true))

(deftest user-schema-test
  (testing "validates a complete user"
    (let [user {:id (str test-user-id)
                :github-login "testuser"
                :email "test@example.com"
                :avatar-url "https://example.com/avatar.png"
                :name "Test User"}]
      (is (m/validate user/User user))))

  (testing "validates a minimal user"
    (let [user {:id (str test-user-id)
                :github-login "testuser"
                :email nil
                :avatar-url nil
                :name nil}]
      (is (m/validate user/User user)))))

(deftest user-resolver-authentication-test
  (testing "returns error when not authenticated"
    (let [ctx                {:request {:authn/authenticated? false}
                              :user-repo (->MockUserRepo)}
          [_schema resolver] (get user/resolvers [:Query :user])
          result             (resolver ctx {:id (str test-user-id)} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))
        (is (= "Authentication required" (:message (:data wrapped-value)))))))

  (testing "returns user when authenticated"
    (let [ctx                {:request {:authn/authenticated? true}
                              :user-repo (->MockUserRepo)}
          [_schema resolver] (get user/resolvers [:Query :user])
          result             (resolver ctx {:id (str test-user-id)} nil)]
      (is (= (str test-user-id) (:id result)))
      (is (= "testuser" (:githubLogin result))))))

(deftest users-resolver-authentication-test
  (testing "returns error when not authenticated"
    (let [ctx                {:request {:authn/authenticated? false}
                              :user-repo (->MockUserRepo)}
          [_schema resolver] (get user/resolvers [:Query :users])
          result             (resolver ctx {} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))
        (is (= "Authentication required" (:message (:data wrapped-value)))))))

  (testing "returns users when authenticated"
    (let [ctx                {:request {:authn/authenticated? true}
                              :user-repo (->MockUserRepo)}
          [_schema resolver] (get user/resolvers [:Query :users])
          result             (resolver ctx {} nil)]
      (is (vector? result))
      (is (= 1 (count result)))
      (is (= "testuser" (:githubLogin (first result)))))))

(deftest user-resolver-criteria-test
  (testing "finds user by id"
    (let [ctx                {:request {:authn/authenticated? true}
                              :user-repo (->MockUserRepo)}
          [_schema resolver] (get user/resolvers [:Query :user])
          result             (resolver ctx {:id (str test-user-id)} nil)]
      (is (some? result))))

  (testing "finds user by github-login"
    (let [ctx                {:request {:authn/authenticated? true}
                              :user-repo (->MockUserRepo)}
          [_schema resolver] (get user/resolvers [:Query :user])
          result             (resolver ctx {:github-login "testuser"} nil)]
      (is (some? result))))

  (testing "returns nil when no criteria provided"
    (let [ctx                {:request {:authn/authenticated? true}
                              :user-repo (->MockUserRepo)}
          [_schema resolver] (get user/resolvers [:Query :user])
          result             (resolver ctx {} nil)]
      (is (nil? result)))))
