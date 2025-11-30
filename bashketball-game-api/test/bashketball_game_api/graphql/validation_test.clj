(ns bashketball-game-api.graphql.validation-test
  "Tests for deck validation GraphQL operations.

  Tests the validateDeck mutation and how validation state persists
  and can be queried."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(deftest validate-empty-deck-test
  (testing "Empty deck validation returns invalid with errors"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "Empty")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation ValidateDeck($id: Uuid!) {
                       validateDeck(id: $id) { isValid validationErrors }
                     }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:validateDeck])]
      (is (false? (:isValid result)))
      (is (seq (:validationErrors result))))))

(deftest deck-validation-persists-test
  (testing "Deck validation results persist and can be queried"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "Test")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          ;; First validate
          _          (tu/graphql-request
                      "mutation ValidateDeck($id: Uuid!) { validateDeck(id: $id) { isValid } }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)
          ;; Then query
          response   (tu/graphql-request
                      "query GetDeck($id: Uuid!) {
                       deck(id: $id) { isValid validationErrors }
                     }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:deck])]
      (is (false? (:isValid result)))
      (is (seq (:validationErrors result))))))

(deftest validate-deck-unauthenticated-test
  (testing "validateDeck requires authentication"
    (let [user     (tu/create-test-user)
          deck     (tu/create-test-deck (:id user) "Test")
          response (tu/graphql-request
                    "mutation ValidateDeck($id: Uuid!) { validateDeck(id: $id) { isValid } }"
                    :variables {:id (str (:id deck))})]
      (is (seq (tu/graphql-errors response))))))

(deftest validate-deck-not-owned-test
  (testing "validateDeck returns null for deck owned by another user"
    (let [user1      (tu/create-test-user "user-1")
          user2      (tu/create-test-user "user-2")
          deck       (tu/create-test-deck (:id user1) "User 1 Deck")
          session-id (tu/create-authenticated-session! (:id user2) :user user2)
          response   (tu/graphql-request
                      "mutation ValidateDeck($id: Uuid!) { validateDeck(id: $id) { isValid } }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)]
      (is (nil? (get-in (tu/graphql-data response) [:validateDeck]))))))

(deftest validation-error-messages-test
  (testing "Validation errors contain descriptive messages"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "Test" ["michael-jordan"])
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation ValidateDeck($id: Uuid!) {
                       validateDeck(id: $id) { isValid validationErrors }
                     }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:validateDeck])]
      (is (false? (:isValid result)))
      ;; Should have error about not enough player cards
      (is (some #(re-find #"player cards" %) (:validationErrors result))))))
