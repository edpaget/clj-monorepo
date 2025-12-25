(ns polix.effects.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [polix.effects.schema :as schema]))

(deftest noop-effect-validation-test
  (testing "valid noop effect"
    (is (schema/valid? {:type :polix.effects/noop})))

  (testing "noop with extra keys is valid"
    (is (schema/valid? {:type :polix.effects/noop :meta {:source "test"}}))))

(deftest assoc-in-effect-validation-test
  (testing "valid assoc-in with keyword path"
    (is (schema/valid? {:type :polix.effects/assoc-in
                        :path [:user :name]
                        :value "Alice"})))

  (testing "valid assoc-in with string path segments"
    (is (schema/valid? {:type :polix.effects/assoc-in
                        :path [:users "user-123" :name]
                        :value "Bob"})))

  (testing "valid assoc-in with integer path segments"
    (is (schema/valid? {:type :polix.effects/assoc-in
                        :path [:items 0 :name]
                        :value "First"})))

  (testing "missing path is invalid"
    (is (some? (schema/explain {:type :polix.effects/assoc-in
                                :value "Alice"}))))

  (testing "missing value is invalid"
    (is (some? (schema/explain {:type :polix.effects/assoc-in
                                :path [:user :name]})))))

(deftest sequence-effect-validation-test
  (testing "valid empty sequence"
    (is (schema/valid? {:type :polix.effects/sequence
                        :effects []})))

  (testing "valid sequence with effects"
    (is (schema/valid? {:type :polix.effects/sequence
                        :effects [{:type :polix.effects/noop}
                                  {:type :polix.effects/assoc-in
                                   :path [:x]
                                   :value 1}]})))

  (testing "missing effects key is invalid"
    (is (some? (schema/explain {:type :polix.effects/sequence})))))

(deftest unknown-effect-type-test
  (testing "unknown effect type passes validation (as CustomEffect)"
    (is (schema/valid? {:type :unknown/effect})))

  (testing "effect without :type key fails validation"
    (is (some? (schema/explain {:path [:x] :value 1})))))
