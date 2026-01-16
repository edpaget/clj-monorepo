(ns clj-jobrunr.job-test
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}
                                 :inline-def {:level :off}}}}
  (:require [clj-jobrunr.job :as job :refer [defjob handle-job]]
            [clojure.test :refer [deftest is testing use-fixtures]]))

;; Reset multimethod between tests to avoid pollution
(defn reset-handlers [f]
  (doseq [k (keys (methods handle-job))]
    (when (not= k :default)
      (remove-method handle-job k)))
  (f))

(use-fixtures :each reset-handlers)

;; ---------------------------------------------------------------------------
;; defjob macro tests
;; ---------------------------------------------------------------------------

(deftest defjob-creates-function-test
  (testing "macro creates a callable function with correct name"
    (defjob test-job-1
      [payload]
      (:value payload))
    (is (fn? test-job-1))
    (is (= 42 (test-job-1 {:value 42})))))

(deftest defjob-function-has-docstring-test
  (testing "generated function has docstring metadata"
    (defjob test-job-2
      "This is a test job."
      [payload]
      payload)
    (is (= "This is a test job." (:doc (meta #'test-job-2))))))

(deftest defjob-without-docstring-test
  (testing "works when docstring is omitted"
    (defjob test-job-3
      [payload]
      (* 2 (:n payload)))
    (is (= 10 (test-job-3 {:n 5})))))

(deftest defjob-registers-multimethod-test
  (testing "handler is registered on handle-job multimethod with namespaced keyword"
    (defjob test-job-4
      [payload]
      payload)
    ;; Should be registered with namespaced keyword
    (is (contains? (methods handle-job) ::test-job-4))))

(deftest defjob-multimethod-dispatches-test
  (testing "handle-job routes to correct handler using namespaced keyword"
    (defjob test-job-5
      [{:keys [x y]}]
      (+ x y))
    ;; Must use namespaced keyword for dispatch
    (is (= 7 (handle-job ::test-job-5 {:x 3 :y 4})))))

(deftest defjob-with-destructuring-test
  (testing "complex destructuring in params works"
    (defjob test-job-6
      [{:keys [user] :as payload}]
      {:user-name (:name user)
       :total-keys (count (keys payload))})
    (let [result (test-job-6 {:user {:name "Alice"} :extra :data})]
      (is (= "Alice" (:user-name result)))
      (is (= 2 (:total-keys result))))))

(deftest defjob-returns-job-type-test
  (testing "defjob returns the namespaced job type keyword"
    (let [result (defjob test-job-7 [p] p)]
      (is (= ::test-job-7 result)))))

;; ---------------------------------------------------------------------------
;; attr-map tests
;; ---------------------------------------------------------------------------

(deftest defjob-with-attr-map-test
  (testing "attr-map is accepted between docstring and params"
    (defjob test-job-attr
      "A job with attr-map."
      {:job/derives [::some-category]}
      [payload]
      payload)
    (is (fn? test-job-attr))
    (is (= "A job with attr-map." (:doc (meta #'test-job-attr))))))

(deftest defjob-attr-map-without-docstring-test
  (testing "attr-map works without docstring"
    (defjob test-job-attr-no-doc
      {:job/derives [::category]}
      [payload]
      (* 2 (:n payload)))
    (is (= 20 (test-job-attr-no-doc {:n 10})))))

;; ---------------------------------------------------------------------------
;; handle-job multimethod tests
;; ---------------------------------------------------------------------------

(deftest handle-job-default-test
  (testing ":default method handles unknown job types"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"No handler registered"
         (handle-job ::unknown-job-type {:data 123})))))

(deftest handle-job-hierarchy-derives-test
  (testing ":job/derives creates hierarchy relationships"
    (defjob email-job
      {:job/derives [::notification]}
      [payload]
      :email-done)

    (defjob sms-job
      {:job/derives [::notification]}
      [payload]
      :sms-done)

    ;; Verify hierarchy exists
    (is (isa? ::email-job ::notification))
    (is (isa? ::sms-job ::notification))

    ;; Specific dispatch still works
    (is (= :email-done (handle-job ::email-job {})))
    (is (= :sms-done (handle-job ::sms-job {})))))

(deftest handle-job-hierarchy-dispatch-test
  (testing "hierarchy dispatch falls back to parent handler"
    ;; Define a parent handler for ::async-job
    (defmethod handle-job ::async-job [job-type payload]
      {:handled-by ::async-job
       :original-type job-type
       :payload payload})

    ;; Define a job that derives from ::async-job but has no specific handler
    ;; We manually derive since we're not using defjob for this test case
    (derive ::report-job ::async-job)

    ;; Dispatch to ::report-job should fall back to ::async-job handler
    (let [result (handle-job ::report-job {:data 123})]
      (is (= ::async-job (:handled-by result)))
      (is (= ::report-job (:original-type result)))
      (is (= {:data 123} (:payload result))))))

(deftest handle-job-multiple-derives-test
  (testing "job can derive from multiple parents"
    (defjob multi-parent-job
      {:job/derives [::category-a ::category-b]}
      [payload]
      :done)

    (is (isa? ::multi-parent-job ::category-a))
    (is (isa? ::multi-parent-job ::category-b))))

;; ---------------------------------------------------------------------------
;; multiple jobs and metadata tests
;; ---------------------------------------------------------------------------

(deftest defjob-multiple-jobs-test
  (testing "multiple defjobs in same namespace work"
    (defjob job-a [p] :a)
    (defjob job-b [p] :b)
    (defjob job-c [p] :c)

    (is (= :a (handle-job ::job-a {})))
    (is (= :b (handle-job ::job-b {})))
    (is (= :c (handle-job ::job-c {})))
    (is (= 3 (count (filter #{::job-a ::job-b ::job-c} (keys (methods handle-job))))))))

(deftest defjob-arglists-metadata-test
  (testing "function has correct :arglists metadata"
    (defjob test-job-args
      "Docstring here."
      [payload]
      payload)
    (is (= '([payload]) (:arglists (meta #'test-job-args))))))
