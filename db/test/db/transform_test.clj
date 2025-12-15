(ns db.transform-test
  "Tests for db.transform Malli-based transformation utilities."
  (:require
   [clojure.test :refer [deftest is testing]]
   [db.transform :as transform]))

(deftest keywordize-keys-test
  (testing "converts string keys to keywords"
    (is (= {:foo "bar" :nested {:baz 1}}
           (transform/keywordize-keys {"foo" "bar" "nested" {"baz" 1}}))))

  (testing "handles vectors of maps"
    (is (= [{:a 1} {:b 2}]
           (transform/keywordize-keys [{"a" 1} {"b" 2}]))))

  (testing "handles deeply nested structures"
    (is (= {:level1 {:level2 {:level3 "value"}}}
           (transform/keywordize-keys {"level1" {"level2" {"level3" "value"}}}))))

  (testing "passes through primitives"
    (is (= "hello" (transform/keywordize-keys "hello")))
    (is (= 42 (transform/keywordize-keys 42)))
    (is (= true (transform/keywordize-keys true))))

  (testing "handles nil"
    (is (nil? (transform/keywordize-keys nil))))

  (testing "handles empty structures"
    (is (= {} (transform/keywordize-keys {})))
    (is (= [] (transform/keywordize-keys [])))))

(def SimpleSchema
  [:map
   [:player-id :string]
   [:score :int]
   [:active :boolean]])

(def NestedSchema
  [:map
   [:player-id :string]
   [:score :int]
   [:nested [:map
             [:inner-value :string]
             [:inner-count :int]]]])

(deftest decode-simple-schema-test
  (testing "decodes string keys to kebab-case keywords"
    (is (= {:player-id "abc" :score 10 :active true}
           (transform/decode
            {"player_id" "abc" "score" 10 "active" true}
            SimpleSchema))))

  (testing "handles nil gracefully"
    (is (nil? (transform/decode nil SimpleSchema)))))

(deftest decode-nested-schema-test
  (testing "decodes nested structures"
    (is (= {:player-id "abc" :score 10 :nested {:inner-value "x" :inner-count 5}}
           (transform/decode
            {"player_id" "abc" "score" 10 "nested" {"inner_value" "x" "inner_count" 5}}
            NestedSchema)))))

(deftest encode-simple-schema-test
  (testing "encodes kebab-case keywords to snake_case strings"
    (is (= {"player_id" "abc" "score" 10 "active" true}
           (transform/encode
            {:player-id "abc" :score 10 :active true}
            SimpleSchema))))

  (testing "handles nil gracefully"
    (is (nil? (transform/encode nil SimpleSchema)))))

(deftest encode-nested-schema-test
  (testing "encodes nested structures"
    (is (= {"player_id" "abc" "score" 10 "nested" {"inner_value" "x" "inner_count" 5}}
           (transform/encode
            {:player-id "abc" :score 10 :nested {:inner-value "x" :inner-count 5}}
            NestedSchema)))))

(deftest roundtrip-test
  (testing "data survives encode->decode roundtrip"
    (let [original {:player-id "abc" :score 10 :nested {:inner-value "x" :inner-count 5}}
          encoded  (transform/encode original NestedSchema)
          decoded  (transform/decode encoded NestedSchema)]
      (is (= original decoded)))))

(def SchemaWithVector
  [:map
   [:items [:vector [:map [:name :string] [:value :int]]]]])

(deftest decode-vector-of-maps-test
  (testing "decodes vectors of maps"
    (is (= {:items [{:name "a" :value 1} {:name "b" :value 2}]}
           (transform/decode
            {"items" [{"name" "a" "value" 1} {"name" "b" "value" 2}]}
            SchemaWithVector)))))

(def SchemaWithMapOf
  [:map
   [:data [:map-of :string [:map [:count :int]]]]])

(deftest decode-map-of-test
  (testing "decodes map-of with string keys - keys remain strings"
    (let [result (transform/decode
                  {"data" {"key1" {"count" 1} "key2" {"count" 2}}}
                  SchemaWithMapOf)]
      (is (map? (:data result)))
      ;; map-of keys are data values, not field names, so they stay as strings
      (is (= 1 (get-in result [:data "key1" :count])))
      (is (= 2 (get-in result [:data "key2" :count]))))))

;; Test schemas for enum and literal keyword decoding
(def EnumSchema
  [:map
   [:status [:enum :ACTIVE :INACTIVE :PENDING]]])

