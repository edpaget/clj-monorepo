(ns bashketball-editor-api.graphql.resolvers.card-test
  (:require
   [bashketball-editor-api.graphql.resolvers.card :as card]
   [bashketball-editor-api.models.protocol :as repo]
   [clojure.test :refer [deftest is testing]]
   [com.walmartlabs.lacinia.resolve :as resolve]))

(def test-set-slug "base-set")
(def test-user-id (random-uuid))

(def mock-player-card
  {:slug "jordan"
   :name "Jordan"
   :set-slug test-set-slug
   :card-type :card-type/PLAYER_CARD
   :deck-size 5
   :sht 5 :pss 3 :def 4 :speed 4
   :size :size/MD
   :abilities ["Clutch" "Fadeaway"]
   :created-at (java.time.Instant/now)
   :updated-at (java.time.Instant/now)})

(def mock-play-card
  {:slug "fast-break"
   :name "Fast Break"
   :set-slug test-set-slug
   :card-type :card-type/PLAY_CARD
   :fate 2
   :play "Score on a fast break"
   :created-at (java.time.Instant/now)
   :updated-at (java.time.Instant/now)})

(def mock-user
  {:id test-user-id
   :name "Test User"
   :email "test@example.com"
   :github-token "test-token"})

(defrecord MockCardRepo [cards]
  repo/Repository
  (find-by [_ criteria]
    (first (filter #(and (= (:slug %) (:slug criteria))
                         (= (:set-slug %) (:set-slug criteria)))
                   @cards)))
  (find-all [_ opts]
    (let [set-slug  (get-in opts [:where :set-slug])
          card-type (get-in opts [:where :card-type])]
      (cond->> @cards
        set-slug (filter #(= (:set-slug %) set-slug))
        card-type (filter #(= (:card-type %) card-type))
        true vec)))
  (create! [_ data]
    (let [card (-> data
                   (assoc :created-at (java.time.Instant/now)
                          :updated-at (java.time.Instant/now)))]
      (swap! cards conj card)
      card))
  (update! [_ criteria data]
    (let [slug     (:slug criteria)
          set-slug (:set-slug criteria)]
      (if-let [existing (first (filter #(and (= (:slug %) slug)
                                             (= (:set-slug %) set-slug))
                                       @cards))]
        (let [updated (-> existing
                          (merge (dissoc data :set-slug :slug))
                          (assoc :updated-at (java.time.Instant/now)))]
          (swap! cards (fn [cs] (mapv #(if (and (= (:slug %) slug)
                                                (= (:set-slug %) set-slug))
                                         updated %) cs)))
          updated)
        (throw (ex-info "Card not found" {:slug slug :set-slug set-slug})))))
  (delete! [_ _] true))

(defrecord MockUserRepo []
  repo/Repository
  (find-by [_ _] mock-user)
  (find-all [_ _] [mock-user])
  (create! [_ _] mock-user)
  (update! [_ _ _] mock-user)
  (delete! [_ _] true))

(defn make-ctx
  ([cards] (make-ctx cards true))
  ([cards authenticated?]
   {:card-repo (->MockCardRepo (atom cards))
    :user-repo (->MockUserRepo)
    :request {:authn/authenticated? authenticated?
              :authn/user-id (str test-user-id)}}))

(deftest card-query-test
  (testing "returns card when found"
    (let [ctx                (make-ctx [mock-player-card])
          [_schema resolver] (get card/resolvers [:Query :card])
          result             (resolver ctx {:slug "jordan" :setSlug test-set-slug} nil)]
      (is (= "jordan" (:slug result)))
      (is (= "Jordan" (:name result)))))

  (testing "returns nil when card not found"
    (let [ctx                (make-ctx [])
          [_schema resolver] (get card/resolvers [:Query :card])
          result             (resolver ctx {:slug "nonexistent" :setSlug test-set-slug} nil)]
      (is (nil? result)))))

(deftest cards-query-test
  (testing "returns all cards for a set wrapped in :data"
    (let [ctx                (make-ctx [mock-player-card mock-play-card])
          [_schema resolver] (get card/resolvers [:Query :cards])
          result             (resolver ctx {:setSlug test-set-slug} nil)]
      (is (map? result))
      (is (= 2 (count (:data result))))))

  (testing "filters by card type"
    (let [ctx                (make-ctx [mock-player-card mock-play-card])
          [_schema resolver] (get card/resolvers [:Query :cards])
          result             (resolver ctx {:setSlug test-set-slug :cardType "PLAYER_CARD"} nil)]
      (is (= 1 (count (:data result))))
      (is (= "jordan" (:slug (first (:data result)))))))

  (testing "returns empty vector for unknown set"
    (let [ctx                (make-ctx [mock-player-card])
          [_schema resolver] (get card/resolvers [:Query :cards])
          result             (resolver ctx {:setSlug "nonexistent-set"} nil)]
      (is (= [] (:data result))))))

(deftest create-player-card-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx [] false)
          [_schema resolver] (get card/resolvers [:Mutation :createPlayerCard])
          result             (resolver ctx {:setSlug test-set-slug
                                            :input {:slug "new-player" :name "New Player"}} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value))))))

  (testing "creates player card when authenticated"
    (let [ctx                (make-ctx [] true)
          [_schema resolver] (get card/resolvers [:Mutation :createPlayerCard])
          result             (resolver ctx {:setSlug test-set-slug
                                            :input {:slug "new-player" :name "New Player"}} nil)]
      (is (= "new-player" (:slug result)))
      (is (= "New Player" (:name result)))
      (is (= :card-type/PLAYER_CARD (:cardType result))))))

(deftest update-card-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx [mock-player-card] false)
          [_schema resolver] (get card/resolvers [:Mutation :updateCard])
          result             (resolver ctx {:slug "jordan"
                                            :setSlug test-set-slug
                                            :input {:name "Updated Jordan"}} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value))))))

  (testing "updates card when authenticated"
    (let [ctx                (make-ctx [mock-player-card] true)
          [_schema resolver] (get card/resolvers [:Mutation :updateCard])
          result             (resolver ctx {:slug "jordan"
                                            :setSlug test-set-slug
                                            :input {:name "Updated Jordan"}} nil)]
      (is (= "jordan" (:slug result)))
      (is (= "Updated Jordan" (:name result))))))

(deftest delete-card-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx [mock-player-card] false)
          [_schema resolver] (get card/resolvers [:Mutation :deleteCard])
          result             (resolver ctx {:slug "jordan" :setSlug test-set-slug} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))))))
