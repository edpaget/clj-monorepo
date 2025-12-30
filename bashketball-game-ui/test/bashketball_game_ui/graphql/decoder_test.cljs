(ns bashketball-game-ui.graphql.decoder-test
  "Tests for the GraphQL response decoder."
  (:require
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.registry :as registry]
   [bashketball-schemas.card :as card]
   [bashketball-schemas.effect :as effect]
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
                       :abilities #js [#js {:name "Quick"
                                            :description "Fast movement"}
                                       #js {:name "Shooter"
                                            :description "Accurate shots"}]}
          result  (decoder/decode card/Card js-card)]
      (t/is (= "star-player" (:slug result)))
      (t/is (= "Star Player" (:name result)))
      (t/is (= "core-set" (:set-slug result)))
      (t/is (= :card-type/PLAYER_CARD (:card-type result)))
      (t/is (= 8 (:sht result)))
      (t/is (= :size/MD (:size result)))
      (t/is (= "Quick" (get-in result [:abilities 0 :name]))))))

(t/deftest full-card-schema-decode-coaching
  (t/testing "decodes a CoachingCard from JS object"
    (let [js-card #js {:slug "timeout"
                       :name "Timeout"
                       :setSlug "core-set"
                       :cardType "COACHING_CARD"
                       :fate 2
                       :call #js {:name "Rest your players"
                                  :description "Remove exhaustion from all players"}
                       :signal #js {:name "Quick rest"
                                    :description "Remove exhaustion from one player"}}
          result  (decoder/decode card/Card js-card)]
      (t/is (= "timeout" (:slug result)))
      (t/is (= :card-type/COACHING_CARD (:card-type result)))
      (t/is (= 2 (:fate result)))
      (t/is (= "Rest your players" (get-in result [:call :name])))
      (t/is (= "Quick rest" (get-in result [:signal :name]))))))

(t/deftest decode-seq-handles-arrays
  (t/testing "decode-seq decodes multiple cards"
    (let [js-cards #js [#js {:slug "card-1"
                             :name "Card 1"
                             :setSlug "set-1"
                             :cardType "PLAY_CARD"
                             :fate 1
                             :play #js {:name "Do something"
                                        :description "Perform an action"}}
                        #js {:slug "card-2"
                             :name "Card 2"
                             :setSlug "set-1"
                             :cardType "PLAY_CARD"
                             :fate 2
                             :play #js {:name "Do another thing"
                                        :description "Perform another action"}}]
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

(t/deftest decode-js-response-decodes-ability-card-type
  (t/testing "decode-js-response decodes AbilityCard card-type enum to namespaced keyword"
    (let [js-obj (clj->js {"__typename" "AbilityCard"
                           "slug" "power-shot"
                           "name" "Power Shot"
                           "setSlug" "core-set"
                           "cardType" "ABILITY_CARD"
                           "fate" 2
                           "abilities" []})
          result (decoder/decode-js-response js-obj)]
      (t/is (= "AbilityCard" (:__typename result)))
      (t/is (= "power-shot" (:slug result)))
      (t/is (= :card-type/ABILITY_CARD (:card-type result))
            "card-type should be decoded to namespaced keyword"))))

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
  (t/testing "BallInAir origin should be decoded with position target"
    (let [js-ball (clj->js {"__typename" "BallInAir"
                            "status" "IN_AIR"
                            "origin" "[1 3]"
                            "target" {"__typename" "PositionTarget"
                                      "position" "[4 10]"}
                            "actionType" "SHOT"})
          result  (decoder/decode-js-response js-ball)]
      (t/is (vector? (:origin result))
            (str "Expected origin to be a vector, got: " (pr-str (:origin result))))
      (t/is (= [1 3] (:origin result)))
      (t/is (= [4 10] (get-in result [:target :position])))
      (t/is (= "PositionTarget" (get-in result [:target :__typename])))))

  (t/testing "BallInAir should decode player target"
    (let [js-ball (clj->js {"__typename" "BallInAir"
                            "status" "IN_AIR"
                            "origin" "[1 3]"
                            "target" {"__typename" "PlayerTarget"
                                      "playerId" "HOME-1"}
                            "actionType" "PASS"})
          result  (decoder/decode-js-response js-ball)]
      (t/is (= [1 3] (:origin result)))
      (t/is (= "HOME-1" (get-in result [:target :player-id])))
      (t/is (= "PlayerTarget" (get-in result [:target :__typename]))))))

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
                                       "defense" 4}
                              "abilities" []
                              "modifiers" []})
          result    (decoder/decode-js-response js-player)]
      (t/is (vector? (:position result))
            (str "Expected position to be a vector, got: " (pr-str (:position result))))
      (t/is (= [2 8] (:position result))))))

