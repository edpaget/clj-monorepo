(ns bashketball-game-ui.graphql.transformer-test
  "Tests for GraphQL response transformers.

  Tests the app-specific Malli transformers for decoding GraphQL responses,
  including HexPosition tuples, PolicyExpr scalars, and SkillTestTarget scalars."
  (:require
   [bashketball-game.schema :as game-schema]
   [bashketball-game-ui.graphql.transformer :as transformer]
   [bashketball-schemas.effect :as effect]
   [cljs.test :as t :include-macros true]
   [clojure.edn :as edn]
   [malli.core :as m]
   [malli.transform :as mt]))

;; =============================================================================
;; Tuple Transformer Tests (HexPosition)
;; =============================================================================

(t/deftest tuple-transformer-decodes-hex-position-string
  (t/testing "decodes EDN string to vector for HexPosition"
    (let [result (m/decode game-schema/HexPosition "[2 5]" transformer/decoding-transformer)]
      (t/is (vector? result))
      (t/is (= [2 5] result)))))

(t/deftest tuple-transformer-preserves-vector
  (t/testing "preserves already-decoded vectors"
    (let [result (m/decode game-schema/HexPosition [3 7] transformer/decoding-transformer)]
      (t/is (= [3 7] result)))))

(t/deftest tuple-transformer-handles-origin-edge
  (t/testing "decodes origin position [0 0]"
    (let [result (m/decode game-schema/HexPosition "[0 0]" transformer/decoding-transformer)]
      (t/is (= [0 0] result)))))

;; =============================================================================
;; PolicyExpr Transformer Tests
;; =============================================================================

(t/deftest policy-expr-decodes-simple-keyword
  (t/testing "decodes keyword from EDN string"
    (let [result (m/decode effect/PolicyExpr ":doc/phase" transformer/decoding-transformer)]
      (t/is (keyword? result))
      (t/is (= :doc/phase result)))))

(t/deftest policy-expr-decodes-vector-expression
  (t/testing "decodes vector expression from EDN string"
    (let [result (m/decode effect/PolicyExpr "[:= :doc/phase :phase/PLAY]" transformer/decoding-transformer)]
      (t/is (vector? result))
      (t/is (= [:= :doc/phase :phase/PLAY] result)))))

(t/deftest policy-expr-decodes-nested-expression
  (t/testing "decodes nested boolean expression"
    (let [expr   "[:and [:= :doc/phase :phase/PLAY] [:bashketball/has-ball? :doc/state :self/id]]"
          result (m/decode effect/PolicyExpr expr transformer/decoding-transformer)]
      (t/is (vector? result))
      (t/is (= :and (first result)))
      (t/is (= 3 (count result))))))

(t/deftest policy-expr-decodes-integer
  (t/testing "decodes integer from EDN string"
    (let [result (m/decode effect/PolicyExpr "42" transformer/decoding-transformer)]
      (t/is (int? result))
      (t/is (= 42 result)))))

(t/deftest policy-expr-decodes-boolean
  (t/testing "decodes boolean from EDN string"
    (t/is (true? (m/decode effect/PolicyExpr "true" transformer/decoding-transformer)))
    (t/is (false? (m/decode effect/PolicyExpr "false" transformer/decoding-transformer)))))

(t/deftest policy-expr-decodes-string-literal
  (t/testing "decodes string literal from EDN string"
    (let [result (m/decode effect/PolicyExpr "\"admin\"" transformer/decoding-transformer)]
      (t/is (string? result))
      (t/is (= "admin" result)))))

(t/deftest policy-expr-preserves-already-decoded
  (t/testing "preserves already-decoded values"
    (t/is (= :doc/phase (m/decode effect/PolicyExpr :doc/phase transformer/decoding-transformer)))
    (t/is (= [:= :a 1] (m/decode effect/PolicyExpr [:= :a 1] transformer/decoding-transformer)))
    (t/is (= 42 (m/decode effect/PolicyExpr 42 transformer/decoding-transformer)))))

