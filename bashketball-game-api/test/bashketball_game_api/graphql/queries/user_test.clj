(ns bashketball-game-api.graphql.queries.user-test
  "Tests for user GraphQL queries.

  Tests the `me` query with both authenticated and unauthenticated requests,
  verifying that user data is returned correctly when authenticated."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(deftest me-query-unauthenticated-test
  (testing "me query returns null when not authenticated"
    (let [response (tu/graphql-request "{ me { id email name } }")]
      (is (nil? (get-in (tu/graphql-data response) [:me]))))))

(deftest me-query-authenticated-test
  (testing "me query returns user data when authenticated"
    (let [user       (tu/create-test-user "google-123")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request "{ me { id email name avatarUrl } }"
                                         :session-id session-id)
          me         (get-in (tu/graphql-data response) [:me])]
      (is (= (str (:id user)) (:id me)))
      (is (= (:email user) (:email me)))
      (is (= (:name user) (:name me)))
      (is (= (:avatar-url user) (:avatarUrl me))))))

(deftest me-query-returns-claims-test
  (testing "me query returns data from session claims"
    (let [user       (tu/create-test-user "google-456")
          session-id (tu/create-authenticated-session!
                      (:id user)
                      :claims {:email "custom@example.com"
                               :name "Custom Name"
                               :picture "https://custom.com/pic.png"})
          response   (tu/graphql-request "{ me { id email name avatarUrl } }"
                                         :session-id session-id)
          me         (get-in (tu/graphql-data response) [:me])]
      (is (= (str (:id user)) (:id me)))
      (is (= "custom@example.com" (:email me)))
      (is (= "Custom Name" (:name me)))
      (is (= "https://custom.com/pic.png" (:avatarUrl me))))))