;; =============================================================================
;; GraphQL Name Override Tests
;; =============================================================================
;; These tests verify that :graphql/name overrides are reversed when decoding.
;; Keys like "HOME" should become :team/HOME based on __typename dispatch.

(t/deftest graphql-name-override-with-players
  (t/testing "decodes Players HOME/AWAY keys to :team/HOME/:team/AWAY"
    (let [js-data (clj->js {"__typename" "Players"
                            "HOME" {"id" "home-player"}
                            "AWAY" {"id" "away-player"}})
          result  (decoder/decode-js-response js-data)]
      (t/is (contains? result :team/HOME)
            "Should have :team/HOME key")
      (t/is (contains? result :team/AWAY)
            "Should have :team/AWAY key")
      ;; Nested objects without __typename have string keys converted to kebab-case
      (t/is (some? (:team/HOME result))
            "HOME value should not be nil")
      (t/is (some? (:team/AWAY result))
            "AWAY value should not be nil"))))

(t/deftest graphql-name-override-with-score
  (t/testing "decodes Score with primitive values"
    (let [js-data (clj->js {"__typename" "Score"
                            "HOME" 42
                            "AWAY" 38})
          result  (decoder/decode-js-response js-data)]
      (t/is (= 42 (:team/HOME result))
            "HOME should be decoded to :team/HOME")
      (t/is (= 38 (:team/AWAY result))
            "AWAY should be decoded to :team/AWAY"))))

(t/deftest graphql-name-works-with-nested-players
  (t/testing "decode-js-response correctly decodes Players with nested data"
    (let [js-players (clj->js {"__typename" "Players"
                               "HOME" {"__typename" "GamePlayer"
                                       "id" "HOME"
                                       "actionsRemaining" 3
                                       "deck" {"__typename" "DeckState"
                                               "drawPile" []
                                               "hand" []
                                               "discard" []
                                               "removed" []}
                                       "team" {"__typename" "TeamRoster"
                                               "starters" []
                                               "bench" []
                                               "players" {}}
                                       "assets" []}
                               "AWAY" {"__typename" "GamePlayer"
                                       "id" "AWAY"
                                       "actionsRemaining" 3
                                       "deck" {"__typename" "DeckState"
                                               "drawPile" []
                                               "hand" []
                                               "discard" []
                                               "removed" []}
                                       "team" {"__typename" "TeamRoster"
                                               "starters" []
                                               "bench" []
                                               "players" {}}
                                       "assets" []}})
          result     (decoder/decode-js-response js-players)]
      (t/is (contains? result :team/HOME)
            "Should have :team/HOME key")
      (t/is (contains? result :team/AWAY)
            "Should have :team/AWAY key")
      (t/is (= 3 (get-in result [:team/HOME :actions-remaining]))
            "Nested data should be decoded correctly"))))

;; =============================================================================
;; Effect Schema Decoding Tests
;; =============================================================================
;; These tests verify that effect schemas are properly registered and basic
;; decoding works. PolicyExpr scalar decoding is tested separately in
;; transformer_test.cljs.
;;
;; NOTE: The effect schemas use namespaced keys (e.g., :trigger/condition)
;; but GraphQL wire format uses simple keys (e.g., condition). The schema-
;; driven PolicyExpr decoding only works when keys match. For full PolicyExpr
;; decoding in production, the API should include __typename on nested objects
;; so the decoder can apply the correct schema.

(t/deftest effect-types-registered-in-typename-registry
  (t/testing "All effect schemas are registered with __typename"
    (let [type-registry registry/typename->schema]
      (t/is (contains? type-registry "TriggerDef"))
      (t/is (contains? type-registry "EffectDef"))
      (t/is (contains? type-registry "AbilityDef"))
      (t/is (contains? type-registry "PlayDef"))
      (t/is (contains? type-registry "ActionModeDef"))
      (t/is (contains? type-registry "CallDef"))
      (t/is (contains? type-registry "SignalDef"))
      (t/is (contains? type-registry "AssetTriggerDef"))
      (t/is (contains? type-registry "ActivatedAbilityDef"))
      (t/is (contains? type-registry "ResponseDef"))
      (t/is (contains? type-registry "AssetPowerDef")))))

