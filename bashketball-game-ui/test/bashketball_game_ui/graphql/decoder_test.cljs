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

(t/deftest decode-js-response-preserves-typename
  (t/testing "decode-js-response preserves __typename as :__typename"
    (let [js-obj (clj->js {"__typename" "PlayerCard"
                           "slug" "test-player"
                           "name" "Test Player"})
          result (decoder/decode-js-response js-obj)]
      (t/is (= "PlayerCard" (:__typename result)))
      (t/is (= "test-player" (:slug result))))))

;; =============================================================================
;; Board Position Decoding Tests
;; =============================================================================
;; These tests document the expected behavior for board position keys.
;; Board tiles and occupants use EDN coordinate strings like "[0 1]" as keys
;; which should be decoded to vectors like [0 1].

(t/deftest board-tiles-keys-should-be-vectors
  (t/testing "Board tile keys should be decoded from strings to vectors"
    (let [js-board  (clj->js {"__typename" "Board"
                              "width" 5
                              "height" 14
                              "tiles" {"[0 1]" {"__typename" "Tile" "terrain" "COURT"}
                                       "[2 3]" {"__typename" "Tile" "terrain" "PAINT"}}
                              "occupants" {}})
          result    (decoder/decode-js-response js-board)
          tile-keys (keys (:tiles result))]
      ;; Keys should be vectors, not keywords
      (t/is (every? vector? tile-keys)
            (str "Expected tile keys to be vectors, got: " (pr-str tile-keys)))
      (t/is (contains? (set tile-keys) [0 1])
            "Should contain position [0 1]")
      (t/is (contains? (set tile-keys) [2 3])
            "Should contain position [2 3]"))))

(t/deftest board-occupants-keys-should-be-vectors
  (t/testing "Board occupant keys should be decoded from strings to vectors"
    (let [js-board      (clj->js {"__typename" "Board"
                                  "width" 5
                                  "height" 14
                                  "tiles" {}
                                  "occupants" {"[1 5]" {"__typename" "Occupant"
                                                        "type" "BASKETBALL_PLAYER"
                                                        "id" "player-1"}
                                               "[3 7]" {"__typename" "Occupant"
                                                        "type" "BALL"}}})
          result        (decoder/decode-js-response js-board)
          occupant-keys (keys (:occupants result))]
      ;; Keys should be vectors, not keywords
      (t/is (every? vector? occupant-keys)
            (str "Expected occupant keys to be vectors, got: " (pr-str occupant-keys)))
      (t/is (contains? (set occupant-keys) [1 5])
            "Should contain position [1 5]")
      (t/is (contains? (set occupant-keys) [3 7])
            "Should contain position [3 7]"))))

(t/deftest ball-loose-position-should-be-vector
  (t/testing "BallLoose position should be decoded as a vector"
    (let [js-ball (clj->js {"__typename" "BallLoose"
                            "status" "LOOSE"
                            "position" "[2 6]"})
          result  (decoder/decode-js-response js-ball)]
      (t/is (vector? (:position result))
            (str "Expected position to be a vector, got: " (pr-str (:position result))))
      (t/is (= [2 6] (:position result))))))

(t/deftest ball-in-air-positions-should-be-vectors
  (t/testing "BallInAir origin should be decoded as a vector"
    (let [js-ball (clj->js {"__typename" "BallInAir"
                            "status" "IN_AIR"
                            "origin" "[1 3]"
                            "target" "[4 10]"
                            "actionType" "SHOT"})
          result  (decoder/decode-js-response js-ball)]
      (t/is (vector? (:origin result))
            (str "Expected origin to be a vector, got: " (pr-str (:origin result))))
      (t/is (= [1 3] (:origin result)))
      ;; Target can be a position vector or a player ID string
      (t/is (vector? (:target result))
            (str "Expected target to be a vector, got: " (pr-str (:target result))))
      (t/is (= [4 10] (:target result))))))

(t/deftest basketball-player-position-should-be-vector
  (t/testing "BasketballPlayer position should be decoded as a vector"
    (let [js-player (clj->js {"__typename" "BasketballPlayer"
                              "id" "player-1"
                              "cardSlug" "star-player"
                              "name" "Star Player"
                              "position" "[2 8]"
                              "exhausted" false
                              "stats" {"__typename" "PlayerStats"
                                       "size" "MD"
                                       "speed" 5
                                       "shooting" 7
                                       "passing" 6
                                       "dribbling" 5
                                       "defense" 4}
                              "abilities" []
                              "modifiers" []})
          result    (decoder/decode-js-response js-player)]
      (t/is (vector? (:position result))
            (str "Expected position to be a vector, got: " (pr-str (:position result))))
      (t/is (= [2 8] (:position result))))))
