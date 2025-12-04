(ns db.transform-test
  "Tests for db.transform Malli-based transformation utilities."
  (:require
   [clojure.test :refer [deftest is testing]]
   [db.transform :as transform]
   [malli.core :as m]))

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