(t/deftest trigger-def-decodes-basic-fields
  (t/testing "TriggerDef decodes with basic fields"
    ;; NOTE: Enum decoding doesn't apply because TriggerDef schema uses
    ;; namespaced keys (:trigger/timing) but GraphQL returns simple keys (timing).
    ;; Fields are decoded to kebab-case keywords but enum values remain strings.
    (let [js-trigger (clj->js {"__typename" "TriggerDef"
                                "event" "bashketball/shoot.after"
                                "timing" "AFTER"
                                "priority" 10})
          result     (decoder/decode-js-response js-trigger)]
      (t/is (= "TriggerDef" (:__typename result)))
      (t/is (= "bashketball/shoot.after" (:event result)))
      (t/is (= "AFTER" (:timing result)))
      (t/is (= 10 (:priority result))))))

(t/deftest ability-def-decodes-basic-fields
  (t/testing "AbilityDef decodes with basic fields and nested types"
    (let [js-ability (clj->js {"__typename" "AbilityDef"
                                "id" "clutch-performer"
                                "name" "Clutch Performer"
                                "description" "Performs better under pressure"
                                "trigger" {"__typename" "TriggerDef"
                                           "event" "bashketball/skill-test.before"}
                                "effect" {"__typename" "EffectDef"
                                          "type" "bashketball/add-modifier"}})
          result     (decoder/decode-js-response js-ability)]
      (t/is (= "AbilityDef" (:__typename result)))
      (t/is (= "clutch-performer" (:id result)))
      (t/is (= "TriggerDef" (get-in result [:trigger :__typename])))
      (t/is (= "EffectDef" (get-in result [:effect :__typename]))))))

(t/deftest action-mode-def-decodes-basic-fields
  (t/testing "ActionModeDef decodes with basic fields"
    (let [js-action (clj->js {"__typename" "ActionModeDef"
                               "id" "power-shot"
                               "name" "Power Shot"
                               "effect" {"__typename" "EffectDef"
                                         "type" "bashketball/shoot"}})
          result    (decoder/decode-js-response js-action)]
      (t/is (= "ActionModeDef" (:__typename result)))
      (t/is (= "power-shot" (:id result)))
      (t/is (= "EffectDef" (get-in result [:effect :__typename]))))))

(t/deftest player-card-with-abilities-decodes
  (t/testing "PlayerCard with structured abilities decodes correctly"
    (let [js-card (clj->js {"__typename" "PlayerCard"
                             "slug" "star-player"
                             "name" "Star Player"
                             "setSlug" "core-set"
                             "cardType" "PLAYER_CARD"
                             "sht" 8
                             "pss" 7
                             "def" 6
                             "speed" 9
                             "size" "MD"
                             "playerSubtypes" ["HUMAN"]
                             "abilities" [{"__typename" "AbilityDef"
                                           "id" "clutch"}
                                          {"__typename" "AbilityDef"
                                           "id" "fadeaway"
                                           "trigger" {"__typename" "TriggerDef"
                                                      "event" "bashketball/shoot.before"}}]})
          result  (decoder/decode-js-response js-card)]
      (t/is (= "PlayerCard" (:__typename result)))
      (t/is (= "star-player" (:slug result)))
      (t/is (= :card-type/PLAYER_CARD (:card-type result)))
      (t/is (= :size/MD (:size result)))
      (t/is (= 2 (count (:abilities result))))
      (t/is (= "clutch" (get-in result [:abilities 0 :id])))
      (t/is (= "AbilityDef" (get-in result [:abilities 0 :__typename]))))))

(t/deftest standard-action-card-with-modes-decodes
  (t/testing "StandardActionCard with offense/defense modes decodes correctly"
    (let [js-card (clj->js {"__typename" "StandardActionCard"
                             "slug" "power-move"
                             "name" "Power Move"
                             "setSlug" "core-set"
                             "cardType" "STANDARD_ACTION_CARD"
                             "fate" 4
                             "offense" {"__typename" "ActionModeDef"
                                        "id" "power-move-offense"
                                        "effect" {"__typename" "EffectDef"
                                                  "type" "bashketball/move"}}
                             "defense" {"__typename" "ActionModeDef"
                                        "id" "power-move-defense"
                                        "effect" {"__typename" "EffectDef"
                                                  "type" "bashketball/defend"}}})
          result  (decoder/decode-js-response js-card)]
      (t/is (= "StandardActionCard" (:__typename result)))
      (t/is (= :card-type/STANDARD_ACTION_CARD (:card-type result)))
      (t/is (= "ActionModeDef" (get-in result [:offense :__typename])))
      (t/is (= "power-move-offense" (get-in result [:offense :id]))))))
