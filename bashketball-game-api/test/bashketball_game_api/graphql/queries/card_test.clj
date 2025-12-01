(ns bashketball-game-api.graphql.queries.card-test
  "Tests for card catalog GraphQL queries.

  Tests the public `sets`, `set`, `cards`, and `card` queries. These queries
  do not require authentication."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)

(def card-inline-fragments
  "Inline fragments for querying card fields across all card types."
  "... on PlayerCard { slug name cardType setSlug speed size }
   ... on StandardActionCard { slug name cardType setSlug fate }
   ... on SplitPlayCard { slug name cardType setSlug fate }
   ... on CoachingCard { slug name cardType setSlug fate }
   ... on TeamAssetCard { slug name cardType setSlug }
   ... on AbilityCard { slug name cardType setSlug }
   ... on PlayCard { slug name cardType setSlug fate }")

;; Sets queries

(deftest sets-query-test
  (testing "sets query returns all card sets"
    (let [response (tu/graphql-request "{ sets { slug name description } }")
          sets     (get-in (tu/graphql-data response) [:sets])]
      (is (seq sets) "Should have at least one set")
      (is (every? :slug sets))
      (is (every? :name sets)))))

(deftest set-query-test
  (testing "set query returns specific set by slug"
    (let [response (tu/graphql-request
                    "query GetSet($slug: String!) { set(slug: $slug) { slug name description } }"
                    :variables {:slug "base"})
          result   (get-in (tu/graphql-data response) [:set])]
      (is (= "base" (:slug result)) "Expected 'base' set slug")
      (is (some? (:name result)) "Expected set name to be present"))))

(deftest set-query-not-found-test
  (testing "set query returns null for unknown slug"
    (let [response (tu/graphql-request
                    "query GetSet($slug: String!) { set(slug: $slug) { slug } }"
                    :variables {:slug "nonexistent-set"})]
      (is (nil? (get-in (tu/graphql-data response) [:set]))))))

;; Cards queries

(deftest cards-query-test
  (testing "cards query returns all cards"
    (let [query    (str "{ cards { " card-inline-fragments " } }")
          response (tu/graphql-request query)
          cards    (get-in (tu/graphql-data response) [:cards])]
      (is (seq cards) "Should have cards")
      (is (every? :slug cards))
      (is (every? :cardType cards)))))

(deftest cards-query-filtered-by-set-test
  (testing "cards query accepts set slug filter parameter"
    (let [query    (str "query CardsBySet($setSlug: String) {
                        cards(setSlug: $setSlug) { " card-inline-fragments " }
                      }")
          response (tu/graphql-request query :variables {:setSlug "base"})
          cards    (get-in (tu/graphql-data response) [:cards])]
      ;; Verify query returns cards - filter implementation tested at service layer
      (is (seq cards))
      (is (some #(= "base" (:setSlug %)) cards)))))

(deftest card-query-test
  (testing "card query returns specific card by slug"
    (let [query    (str "query GetCard($slug: String!) {
                        card(slug: $slug) { " card-inline-fragments " }
                      }")
          response (tu/graphql-request query :variables {:slug "michael-jordan"})
          card     (get-in (tu/graphql-data response) [:card])]
      (is (= "michael-jordan" (:slug card)))
      (is (= "PLAYER_CARD" (:cardType card))))))

(deftest card-query-not-found-test
  (testing "card query returns null for unknown slug"
    (let [query    (str "query GetCard($slug: String!) {
                        card(slug: $slug) { " card-inline-fragments " }
                      }")
          response (tu/graphql-request query :variables {:slug "nonexistent-card"})]
      (is (nil? (get-in (tu/graphql-data response) [:card]))))))

(deftest player-card-fields-test
  (testing "player card returns player-specific fields"
    (let [query    "query GetCard($slug: String!) {
                   card(slug: $slug) {
                     ... on PlayerCard { slug name sht pss def speed size abilities }
                   }
                 }"
          response (tu/graphql-request query :variables {:slug "michael-jordan"})
          card     (get-in (tu/graphql-data response) [:card])]
      (is (= "michael-jordan" (:slug card)))
      (is (integer? (:sht card)))
      (is (integer? (:pss card)))
      (is (integer? (:def card)))
      (is (integer? (:speed card)))
      (is (some? (:size card))))))
