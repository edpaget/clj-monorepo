(ns polix.bench.policies
  "Test policy generators for benchmarking.

  Provides policy expressions at three complexity levels: simple, medium, and complex.
  Each level includes matching documents for testing satisfied, contradicted, and
  residual evaluation paths."
  (:require
   [polix.parser :as parser]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; Simple Policies (1-2 operators)
;;; ---------------------------------------------------------------------------

(def simple-equality
  "Simple equality check."
  [:= :doc/role "admin"])

(def simple-comparison
  "Simple comparison check."
  [:> :doc/level 5])

(def simple-in
  "Simple set membership check."
  [:in :doc/status #{"active" "pending"}])

;;; ---------------------------------------------------------------------------
;;; Medium Policies (5-10 operators)
;;; ---------------------------------------------------------------------------

(def medium-and
  "Medium complexity AND policy with 5 constraints."
  [:and
   [:= :doc/role "admin"]
   [:> :doc/level 5]
   [:in :doc/status #{"active" "pending"}]
   [:< :doc/age 65]
   [:>= :doc/score 80]])

(def medium-mixed
  "Medium complexity with nested OR."
  [:and
   [:or [:= :doc/role "admin"]
        [:= :doc/role "moderator"]]
   [:> :doc/level 3]
   [:in :doc/department #{"engineering" "security"}]
   [:not [:= :doc/suspended true]]])

;;; ---------------------------------------------------------------------------
;;; Complex Policies (20+ operators)
;;; ---------------------------------------------------------------------------

(def complex-nested
  "Complex deeply nested policy with 20+ operators."
  [:or
   [:and
    [:= :doc/role "superadmin"]
    [:> :doc/clearance 9]]
   [:and
    [:= :doc/role "admin"]
    [:> :doc/level 10]
    [:in :doc/status #{"active"}]
    [:or
     [:= :doc/department "security"]
     [:and
      [:= :doc/department "engineering"]
      [:> :doc/tenure 5]]]]
   [:and
    [:= :doc/role "moderator"]
    [:or
     [:and [:> :doc/karma 1000] [:< :doc/warnings 3]]
     [:and [:> :doc/reputation 500] [:= :doc/verified true]]]
    [:in :doc/region #{"us" "eu" "apac"}]
    [:not [:in :doc/restricted-flag #{"flagged" "suspended"}]]]
   [:and
    [:= :doc/role "user"]
    [:> :doc/account-age 365]
    [:>= :doc/trust-score 90]
    [:in :doc/subscription #{"premium" "enterprise"}]
    [:not [:= :doc/trial true]]]])

(def complex-wide
  "Complex wide policy with many AND constraints."
  [:and
   [:= :doc/type "request"]
   [:in :doc/method #{"GET" "POST" "PUT" "DELETE"}]
   [:> :doc/priority 0]
   [:< :doc/priority 100]
   [:>= :doc/timestamp 1000000]
   [:not [:= :doc/blocked true]]
   [:in :doc/source #{"api" "web" "mobile" "sdk"}]
   [:or [:= :doc/authenticated true]
        [:in :doc/path #{"/" "/health" "/public"}]]
   [:not [:in :doc/ip-country #{"XX" "YY"}]]
   [:>= :doc/rate-limit-remaining 1]
   [:< :doc/request-size 10485760]
   [:in :doc/content-type #{"application/json" "text/plain" "multipart/form-data"}]
   [:or [:= :doc/internal true]
        [:and [:> :doc/api-version 1] [:<= :doc/api-version 3]]]
   [:not [:= :doc/deprecated-endpoint true]]
   [:in :doc/protocol #{"http" "https"}]])

;;; ---------------------------------------------------------------------------
;;; Documents
;;; ---------------------------------------------------------------------------

(def doc-simple-satisfied
  "Document that satisfies simple policies."
  {:role "admin" :level 10 :status "active"})

(def doc-simple-contradicted
  "Document that contradicts simple policies."
  {:role "guest" :level 2 :status "banned"})

(def doc-medium-satisfied
  "Document that satisfies medium policies."
  {:role "admin" :level 10 :status "active" :age 30 :score 95
   :department "engineering" :suspended false})

(def doc-medium-partial
  "Document that partially satisfies medium policies (creates residual)."
  {:role "admin"})

(def doc-complex-satisfied
  "Document that satisfies complex-nested (admin path)."
  {:role "admin" :level 15 :status "active" :department "security"
   :clearance 5 :karma 500 :warnings 0 :region "us"})

(def doc-complex-partial
  "Document that partially satisfies complex policies."
  {:role "admin" :level 15})

(def doc-empty
  "Empty document - creates full residual."
  {})

;;; ---------------------------------------------------------------------------
;;; Pre-parsed ASTs
;;; ---------------------------------------------------------------------------

(defn parse!
  "Parses a policy expression, throwing on error."
  [expr]
  (let [result (parser/parse-policy expr)]
    (if (r/error? result)
      (throw (ex-info "Parse failed" (r/unwrap result)))
      (r/unwrap result))))

(def simple-equality-ast (delay (parse! simple-equality)))
(def simple-comparison-ast (delay (parse! simple-comparison)))
(def medium-and-ast (delay (parse! medium-and)))
(def medium-mixed-ast (delay (parse! medium-mixed)))
(def complex-nested-ast (delay (parse! complex-nested)))
(def complex-wide-ast (delay (parse! complex-wide)))

;;; ---------------------------------------------------------------------------
;;; Policy Collections
;;; ---------------------------------------------------------------------------

(def all-simple-policies
  "All simple policy expressions."
  [simple-equality simple-comparison simple-in])

(def all-medium-policies
  "All medium policy expressions."
  [medium-and medium-mixed])

(def all-complex-policies
  "All complex policy expressions."
  [complex-nested complex-wide])

(def scaling-policies
  "Policies for scaling benchmarks - 1, 5, 10, 50 simple constraints merged."
  {:n1  [simple-equality]
   :n5  (repeat 5 simple-equality)
   :n10 (repeat 10 simple-equality)
   :n50 (repeat 50 simple-equality)})
