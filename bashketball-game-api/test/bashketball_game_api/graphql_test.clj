(ns bashketball-game-api.graphql-test
  "Tests for GraphQL endpoint."
  (:require
   [bashketball-game-api.test-utils :refer [with-server server-port]]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-server)

(defn graphql-url []
  (str "http://localhost:" (server-port) "/graphql"))

(defn execute-query [query & [variables]]
  (let [response (http/post (graphql-url)
                            {:body (json/generate-string
                                    {:query query
                                     :variables variables})
                             :content-type :json
                             :as :json
                             :throw-exceptions false})]
    (:body response)))

(deftest graphiql-served-test
  (testing "GraphiQL is served on GET /graphql"
    (let [response (http/get (graphql-url)
                             {:throw-exceptions false})]
      (is (= 200 (:status response)))
      (is (str/includes? (:body response) "GraphiQL")))))

(deftest sets-query-test
  (testing "Query all sets"
    (let [result (execute-query "{ sets { slug name description } }")]
      (is (nil? (:errors result)))
      (is (seq (get-in result [:data :sets])))
      (is (some #(= "base" (:slug %)) (get-in result [:data :sets]))))))

(deftest set-query-test
  (testing "Query single set by slug"
    (let [result (execute-query "{ set(slug: \"base\") { slug name description } }")]
      (is (nil? (:errors result)))
      (is (= "base" (get-in result [:data :set :slug])))
      (is (some? (get-in result [:data :set :name])))))

  (testing "Query nonexistent set returns null"
    (let [result (execute-query "{ set(slug: \"nonexistent\") { slug name } }")]
      (is (nil? (:errors result)))
      (is (nil? (get-in result [:data :set]))))))

(deftest cards-query-test
  (testing "Query all cards"
    (let [result (execute-query "{ cards { ... on PlayerCard { slug name cardType } ... on StandardActionCard { slug name cardType } ... on SplitPlayCard { slug name cardType } ... on CoachingCard { slug name cardType } ... on TeamAssetCard { slug name cardType } ... on AbilityCard { slug name cardType } ... on PlayCard { slug name cardType } } }")]
      (is (nil? (:errors result)))
      (is (seq (get-in result [:data :cards]))))))

(deftest cards-by-set-query-test
  (testing "Query cards filtered by set returns cards"
    (let [result (execute-query "{ cards(setSlug: \"base\") { ... on PlayerCard { slug name setSlug } ... on StandardActionCard { slug name setSlug } ... on CoachingCard { slug name setSlug } ... on PlayCard { slug name setSlug } ... on SplitPlayCard { slug name setSlug } ... on TeamAssetCard { slug name setSlug } ... on AbilityCard { slug name setSlug } } }")]
      (is (nil? (:errors result)))
      (is (seq (get-in result [:data :cards]))))))

(deftest card-query-test
  (testing "Query single card by slug"
    (let [result (execute-query "{ card(slug: \"michael-jordan\") { ... on PlayerCard { slug name cardType sht pss def speed size abilities { id } } } }")]
      (is (nil? (:errors result)))
      (is (= "michael-jordan" (get-in result [:data :card :slug])))
      (is (= "Michael Jordan" (get-in result [:data :card :name])))
      (is (= "PLAYER_CARD" (get-in result [:data :card :cardType])))
      (is (integer? (get-in result [:data :card :sht])))))

  (testing "Query nonexistent card returns null"
    (let [result (execute-query "{ card(slug: \"nonexistent\") { ... on PlayerCard { slug name } } }")]
      (is (nil? (:errors result)))
      (is (nil? (get-in result [:data :card]))))))

(deftest me-query-test
  (testing "Me query returns null when not authenticated"
    (let [result (execute-query "{ me { id email name avatarUrl } }")]
      (is (nil? (:errors result)))
      (is (nil? (get-in result [:data :me]))))))
