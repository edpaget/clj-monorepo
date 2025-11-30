(ns bashketball-game-api.graphql.field-resolvers-test
  "Tests for GraphQL field resolvers.

  Tests field resolvers like Deck.cards that resolve nested data
  from related entities."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(def card-inline-fragments
  "Inline fragments for querying card fields."
  "... on PlayerCard { slug name cardType }
   ... on StandardActionCard { slug name cardType }
   ... on SplitPlayCard { slug name cardType }
   ... on CoachingCard { slug name cardType }
   ... on TeamAssetCard { slug name cardType }
   ... on AbilityCard { slug name cardType }
   ... on PlayCard { slug name cardType }")

(deftest deck-cards-field-resolver-test
  (testing "Deck.cards field resolver returns full card objects"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "Test Deck"
                                          ["michael-jordan" "basic-shot"])
          session-id (tu/create-authenticated-session! (:id user) :user user)
          query      (str "query GetDeck($id: Uuid!) {
                        deck(id: $id) {
                          name
                          cardSlugs
                          cards { " card-inline-fragments " }
                        }
                      }")
          response   (tu/graphql-request query
                                         :variables {:id (str (:id deck))}
                                         :session-id session-id)
          deck-data  (get-in (tu/graphql-data response) [:deck])]
      (is (= 2 (count (:cards deck-data))))
      (is (= #{"michael-jordan" "basic-shot"}
             (set (map :slug (:cards deck-data))))))))

(deftest deck-cards-empty-deck-test
  (testing "Deck.cards returns empty array for deck with no cards"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "Empty Deck")
          session-id (tu/create-authenticated-session! (:id user) :user user)
          query      (str "query GetDeck($id: Uuid!) {
                        deck(id: $id) { cards { " card-inline-fragments " } }
                      }")
          response   (tu/graphql-request query
                                         :variables {:id (str (:id deck))}
                                         :session-id session-id)]
      (is (= [] (get-in (tu/graphql-data response) [:deck :cards]))))))

(deftest deck-cards-returns-card-details-test
  (testing "Deck.cards returns card-specific fields"
    (let [user       (tu/create-test-user)
          deck       (tu/create-test-deck (:id user) "Test Deck" ["michael-jordan"])
          session-id (tu/create-authenticated-session! (:id user) :user user)
          query      "query GetDeck($id: Uuid!) {
                   deck(id: $id) {
                     cards {
                       ... on PlayerCard { slug name sht pss def speed size }
                     }
                   }
                 }"
          response   (tu/graphql-request query
                                         :variables {:id (str (:id deck))}
                                         :session-id session-id)
          card       (first (get-in (tu/graphql-data response) [:deck :cards]))]
      (is (= "michael-jordan" (:slug card)))
      (is (integer? (:sht card)))
      (is (integer? (:speed card))))))
