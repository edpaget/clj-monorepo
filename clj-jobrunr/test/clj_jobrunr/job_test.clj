(ns clj-jobrunr.job-test
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}
                                 :inline-def {:level :off}}}}
  (:require
   [clj-jobrunr.job :as job :refer [defjob handle-job]]
   [clj-jobrunr.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each tu/reset-handlers)

;; ---------------------------------------------------------------------------
;; defjob macro tests
;; ---------------------------------------------------------------------------

(deftest defjob-creates-keyword-var-test
  (testing "macro creates a var holding the job type keyword"
    (defjob test-job-1
      [payload]
      (:value payload))
    (is (keyword? test-job-1))
    (is (= ::test-job-1 test-job-1))
    (is (= 42 (handle-job test-job-1 {:value 42})))))

(deftest defjob-var-has-docstring-test
  (testing "generated var has docstring metadata"
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
    (is (= 10 (handle-job test-job-3 {:n 5})))))

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
    (let [result (handle-job test-job-6 {:user {:name "Alice"} :extra :data})]
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
    (is (keyword? test-job-attr))
    (is (= "A job with attr-map." (:doc (meta #'test-job-attr))))))

(deftest defjob-attr-map-without-docstring-test
  (testing "attr-map works without docstring"
    (defjob test-job-attr-no-doc
      {:job/derives [::category]}
      [payload]
      (* 2 (:n payload)))
    (is (= 20 (handle-job test-job-attr-no-doc {:n 10})))))

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

(deftest defjob-var-value-test
  (testing "var value is the namespaced keyword"
    (defjob test-job-args
      "Docstring here."
      [payload]
      payload)
    (is (= ::test-job-args test-job-args))
    (is (= :test-job-args (keyword (name test-job-args))))))

;; ---------------------------------------------------------------------------
;; Phase 4: Additional edge case tests
;; ---------------------------------------------------------------------------

(deftest defjob-nested-destructuring-test
  (testing "deeply nested destructuring works"
    (defjob test-nested-job
      [{:keys [user]
        {:keys [street city]} :address
        :as payload}]
      {:user-id (:id user)
       :location (str street ", " city)
       :raw payload})
    (let [result (handle-job test-nested-job {:user {:id 123}
                                              :address {:street "123 Main St" :city "Boston"}})]
      (is (= 123 (:user-id result)))
      (is (= "123 Main St, Boston" (:location result))))))

(deftest defjob-side-effect-only-test
  (testing "job that performs side effects and returns nil"
    (let [side-effect (atom nil)]
      (defjob test-side-effect-job
        [payload]
        (reset! side-effect (:value payload))
        nil)
      (let [result (handle-job test-side-effect-job {:value "done"})]
        (is (nil? result))
        (is (= "done" @side-effect))))))

(deftest defjob-with-let-binding-test
  (testing "job can use let bindings in body"
    (defjob test-let-job
      [{:keys [items]}]
      (let [total (reduce + items)
            cnt   (count items)
            avg   (/ total cnt)]
        {:total total :count cnt :avg avg}))
    (let [result (handle-job test-let-job {:items [10 20 30]})]
      (is (= 60 (:total result)))
      (is (= 3 (:count result)))
      (is (= 20 (:avg result))))))

(deftest defjob-job-type-keyword-format-test
  (testing "job type keyword is properly namespaced with current namespace"
    (defjob test-namespace-job [p] p)
    (let [job-type ::test-namespace-job]
      ;; Verify it's a keyword
      (is (keyword? job-type))
      ;; Verify it has a namespace
      (is (some? (namespace job-type)))
      ;; Verify the namespace matches this test namespace
      (is (= "clj-jobrunr.job-test" (namespace job-type)))
      ;; Verify the name
      (is (= "test-namespace-job" (name job-type))))))

(deftest defjob-exception-propagation-test
  (testing "exceptions from job body propagate correctly"
    (defjob test-throwing-job
      [{:keys [should-throw]}]
      (when should-throw
        (throw (ex-info "Job error" {:reason :test})))
      :success)
    ;; Normal execution
    (is (= :success (handle-job test-throwing-job {:should-throw false})))
    ;; Exception propagates
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Job error"
         (handle-job test-throwing-job {:should-throw true})))))
