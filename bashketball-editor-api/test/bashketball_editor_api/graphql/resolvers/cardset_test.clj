(ns bashketball-editor-api.graphql.resolvers.cardset-test
  (:require
   [bashketball-editor-api.graphql.resolvers.cardset :as cardset]
   [bashketball-editor-api.models.protocol :as repo]
   [clojure.test :refer [deftest is testing]]
   [com.walmartlabs.lacinia.resolve :as resolve]))

(def test-set-slug "base-set")
(def test-user-id (random-uuid))

(def mock-card-set
  {:slug test-set-slug
   :name "Base Set"
   :description "The base game cards"
   :created-at (java.time.Instant/now)
   :updated-at (java.time.Instant/now)})

(def mock-card
  {:slug "jordan"
   :name "Jordan"
   :set-slug test-set-slug
   :card-type :card-type/PLAYER_CARD
   :deck-size 5
   :sht 5 :pss 3 :def 4 :speed 4
   :size :size/MD
   :abilities []
   :created-at (java.time.Instant/now)
   :updated-at (java.time.Instant/now)})

(def mock-user
  {:id test-user-id
   :name "Test User"
   :email "test@example.com"
   :github-token "test-token"})

(defrecord MockSetRepo [sets]
  repo/Repository
  (find-by [_ criteria]
    (first (filter #(= (:slug %) (:slug criteria)) @sets)))
  (find-all [_ _] @sets)
  (create! [_ data]
    (let [card-set (-> data
                       (assoc :slug (or (:slug data) (str "set-" (rand-int 10000)))
                              :created-at (java.time.Instant/now)
                              :updated-at (java.time.Instant/now)))]
      (swap! sets conj card-set)
      card-set))
  (update! [_ slug data]
    (if-let [existing (first (filter #(= (:slug %) slug) @sets))]
      (let [updated (-> existing
                        (merge data)
                        (assoc :updated-at (java.time.Instant/now)))]
        (swap! sets (fn [ss] (mapv #(if (= (:slug %) slug) updated %) ss)))
        updated)
      (throw (ex-info "Set not found" {:slug slug}))))
  (delete! [_ _] true))

(defrecord MockCardRepo [cards]
  repo/Repository
  (find-by [_ criteria]
    (first (filter #(and (= (:slug %) (:slug criteria))
                         (= (:set-slug %) (:set-slug criteria)))
                   @cards)))
  (find-all [_ opts]
    (let [set-slug (get-in opts [:where :set-slug])]
      (if set-slug
        (vec (filter #(= (:set-slug %) set-slug) @cards))
        @cards)))
  (create! [_ data] data)
  (update! [_ _ data] data)
  (delete! [_ _] true))

(defrecord MockUserRepo []
  repo/Repository
  (find-by [_ _] mock-user)
  (find-all [_ _] [mock-user])
  (create! [_ _] mock-user)
  (update! [_ _ _] mock-user)
  (delete! [_ _] true))

(defn make-ctx
  ([sets cards] (make-ctx sets cards true))
  ([sets cards authenticated?]
   {:set-repo (->MockSetRepo (atom sets))
    :card-repo (->MockCardRepo (atom cards))
    :user-repo (->MockUserRepo)
    :request {:authn/authenticated? authenticated?
              :authn/user-id (str test-user-id)}}))

(deftest card-set-query-test
  (testing "returns card set when found"
    (let [ctx                (make-ctx [mock-card-set] [])
          [_schema resolver] (get cardset/resolvers [:Query :cardSet])
          result             (resolver ctx {:slug test-set-slug} nil)]
      (is (= test-set-slug (:slug result)))
      (is (= "Base Set" (:name result)))))

  (testing "returns nil when set not found"
    (let [ctx                (make-ctx [] [])
          [_schema resolver] (get cardset/resolvers [:Query :cardSet])
          result             (resolver ctx {:slug "nonexistent-set"} nil)]
      (is (nil? result)))))

(deftest card-sets-query-test
  (testing "returns all card sets wrapped in :data"
    (let [another-set        {:slug "expansion-1" :name "Expansion 1"}
          ctx                (make-ctx [mock-card-set another-set] [])
          [_schema resolver] (get cardset/resolvers [:Query :cardSets])
          result             (resolver ctx {} nil)]
      (is (map? result))
      (is (= 2 (count (:data result))))))

  (testing "returns empty vector when no sets"
    (let [ctx                (make-ctx [] [])
          [_schema resolver] (get cardset/resolvers [:Query :cardSets])
          result             (resolver ctx {} nil)]
      (is (= [] (:data result))))))

(deftest create-card-set-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx [] [] false)
          [_schema resolver] (get cardset/resolvers [:Mutation :createCardSet])
          result             (resolver ctx {:input {:name "New Set"}} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value))))))

  (testing "creates card set when authenticated"
    (let [ctx                (make-ctx [] [] true)
          [_schema resolver] (get cardset/resolvers [:Mutation :createCardSet])
          result             (resolver ctx {:input {:name "New Set" :description "A new set"}} nil)]
      (is (some? (:slug result)))
      (is (= "New Set" (:name result)))
      (is (= "A new set" (:description result))))))

(deftest update-card-set-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx [mock-card-set] [] false)
          [_schema resolver] (get cardset/resolvers [:Mutation :updateCardSet])
          result             (resolver ctx {:slug test-set-slug
                                            :input {:name "Updated Set"}} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value))))))

  (testing "updates card set when authenticated"
    (let [ctx                (make-ctx [mock-card-set] [] true)
          [_schema resolver] (get cardset/resolvers [:Mutation :updateCardSet])
          result             (resolver ctx {:slug test-set-slug
                                            :input {:name "Updated Set"}} nil)]
      (is (= test-set-slug (:slug result)))
      (is (= "Updated Set" (:name result))))))

(deftest delete-card-set-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx [mock-card-set] [] false)
          [_schema resolver] (get cardset/resolvers [:Mutation :deleteCardSet])
          result             (resolver ctx {:slug test-set-slug} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))))))

(deftest cards-resolver-test
  (testing "returns cards for a set wrapped in :data"
    (let [ctx    (make-ctx [mock-card-set] [mock-card])
          result (cardset/cards-resolver ctx {} {:slug test-set-slug})]
      (is (map? result))
      (is (= 1 (count (:data result))))
      (is (= "jordan" (:slug (first (:data result)))))))

  (testing "returns empty vector for set with no cards"
    (let [ctx    (make-ctx [mock-card-set] [])
          result (cardset/cards-resolver ctx {} {:slug test-set-slug})]
      (is (= [] (:data result))))))
