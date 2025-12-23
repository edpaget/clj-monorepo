(ns polix.operators-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [polix.compiler :as compiler]
   [polix.operators :as ops]
   [polix.residual :as res]))

(deftest builtin-operators-test
  (testing "equality operators"
    (is (true? (ops/eval-constraint {:op := :value "admin"} "admin")))
    (is (false? (ops/eval-constraint {:op := :value "admin"} "guest")))
    (is (true? (ops/eval-constraint {:op :!= :value "admin"} "guest")))
    (is (false? (ops/eval-constraint {:op :!= :value "admin"} "admin"))))

  (testing "comparison operators"
    (is (true? (ops/eval-constraint {:op :> :value 5} 10)))
    (is (false? (ops/eval-constraint {:op :> :value 5} 3)))
    (is (true? (ops/eval-constraint {:op :>= :value 5} 5)))
    (is (true? (ops/eval-constraint {:op :< :value 10} 5)))
    (is (true? (ops/eval-constraint {:op :<= :value 10} 10))))

  (testing "set membership operators"
    (is (true? (ops/eval-constraint {:op :in :value #{"a" "b"}} "a")))
    (is (false? (ops/eval-constraint {:op :in :value #{"a" "b"}} "c")))
    (is (true? (ops/eval-constraint {:op :not-in :value #{"a" "b"}} "c")))
    (is (false? (ops/eval-constraint {:op :not-in :value #{"a" "b"}} "a"))))

  (testing "pattern matching operators"
    (is (true? (ops/eval-constraint {:op :matches :value "admin.*"} "admin123")))
    (is (false? (ops/eval-constraint {:op :matches :value "admin.*"} "user123")))
    (is (true? (ops/eval-constraint {:op :not-matches :value "admin.*"} "user123")))))

(deftest operator-negation-test
  (testing "negating equality"
    (let [c       {:op := :key :role :value "admin"}
          negated (ops/negate-constraint c)]
      (is (= :!= (:op negated)))
      (is (= "admin" (:value negated)))))

  (testing "negating comparison"
    (is (= :<= (:op (ops/negate-constraint {:op :> :value 5}))))
    (is (= :< (:op (ops/negate-constraint {:op :>= :value 5}))))
    (is (= :>= (:op (ops/negate-constraint {:op :< :value 5}))))
    (is (= :> (:op (ops/negate-constraint {:op :<= :value 5}))))))

(deftest negate-op-test
  (testing "negate-op returns negated operator keyword"
    (is (= :!= (ops/negate-op :=)))
    (is (= := (ops/negate-op :!=)))
    (is (= :<= (ops/negate-op :>)))
    (is (= :< (ops/negate-op :>=)))
    (is (= :>= (ops/negate-op :<)))
    (is (= :> (ops/negate-op :<=)))
    (is (= :not-in (ops/negate-op :in)))
    (is (= :in (ops/negate-op :not-in))))

  (testing "negate-op returns nil for unknown operator"
    (is (nil? (ops/negate-op :unknown-op)))))

(deftest flip-op-test
  (testing "flip-op returns flipped operator for asymmetric ops"
    (is (= :< (ops/flip-op :>)))
    (is (= :> (ops/flip-op :<)))
    (is (= :<= (ops/flip-op :>=)))
    (is (= :>= (ops/flip-op :<=))))

  (testing "flip-op returns same operator for symmetric ops"
    (is (= := (ops/flip-op :=)))
    (is (= :!= (ops/flip-op :!=)))
    (is (= :in (ops/flip-op :in)))
    (is (= :not-in (ops/flip-op :not-in))))

  (testing "flip-op returns op-key for unknown operator"
    (is (= :unknown-op (ops/flip-op :unknown-op)))))

(deftest custom-operator-test
  (testing "registering and using custom operator"
    (ops/register-operator! :starts-with
                            {:eval (fn [value expected] (str/starts-with? (str value) expected))
                             :negate :not-starts-with})

    (ops/register-operator! :not-starts-with
                            {:eval (fn [value expected] (not (str/starts-with? (str value) expected)))
                             :negate :starts-with})

    (is (true? (ops/eval-constraint {:op :starts-with :value "admin"} "admin-user")))
    (is (false? (ops/eval-constraint {:op :starts-with :value "admin"} "guest-user")))

    (let [negated (ops/negate-constraint {:op :starts-with :value "admin"})]
      (is (= :not-starts-with (:op negated))))))

(deftest custom-operator-in-compiler-test
  (testing "custom operator works with compile-policies"
    (ops/register-operator! :ends-with
                            {:eval (fn [value expected] (str/ends-with? (str value) expected))})

    (let [check (compiler/compile-policies [[:ends-with :doc/email "@example.com"]])]
      (is (= {} (check {:email "user@example.com"})))
      (is (res/has-conflicts? (check {:email "user@other.com"}))))))

(deftest defoperator-macro-test
  (testing "defoperator macro"
    (ops/defoperator :contains-substr
      :eval (fn [value expected] (str/includes? (str value) expected))
      :negate :not-contains-substr)

    (is (true? (ops/eval-constraint {:op :contains-substr :value "foo"} "hello foo world")))
    (is (false? (ops/eval-constraint {:op :contains-substr :value "bar"} "hello foo world")))))

(deftest operator-registry-test
  (testing "get-operator returns registered operator"
    (is (some? (ops/get-operator :=)))
    (is (some? (ops/get-operator :>)))
    (is (nil? (ops/get-operator :nonexistent-op))))

  (testing "operator-keys returns all keys"
    (let [keys (ops/operator-keys)]
      (is (contains? (set keys) :=))
      (is (contains? (set keys) :>))
      (is (contains? (set keys) :in)))))

(deftest spec-validation-test
  (testing "missing :eval throws"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                             :cljs ExceptionInfo)
                          #"Invalid operator specification"
                          (ops/register-operator! :bad-op {}))))

  (testing "invalid :eval type throws"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                             :cljs ExceptionInfo)
                          #"Invalid operator specification"
                          (ops/register-operator! :bad-op {:eval "not-a-fn"}))))

  (testing "invalid :negate type throws"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                             :cljs ExceptionInfo)
                          #"Invalid operator specification"
                          (ops/register-operator! :bad-op {:eval identity :negate "not-a-keyword"})))))
