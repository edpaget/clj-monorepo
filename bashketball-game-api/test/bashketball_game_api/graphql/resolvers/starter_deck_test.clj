(ns bashketball-game-api.graphql.resolvers.starter-deck-test
  "Tests for starter deck GraphQL queries and mutations."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

;; Query tests

(deftest starter-decks-query-test
  (testing "starterDecks returns all definitions (no auth required)"
    (let [response (tu/graphql-request
                    "query { starterDecks { id name description cardCount } }")
          decks    (get-in (tu/graphql-data response) [:starterDecks])]
      (is (= 3 (count decks)))
      (is (every? #(some? (:id %)) decks))
      (is (every? #(some? (:name %)) decks))
      (is (every? #(pos? (:cardCount %)) decks)))))

(deftest available-starter-decks-query-test
  (testing "availableStarterDecks returns all for new user"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "query { availableStarterDecks { id name } }"
                      :session-id session-id)
          decks      (get-in (tu/graphql-data response) [:availableStarterDecks])]
      (is (= 3 (count decks))))))

(deftest available-starter-decks-unauthenticated-test
  (testing "availableStarterDecks requires authentication"
    (let [response (tu/graphql-request
                    "query { availableStarterDecks { id } }")]
      (is (seq (tu/graphql-errors response))))))

(deftest claimed-starter-decks-empty-test
  (testing "claimedStarterDecks returns empty for new user"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "query { claimedStarterDecks { starterDeckId deckId } }"
                      :session-id session-id)
          claims     (get-in (tu/graphql-data response) [:claimedStarterDecks])]
      (is (empty? claims)))))

(deftest claimed-starter-decks-unauthenticated-test
  (testing "claimedStarterDecks requires authentication"
    (let [response (tu/graphql-request
                    "query { claimedStarterDecks { starterDeckId } }")]
      (is (seq (tu/graphql-errors response))))))

;; Mutation tests

(deftest claim-starter-deck-test
  (testing "claimStarterDeck creates deck and returns claim"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation ClaimDeck($id: String!) {
                         claimStarterDeck(starterDeckId: $id) {
                           starterDeckId deckId
                         }
                       }"
                      :variables {:id "speed-demons"}
                      :session-id session-id)
          claim      (get-in (tu/graphql-data response) [:claimStarterDeck])]
      (is (= "speed-demons" (:starterDeckId claim)))
      (is (some? (:deckId claim))))))

(deftest claim-starter-deck-idempotent-test
  (testing "Claiming same deck twice returns null second time"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          _first     (tu/graphql-request
                      "mutation { claimStarterDeck(starterDeckId: \"speed-demons\") { starterDeckId } }"
                      :session-id session-id)
          second     (tu/graphql-request
                      "mutation { claimStarterDeck(starterDeckId: \"speed-demons\") { starterDeckId } }"
                      :session-id session-id)
          result     (get-in (tu/graphql-data second) [:claimStarterDeck])]
      (is (nil? result)))))

(deftest claim-different-decks-test
  (testing "User can claim multiple different starter decks"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          first      (tu/graphql-request
                      "mutation { claimStarterDeck(starterDeckId: \"speed-demons\") { starterDeckId } }"
                      :session-id session-id)
          second     (tu/graphql-request
                      "mutation { claimStarterDeck(starterDeckId: \"post-dominance\") { starterDeckId } }"
                      :session-id session-id)]
      (is (some? (get-in (tu/graphql-data first) [:claimStarterDeck])))
      (is (some? (get-in (tu/graphql-data second) [:claimStarterDeck]))))))

(deftest claim-starter-deck-unauthenticated-test
  (testing "claimStarterDeck requires authentication"
    (let [response (tu/graphql-request
                    "mutation { claimStarterDeck(starterDeckId: \"speed-demons\") { starterDeckId } }")]
      (is (seq (tu/graphql-errors response))))))

(deftest claim-invalid-starter-deck-test
  (testing "Claiming non-existent starter deck returns null"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request
                      "mutation { claimStarterDeck(starterDeckId: \"nonexistent\") { starterDeckId } }"
                      :session-id session-id)
          result     (get-in (tu/graphql-data response) [:claimStarterDeck])]
      (is (nil? result)))))

(deftest available-after-claim-test
  (testing "Available decks excludes claimed ones"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          _          (tu/graphql-request
                      "mutation { claimStarterDeck(starterDeckId: \"speed-demons\") { starterDeckId } }"
                      :session-id session-id)
          response   (tu/graphql-request
                      "query { availableStarterDecks { id } }"
                      :session-id session-id)
          available  (get-in (tu/graphql-data response) [:availableStarterDecks])]
      (is (= 2 (count available)))
      (is (not (some #(= "speed-demons" (:id %)) available))))))

(deftest claimed-deck-appears-in-my-decks-test
  (testing "Claimed starter deck appears in user's myDecks query"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          _          (tu/graphql-request
                      "mutation { claimStarterDeck(starterDeckId: \"balanced-attack\") { starterDeckId } }"
                      :session-id session-id)
          response   (tu/graphql-request
                      "query { myDecks { id name isValid } }"
                      :session-id session-id)
          decks      (get-in (tu/graphql-data response) [:myDecks])]
      (is (= 1 (count decks)))
      (is (= "Balanced Attack" (:name (first decks))))
      (is (true? (:isValid (first decks)))))))
