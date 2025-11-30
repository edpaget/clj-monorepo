(ns bashketball-game-api.graphql.queries.deck-test
  "Tests for deck GraphQL queries.

  Tests the `myDecks` and `deck` queries with authentication requirements
  and data isolation between users."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(deftest my-decks-unauthenticated-test
  (testing "myDecks query returns errors when not authenticated"
    (let [response (tu/graphql-request "{ myDecks { id name } }")]
      (is (seq (tu/graphql-errors response)))
      (is (nil? (get-in response [:data :myDecks]))))))

(deftest my-decks-empty-test
  (testing "myDecks returns empty array for user with no decks"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request "{ myDecks { id name cardSlugs } }"
                                         :session-id session-id)]
      (is (= [] (get-in (tu/graphql-data response) [:myDecks]))))))

(deftest my-decks-with-decks-test
  (testing "myDecks returns user's decks"
    (let [user       (tu/create-test-user)
          _          (tu/create-test-deck (:id user) "Deck 1")
          _          (tu/create-test-deck (:id user) "Deck 2")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request "{ myDecks { id name } }"
                                         :session-id session-id)
          decks      (get-in (tu/graphql-data response) [:myDecks])]
      (is (= 2 (count decks)))
      (is (= #{"Deck 1" "Deck 2"} (set (map :name decks)))))))

(deftest my-decks-isolation-test
  (testing "myDecks only returns decks owned by the authenticated user"
    (let [user1      (tu/create-test-user "user-1")
          user2      (tu/create-test-user "user-2")
          _          (tu/create-test-deck (:id user1) "User 1 Deck")
          _          (tu/create-test-deck (:id user2) "User 2 Deck")
          session-id (tu/create-authenticated-session! (:id user1) :user user1)
          response   (tu/graphql-request "{ myDecks { name } }"
                                         :session-id session-id)
          decks      (get-in (tu/graphql-data response) [:myDecks])]
      (is (= 1 (count decks)))
      (is (= "User 1 Deck" (:name (first decks)))))))

(deftest deck-query-test
  (testing "deck query returns specific deck by id"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "My Deck")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "query GetDeck($id: Uuid!) { deck(id: $id) { id name cardSlugs isValid } }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:deck])]
      (is (= "My Deck" (:name result)))
      (is (= [] (:cardSlugs result))))))

(deftest deck-query-unauthenticated-test
  (testing "deck query returns errors when not authenticated"
    (let [user     (tu/create-test-user)
          deck     (tu/create-test-deck (:id user) "My Deck")
          response (tu/graphql-request
                    "query GetDeck($id: Uuid!) { deck(id: $id) { id } }"
                    :variables {:id (str (:id deck))})]
      (is (seq (tu/graphql-errors response))))))

(deftest deck-query-not-owned-test
  (testing "deck query returns null for deck owned by another user"
    (let [user1      (tu/create-test-user "user-1")
          user2      (tu/create-test-user "user-2")
          deck       (tu/create-test-deck (:id user1) "User 1 Deck")
          session-id (tu/create-authenticated-session! (:id user2) :user user2)
          response   (tu/graphql-request
                      "query GetDeck($id: Uuid!) { deck(id: $id) { id } }"
                      :variables {:id (str (:id deck))}
                      :session-id session-id)]
      (is (nil? (get-in (tu/graphql-data response) [:deck]))))))

(deftest deck-query-not-found-test
  (testing "deck query returns null for nonexistent deck"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          fake-id    "00000000-0000-0000-0000-000000000000"
          response   (tu/graphql-request
                      "query GetDeck($id: Uuid!) { deck(id: $id) { id } }"
                      :variables {:id fake-id}
                      :session-id session-id)]
      (is (nil? (get-in (tu/graphql-data response) [:deck]))))))