;; =============================================================================
;; SkillTestTarget Transformer Tests
;; =============================================================================
;; SkillTestTarget is an inline :or schema inside SkillTestContext.
;; We test it by decoding the full SkillTestContext schema.

(t/deftest skill-test-context-decodes-hex-position-target
  (t/testing "decodes HexPosition target from EDN string"
    (let [input  {:type   :shoot
                  :origin "[1 3]"
                  :target "[2 8]"}
          result (m/decode game-schema/SkillTestContext input transformer/decoding-transformer)]
      (t/is (= :shoot (:type result)))
      (t/is (= [1 3] (:origin result)))
      (t/is (= [2 8] (:target result))))))

(t/deftest skill-test-context-decodes-string-target
  (t/testing "decodes string player ID target from EDN string"
    (let [input  {:type        :defend
                  :defender-id "player-1"
                  :target      "\"HOME-2\""}
          result (m/decode game-schema/SkillTestContext input transformer/decoding-transformer)]
      (t/is (= :defend (:type result)))
      (t/is (= "player-1" (:defender-id result)))
      (t/is (= "HOME-2" (:target result))))))

(t/deftest skill-test-context-preserves-already-decoded-target
  (t/testing "preserves already-decoded target values"
    (let [input  {:type   :pass
                  :origin [0 5]
                  :target [3 10]}
          result (m/decode game-schema/SkillTestContext input transformer/decoding-transformer)]
      (t/is (= [0 5] (:origin result)))
      (t/is (= [3 10] (:target result))))))

;; =============================================================================
;; Isolated :or Scalar Transformer Tests
;; =============================================================================
;; Test the or-scalar-transformer in isolation to verify PolicyExpr decoding.

(def ^:private or-scalar-only-transformer
  "Isolated transformer for testing :or scalar decoding."
  (mt/transformer
   {:decoders
    {:or
     {:compile
      (fn [schema _opts]
        (when (-> schema m/properties :graphql/scalar)
          (fn [value]
            (if (string? value)
              (edn/read-string value)
              value))))}}}))

(t/deftest or-scalar-decodes-in-isolation
  (t/testing "or-scalar-transformer decodes PolicyExpr from EDN string"
    (let [result (m/decode effect/PolicyExpr "[:= :a :b]" or-scalar-only-transformer)]
      (t/is (vector? result))
      (t/is (= [:= :a :b] result)))))

(t/deftest or-scalar-preserves-non-strings
  (t/testing "or-scalar-transformer preserves non-string values"
    (t/is (= [:= :a :b] (m/decode effect/PolicyExpr [:= :a :b] or-scalar-only-transformer)))
    (t/is (= :keyword (m/decode effect/PolicyExpr :keyword or-scalar-only-transformer)))
    (t/is (= 42 (m/decode effect/PolicyExpr 42 or-scalar-only-transformer)))))

;; =============================================================================
;; Decode Function Tests
;; =============================================================================
;; Test the decode helper function with effect schemas.

(t/deftest decode-function-handles-policy-expr
  (t/testing "transformer/decode decodes PolicyExpr in maps"
    (let [input  {:condition "[:= :doc/phase :phase/PLAY]"}
          schema [:map [:condition effect/PolicyExpr]]
          result (transformer/decode input schema)]
      (t/is (vector? (:condition result)))
      (t/is (= [:= :doc/phase :phase/PLAY] (:condition result))))))

(t/deftest decode-function-handles-nested-policy-expr
  (t/testing "transformer/decode handles nested PolicyExpr"
    (let [input  {:outer {:inner {:expr "[:and true false]"}}}
          schema [:map [:outer [:map [:inner [:map [:expr effect/PolicyExpr]]]]]]
          result (transformer/decode input schema)]
      (t/is (= [:and true false] (get-in result [:outer :inner :expr]))))))