(def LiteralKeywordSchema
  [:map
   [:status [:= :POSSESSED]]])

(def BallPossessedSchema
  [:map
   [:status [:= :POSSESSED]]
   [:holder-id :string]])

(def BallLooseSchema
  [:map
   [:status [:= :LOOSE]]
   [:position [:vector :int]]])

(def BallSchema
  "Multi schema with plain keyword dispatch - db-dispatch is applied automatically."
  [:multi {:dispatch :status}
   [:POSSESSED BallPossessedSchema]
   [:LOOSE BallLooseSchema]])

(deftest decode-enum-test
  (testing "decodes string to keyword for enum schema"
    (is (= {:status :ACTIVE}
           (transform/decode {"status" "ACTIVE"} EnumSchema)))
    (is (= {:status :PENDING}
           (transform/decode {"status" "PENDING"} EnumSchema))))

  (testing "preserves keyword enum values"
    (is (= {:status :ACTIVE}
           (transform/decode {"status" :ACTIVE} EnumSchema)))))

(deftest decode-literal-keyword-test
  (testing "decodes string to keyword for [:= :KEYWORD] schema"
    (is (= {:status :POSSESSED}
           (transform/decode {"status" "POSSESSED"} LiteralKeywordSchema))))

  (testing "preserves keyword literal values"
    (is (= {:status :POSSESSED}
           (transform/decode {"status" :POSSESSED} LiteralKeywordSchema)))))

(deftest decode-multi-schema-with-literal-dispatch-test
  (testing "decodes multi schema with uppercase literal dispatch values"
    (let [result (transform/decode
                  {"status" "POSSESSED" "holder_id" "player-1"}
                  BallSchema)]
      (is (= :POSSESSED (:status result)))
      (is (= "player-1" (:holder-id result)))))

  (testing "decodes loose ball variant"
    (let [result (transform/decode
                  {"status" "LOOSE" "position" [1 2]}
                  BallSchema)]
      (is (= :LOOSE (:status result)))
      (is (= [1 2] (:position result))))))

;; =============================================================================
;; Map-of with tuple keys tests
;; =============================================================================

(def OccupantType
  [:enum :PLAYER :BALL :BASKETBALL_PLAYER])

(def MapOfTupleKeySchema
  "Schema with map-of using [:tuple :int :int] keys (like board occupants)."
  [:map
   [:occupants [:map-of [:tuple :int :int] [:map [:type OccupantType] [:id :string]]]]])

(deftest decode-map-of-tuple-keys-test
  (testing "decodes stringified tuple keys to vectors"
    (let [result (transform/decode
                  {"occupants" {"[2 3]" {"type" "PLAYER" "id" "p1"}}}
                  MapOfTupleKeySchema)]
      (is (= [2 3] (first (keys (:occupants result)))))
      (is (vector? (first (keys (:occupants result)))))
      (is (= {:type :PLAYER :id "p1"} (get (:occupants result) [2 3])))))

  (testing "decodes tuple keys with comma separator"
    (let [result (transform/decode
                  {"occupants" {"[2, 3]" {"type" "PLAYER" "id" "p1"}}}
                  MapOfTupleKeySchema)]
      (is (= [2 3] (first (keys (:occupants result)))))))

  (testing "decodes multiple tuple keys"
    (let [result (transform/decode
                  {"occupants" {"[2 3]" {"type" "PLAYER" "id" "p1"}
                                "[4 5]" {"type" "BALL" "id" "b1"}}}
                  MapOfTupleKeySchema)]
      (is (= 2 (count (:occupants result))))
      (is (contains? (:occupants result) [2 3]))
      (is (contains? (:occupants result) [4 5]))))

  (testing "handles negative coordinates"
    (let [result (transform/decode
                  {"occupants" {"[-1 3]" {"type" "PLAYER" "id" "p1"}}}
                  MapOfTupleKeySchema)]
      (is (= [-1 3] (first (keys (:occupants result)))))))

  (testing "handles empty occupants map"
    (let [result (transform/decode
                  {"occupants" {}}
                  MapOfTupleKeySchema)]
      (is (= {} (:occupants result))))))

(def TerrainType
  [:enum :COURT :HOOP :PAINT :THREE_POINT_LINE])

