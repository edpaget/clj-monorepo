(ns bashketball-game-ui.graphql.decoder-test
  "Tests for the GraphQL response decoder."
  (:require
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-schemas.card :as card]
   [bashketball-schemas.enums :as enums]
   [cljs.test :as t :include-macros true]))

;; Test schema - simple multi schema for testing dispatch
(def TestMulti
  "Multi schema that dispatches on :card-type keyword."
  [:multi {:dispatch :card-type}
   [:card-type/PLAYER_CARD [:map
                            [:card-type enums/CardType]
                            [:name :string]
                            [:sht :int]]]
   [:card-type/PLAY_CARD [:map
                          [:card-type enums/CardType]
                          [:name :string]
                          [:fate :int]]]])

(t/deftest js->clj-kebab-converts-keys
  (t/testing "converts camelCase JS object keys to kebab-case keywords"
    (let [js-obj #js {:cardType "PLAYER_CARD"
                      :setSlug "core-set"
                      :imagePrompt "A tall player"}
          result (decoder/decode [:map
                                  [:card-type :string]
                                  [:set-slug :string]
                                  [:image-prompt :string]]
                                 js-obj)]
      (t/is (= "PLAYER_CARD" (:card-type result)))
      (t/is (= "core-set" (:set-slug result)))
      (t/is (= "A tall player" (:image-prompt result))))))

(t/deftest js->clj-kebab-handles-nested-objects
  (t/testing "recursively converts nested JS objects"
    (let [js-obj #js {:outerKey #js {:innerKey "value"
                                     :deepNested #js {:finalKey "deep"}}}
          result (decoder/decode [:map
                                  [:outer-key [:map
                                               [:inner-key :string]
                                               [:deep-nested [:map
                                                              [:final-key :string]]]]]]
                                 js-obj)]
      (t/is (= "value" (get-in result [:outer-key :inner-key])))
      (t/is (= "deep" (get-in result [:outer-key :deep-nested :final-key]))))))

(t/deftest js->clj-kebab-handles-arrays
  (t/testing "converts JS arrays to vectors with transformed objects"
    (let [js-obj #js {:items #js [#js {:itemName "first"}
                                  #js {:itemName "second"}]}
          result (decoder/decode [:map
                                  [:items [:vector [:map [:item-name :string]]]]]
                                 js-obj)]
      (t/is (vector? (:items result)))
      (t/is (= "first" (get-in result [:items 0 :item-name])))
      (t/is (= "second" (get-in result [:items 1 :item-name]))))))

(t/deftest enum-transformer-decodes-strings
  (t/testing "converts string enum values to namespaced keywords"
    (let [js-obj #js {:cardType "PLAYER_CARD"}
          result (decoder/decode [:map [:card-type enums/CardType]] js-obj)]
      (t/is (= :card-type/PLAYER_CARD (:card-type result))))))

(t/deftest enum-transformer-handles-all-card-types
  (t/testing "decodes all CardType enum values"
    (doseq [card-type [:card-type/PLAYER_CARD
                       :card-type/ABILITY_CARD
                       :card-type/PLAY_CARD
                       :card-type/STANDARD_ACTION_CARD
                       :card-type/SPLIT_PLAY_CARD
                       :card-type/COACHING_CARD
                       :card-type/TEAM_ASSET_CARD]]
      (let [type-str (name card-type)
            js-obj   #js {:cardType type-str}
            result   (decoder/decode [:map [:card-type enums/CardType]] js-obj)]
        (t/is (= card-type (:card-type result))
              (str "Failed to decode " type-str))))))

(t/deftest multi-schema-dispatch-works
  (t/testing "multi schema dispatches correctly after key transformation"
    (let [js-player #js {:cardType "PLAYER_CARD"
                         :name "Star Player"
                         :sht 8}
          result    (decoder/decode TestMulti js-player)]
      (t/is (= :card-type/PLAYER_CARD (:card-type result)))
      (t/is (= "Star Player" (:name result)))
      (t/is (= 8 (:sht result))))))

(t/deftest multi-schema-dispatch-play-card
  (t/testing "multi schema dispatches to play card branch"
    (let [js-play #js {:cardType "PLAY_CARD"
                       :name "Fast Break"
                       :fate 3}
          result  (decoder/decode TestMulti js-play)]
      (t/is (= :card-type/PLAY_CARD (:card-type result)))
      (t/is (= "Fast Break" (:name result)))
      (t/is (= 3 (:fate result))))))

(t/deftest full-card-schema-decode-player
  (t/testing "decodes a full PlayerCard from JS object"
    (let [js-card #js {:slug "star-player"
                       :name "Star Player"
                       :setSlug "core-set"
                       :cardType "PLAYER_CARD"
                       :sht 8
                       :pss 7
                       :def 6
                       :speed 9
                       :size "MD"
                       :abilities #js ["Quick" "Shooter"]}
          result  (decoder/decode card/Card js-card)]
      (t/is (= "star-player" (:slug result)))
      (t/is (= "Star Player" (:name result)))
      (t/is (= "core-set" (:set-slug result)))
      (t/is (= :card-type/PLAYER_CARD (:card-type result)))
      (t/is (= 8 (:sht result)))
      (t/is (= :size/MD (:size result)))
      (t/is (= ["Quick" "Shooter"] (:abilities result))))))

(t/deftest full-card-schema-decode-coaching
  (t/testing "decodes a CoachingCard from JS object"
    (let [js-card #js {:slug "timeout"
                       :name "Timeout"
                       :setSlug "core-set"
                       :cardType "COACHING_CARD"
                       :fate 2
                       :coaching "Rest your players"}
          result  (decoder/decode card/Card js-card)]
      (t/is (= "timeout" (:slug result)))
      (t/is (= :card-type/COACHING_CARD (:card-type result)))
      (t/is (= 2 (:fate result)))
      (t/is (= "Rest your players" (:coaching result))))))

(t/deftest decode-seq-handles-arrays
  (t/testing "decode-seq decodes multiple cards"
    (let [js-cards #js [#js {:slug "card-1"
                             :name "Card 1"
                             :setSlug "set-1"
                             :cardType "PLAY_CARD"
                             :fate 1
                             :play "Do something"}
                        #js {:slug "card-2"
                             :name "Card 2"
                             :setSlug "set-1"
                             :cardType "PLAY_CARD"
                             :fate 2
                             :play "Do another thing"}]
          results  (decoder/decode-seq card/Card js-cards)]
      (t/is (= 2 (count results)))
      (t/is (= "card-1" (:slug (first results))))
      (t/is (= "card-2" (:slug (second results))))
      (t/is (every? #(= :card-type/PLAY_CARD (:card-type %)) results)))))

(t/deftest handles-nil-values
  (t/testing "passes through nil values"
    (let [js-obj #js {:name "Test"
                      :imagePrompt nil}
          result (decoder/decode [:map
                                  [:name :string]
                                  [:image-prompt {:optional true} [:maybe :string]]]
                                 js-obj)]
      (t/is (= "Test" (:name result)))
      (t/is (nil? (:image-prompt result))))))

(t/deftest handles-already-clojure-data
  (t/testing "handles Clojure maps passed directly"
    (let [clj-data {:card-type "PLAYER_CARD"
                    :name "Already Clojure"}
          result   (decoder/decode [:map
                                    [:card-type enums/CardType]
                                    [:name :string]]
                                   clj-data)]
      (t/is (= :card-type/PLAYER_CARD (:card-type result)))
      (t/is (= "Already Clojure" (:name result))))))
