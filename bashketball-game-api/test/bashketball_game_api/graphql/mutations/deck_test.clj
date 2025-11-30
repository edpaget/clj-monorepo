(ns bashketball-game-api.graphql.mutations.deck-test
  "Tests for deck GraphQL mutations.

  Tests create, update, delete operations and card management
  for decks via GraphQL mutations."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

;; Create mutations

(deftest create-deck-test
  (testing "createDeck creates a new deck"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation CreateDeck($name: String!) {
                       createDeck(name: $name) { id name cardSlugs isValid }
                     }"
                      :variables {:name "New Deck"}
                      :session-id session-id)
          deck       (get-in (tu/graphql-data response) [:createDeck])]
      (is (some? (:id deck)))
      (is (= "New Deck" (:name deck)))
      (is (= [] (:cardSlugs deck)))
      (is (false? (:isValid deck))))))

(deftest create-deck-unauthenticated-test
  (testing "createDeck returns errors when not authenticated"
    (let [response (tu/graphql-request
                    "mutation { createDeck(name: \"Test\") { id } }")]
      (is (seq (tu/graphql-errors response))))))

;; Update mutations

(deftest update-deck-name-test
  (testing "updateDeck can change deck name"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "Original Name")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation UpdateDeck($id: Uuid!, $name: String) {
                       updateDeck(id: $id, name: $name) { id name }
                     }"
                      :variables {:id (str (:id deck)) :name "New Name"}
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:updateDeck])]
      (is (= "New Name" (:name result))))))

(deftest update-deck-not-owned-test
  (testing "updateDeck returns null for deck owned by another user"
    (let [user1      (tu/create-test-user "user-1")
          user2      (tu/create-test-user "user-2")
          deck       (tu/create-test-deck (:id user1) "User 1 Deck")
          session-id (tu/create-authenticated-session! (:id user2) :user user2)
          response   (tu/graphql-request
                      "mutation UpdateDeck($id: Uuid!, $name: String) {
                       updateDeck(id: $id, name: $name) { id }
                     }"
                      :variables {:id (str (:id deck)) :name "Hacked Name"}
                      :session-id session-id)]
      (is (nil? (get-in (tu/graphql-data response) [:updateDeck]))))))

;; Card management mutations

(deftest add-cards-to-deck-test
  (testing "addCardsToDeck adds cards to an existing deck"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "My Deck")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation AddCards($id: Uuid!, $cardSlugs: [String!]!) {
                       addCardsToDeck(id: $id, cardSlugs: $cardSlugs) {
                         id cardSlugs
                       }
                     }"
                      :variables {:id (str (:id deck))
                                  :cardSlugs ["michael-jordan" "basic-shot"]}
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:addCardsToDeck])]
      (is (= 2 (count (:cardSlugs result))))
      (is (= #{"michael-jordan" "basic-shot"} (set (:cardSlugs result)))))))

(deftest add-cards-to-deck-not-owned-test
  (testing "addCardsToDeck returns null for deck owned by another user"
    (let [user1      (tu/create-test-user "user-1")
          user2      (tu/create-test-user "user-2")
          deck       (tu/create-test-deck (:id user1) "User 1 Deck")
          session-id (tu/create-authenticated-session! (:id user2) :user user2)
          response   (tu/graphql-request
                      "mutation AddCards($id: Uuid!, $cardSlugs: [String!]!) {
                       addCardsToDeck(id: $id, cardSlugs: $cardSlugs) { id }
                     }"
                      :variables {:id (str (:id deck)) :cardSlugs ["michael-jordan"]}
                      :session-id session-id)]
      (is (nil? (get-in (tu/graphql-data response) [:addCardsToDeck]))))))

(deftest remove-cards-from-deck-test
  (testing "removeCardsFromDeck removes cards from a deck"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "My Deck"
                                          ["michael-jordan" "basic-shot" "basic-pass"])
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation RemoveCards($id: Uuid!, $cardSlugs: [String!]!) {
                       removeCardsFromDeck(id: $id, cardSlugs: $cardSlugs) {
                         id cardSlugs
                       }
                     }"
                      :variables {:id (str (:id deck))
                                  :cardSlugs ["basic-shot"]}
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:removeCardsFromDeck])]
      (is (= 2 (count (:cardSlugs result))))
      (is (= #{"michael-jordan" "basic-pass"} (set (:cardSlugs result)))))))

(deftest remove-cards-from-deck-not-owned-test
  (testing "removeCardsFromDeck returns null for deck owned by another user"
    (let [user1      (tu/create-test-user "user-1")
          user2      (tu/create-test-user "user-2")
          deck       (tu/create-test-deck (:id user1) "User 1 Deck" ["michael-jordan"])
          session-id (tu/create-authenticated-session! (:id user2) :user user2)
          response   (tu/graphql-request
                      "mutation RemoveCards($id: Uuid!, $cardSlugs: [String!]!) {
                       removeCardsFromDeck(id: $id, cardSlugs: $cardSlugs) { id }
                     }"
                      :variables {:id (str (:id deck)) :cardSlugs ["michael-jordan"]}
                      :session-id session-id)]
      (is (nil? (get-in (tu/graphql-data response) [:removeCardsFromDeck]))))))

;; Delete mutations

(deftest delete-deck-test
  (testing "deleteDeck removes deck"
    (let [user            (tu/create-test-user)
          deck            (tu/create-test-deck (:id user) "To Delete")
          session-id      (tu/create-authenticated-session! (:id user) :user user)
          delete-response (tu/graphql-request
                           "mutation DeleteDeck($id: Uuid!) { deleteDeck(id: $id) }"
                           :variables {:id (str (:id deck))}
                           :session-id session-id)
          ;; Verify deletion by querying
          query-response  (tu/graphql-request
                           "query GetDeck($id: Uuid!) { deck(id: $id) { id } }"
                           :variables {:id (str (:id deck))}
                           :session-id session-id)]
      (is (true? (get-in (tu/graphql-data delete-response) [:deleteDeck])))
      (is (nil? (get-in (tu/graphql-data query-response) [:deck]))))))

(deftest delete-deck-not-owned-test
  (testing "deleteDeck returns false for deck owned by another user"
    (let [user1      (tu/create-test-user "user-1")
          user2      (tu/create-test-user "user-2")
          deck       (tu/create-test-deck (:id user1) "User 1 Deck")
          session-id (tu/create-authenticated-session! (:id user2) :user user2)
          response   (tu/graphql-request
                      "mutation DeleteDeck($id: Uuid!) { deleteDeck(id: $id) }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)]
      (is (false? (get-in (tu/graphql-data response) [:deleteDeck]))))))

(deftest delete-deck-not-found-test
  (testing "deleteDeck returns false for nonexistent deck"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          fake-id    "00000000-0000-0000-0000-000000000000"
          response   (tu/graphql-request
                      "mutation DeleteDeck($id: Uuid!) { deleteDeck(id: $id) }"
                      :variables {:id fake-id}
                      :session-id session-id)]
      (is (false? (get-in (tu/graphql-data response) [:deleteDeck]))))))