(def BoardSchema
  "Schema matching the game board structure."
  [:map
   [:width :int]
   [:height :int]
   [:tiles [:map-of [:tuple :int :int] [:map [:terrain TerrainType]]]]
   [:occupants [:map-of [:tuple :int :int] [:map [:type OccupantType] [:id {:optional true} :string]]]]])

(deftest decode-board-schema-test
  (testing "decodes full board with tiles and occupants"
    (let [result (transform/decode
                  {"width" 5
                   "height" 14
                   "tiles" {"[0 0]" {"terrain" "COURT"}
                            "[2 0]" {"terrain" "HOOP"}}
                   "occupants" {"[2 3]" {"type" "BASKETBALL_PLAYER" "id" "player-1"}}}
                  BoardSchema)]
      (is (= 5 (:width result)))
      (is (= 14 (:height result)))
      (is (= :COURT (:terrain (get (:tiles result) [0 0]))))
      (is (= :HOOP (:terrain (get (:tiles result) [2 0]))))
      (is (= :BASKETBALL_PLAYER (:type (get (:occupants result) [2 3]))))
      (is (= "player-1" (:id (get (:occupants result) [2 3])))))))

(def GameStateWithBoardSchema
  "Simplified game state schema for testing board occupants."
  [:map
   [:board BoardSchema]
   [:players [:map
              [:HOME [:map
                      [:team [:map
                              [:players [:map-of :string [:map
                                                          [:id :string]
                                                          [:position {:optional true} [:tuple :int :int]]]]]]]]]]]])

(deftest decode-game-state-move-scenario-test
  (testing "player position and occupant keys are both vectors after decode"
    (let [result          (transform/decode
                           {"board" {"width" 5
                                     "height" 14
                                     "tiles" {}
                                     "occupants" {"[2 3]" {"type" "BASKETBALL_PLAYER" "id" "player-1"}}}
                            "players" {"HOME" {"team" {"players" {"player-1" {"id" "player-1"
                                                                              "position" [2 3]}}}}}}
                           GameStateWithBoardSchema)
          occupant-key    (first (keys (get-in result [:board :occupants])))
          player-position (get-in result [:players :HOME :team :players "player-1" :position])]
      (is (vector? occupant-key))
      (is (vector? player-position))
      (is (= occupant-key player-position))
      (is (= [2 3] occupant-key))
      (is (= [2 3] player-position))))

  (testing "dissoc works correctly after decode"
    (let [result                 (transform/decode
                                  {"board" {"width" 5
                                            "height" 14
                                            "tiles" {}
                                            "occupants" {"[2 3]" {"type" "BASKETBALL_PLAYER" "id" "player-1"}}}
                                   "players" {"HOME" {"team" {"players" {"player-1" {"id" "player-1"
                                                                                     "position" [2 3]}}}}}}
                                  GameStateWithBoardSchema)
          player-position        (get-in result [:players :HOME :team :players "player-1" :position])
          occupants-after-dissoc (dissoc (get-in result [:board :occupants]) player-position)]
      (is (empty? occupants-after-dissoc)))))

;; =============================================================================
;; Namespaced keyword tests (for game enum migration)
;; =============================================================================

(def NamespacedTeamEnum
  "Team enum with namespaced keywords (matching game schema)."
  [:enum :team/HOME :team/AWAY])

(def NamespacedBallStatusEnum
  "Ball status enum with namespaced keywords."
  [:enum :ball-status/POSSESSED :ball-status/LOOSE :ball-status/IN_AIR])

(def NamespacedPhaseEnum
  "Game phase enum with namespaced keywords."
  [:enum :phase/SETUP :phase/TIP_OFF :phase/ACTIONS :phase/GAME_OVER])

(def ScoreSchema
  "Score map with namespaced team keys."
  [:map
   [:team/HOME :int]
   [:team/AWAY :int]])

(def GameStateWithNamespacedEnumsSchema
  "Simplified game state schema with namespaced enums."
  [:map
   [:active-player NamespacedTeamEnum]
   [:phase NamespacedPhaseEnum]
   [:score ScoreSchema]])

(def NamespacedBallPossessedSchema
  [:map
   [:status [:= :ball-status/POSSESSED]]
   [:holder-id :string]])

(def NamespacedBallLooseSchema
  [:map
   [:status [:= :ball-status/LOOSE]]
   [:position [:vector :int]]])

(def NamespacedBallSchema
  "Multi schema with namespaced dispatch values."
  [:multi {:dispatch :status}
   [:ball-status/POSSESSED NamespacedBallPossessedSchema]
   [:ball-status/LOOSE NamespacedBallLooseSchema]])

;; -----------------------------------------------------------------------------
;; Decoding tests for namespaced keywords
;; -----------------------------------------------------------------------------

(deftest decode-namespaced-enum-value-test
  (testing "decodes namespaced enum value from string"
    (let [schema [:map [:active-player NamespacedTeamEnum]]
          result (transform/decode {"active_player" "team/HOME"} schema)]
      (is (= :team/HOME (:active-player result)))))

  (testing "decodes multiple namespaced enums"
    (let [result (transform/decode
                  {"active_player" "team/AWAY"
                   "phase" "phase/ACTIONS"
                   "score" {"team/HOME" 10 "team/AWAY" 8}}
                  GameStateWithNamespacedEnumsSchema)]
      (is (= :team/AWAY (:active-player result)))
      (is (= :phase/ACTIONS (:phase result)))
      (is (= 10 (:team/HOME (:score result))))
      (is (= 8 (:team/AWAY (:score result)))))))

(deftest decode-namespaced-map-keys-test
  (testing "decodes namespaced uppercase map keys"
    (let [result (transform/decode
                  {"team/HOME" 10 "team/AWAY" 5}
                  ScoreSchema)]
      (is (= {:team/HOME 10 :team/AWAY 5} result))
      (is (contains? result :team/HOME))
      (is (contains? result :team/AWAY)))))

(deftest decode-namespaced-multi-schema-test
  (testing "decodes multi schema with namespaced dispatch value"
    (let [result (transform/decode
                  {"status" "ball-status/POSSESSED" "holder_id" "player-1"}
                  NamespacedBallSchema)]
      (is (= :ball-status/POSSESSED (:status result)))
      (is (= "player-1" (:holder-id result)))))

  (testing "decodes loose ball variant"
    (let [result (transform/decode
                  {"status" "ball-status/LOOSE" "position" [1 2]}
                  NamespacedBallSchema)]
      (is (= :ball-status/LOOSE (:status result)))
      (is (= [1 2] (:position result))))))

;; -----------------------------------------------------------------------------
;; Encoding tests for namespaced keywords
;; -----------------------------------------------------------------------------

(deftest encode-namespaced-enum-value-test
  (testing "encodes namespaced enum value to string with namespace"
    (let [schema [:map [:active-player NamespacedTeamEnum]]
          result (transform/encode {:active-player :team/HOME} schema)]
      (is (= "team/HOME" (get result "active_player"))))))

(deftest encode-namespaced-map-keys-test
  (testing "encodes namespaced uppercase map keys"
    (let [result (transform/encode
                  {:team/HOME 10 :team/AWAY 5}
                  ScoreSchema)]
      (is (= {"team/HOME" 10 "team/AWAY" 5} result)))))

(deftest encode-namespaced-multi-schema-test
  (testing "encodes multi schema dispatch value with namespace"
    (let [result (transform/encode
                  {:status :ball-status/POSSESSED :holder-id "player-1"}
                  NamespacedBallSchema)]
      (is (= "ball-status/POSSESSED" (get result "status")))
      (is (= "player-1" (get result "holder_id"))))))

;; -----------------------------------------------------------------------------
;; Round-trip tests for namespaced keywords
;; -----------------------------------------------------------------------------

(deftest roundtrip-namespaced-keywords-test
  (testing "namespaced enum values survive roundtrip"
    (let [original {:active-player :team/HOME
                    :phase :phase/ACTIONS
                    :score {:team/HOME 10 :team/AWAY 8}}
          encoded  (transform/encode original GameStateWithNamespacedEnumsSchema)
          decoded  (transform/decode encoded GameStateWithNamespacedEnumsSchema)]
      (is (= original decoded))))

  (testing "namespaced map keys survive roundtrip"
    (let [original {:team/HOME 10 :team/AWAY 5}
          encoded  (transform/encode original ScoreSchema)
          decoded  (transform/decode encoded ScoreSchema)]
      (is (= original decoded))))

  (testing "namespaced multi schema dispatch survives roundtrip"
    (let [original {:status :ball-status/POSSESSED :holder-id "player-1"}
          encoded  (transform/encode original NamespacedBallSchema)
          decoded  (transform/decode encoded NamespacedBallSchema)]
      (is (= original decoded)))))
