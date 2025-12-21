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

;;; ---------------------------------------------------------------------------
;;; Quantifier Policies
;;; ---------------------------------------------------------------------------

(def forall-simple
  "Forall with simple constraint - all users must be active."
  [:forall [:u :doc/users] [:= :u/active true]])

(def forall-nested-path
  "Forall with nested path access - all users must have verified profile."
  [:forall [:u :doc/users] [:= :u/profile.verified true]])

(def forall-multiple-constraints
  "Forall with AND body - all users must be active with score > 50."
  [:forall [:u :doc/users]
   [:and
    [:= :u/active true]
    [:> :u/score 50]]])

(def exists-simple
  "Exists with simple constraint - at least one admin."
  [:exists [:u :doc/users] [:= :u/role "admin"]])

(def nested-forall-exists
  "Nested quantifiers - every team must have at least one lead."
  [:forall [:team :doc/teams]
   [:exists [:member :team/members]
    [:= :member/role "lead"]]])

;;; ---------------------------------------------------------------------------
;;; Quantifier Documents
;;; ---------------------------------------------------------------------------

(def doc-users-5-all-active
  "5 users, all active."
  {:users (vec (repeat 5 {:active true :score 80 :profile {:verified true}}))})

(def doc-users-5-one-inactive
  "5 users, one inactive."
  {:users (conj (vec (repeat 4 {:active true})) {:active false})})

(def doc-users-20-all-verified
  "20 users with verified profiles."
  {:users (vec (repeat 20 {:active true :profile {:verified true}}))})

(def doc-users-100-all-active
  "100 users, all active."
  {:users (vec (repeat 100 {:active true}))})

(def doc-users-5-first-admin
  "5 users, first is admin."
  {:users (into [{:role "admin"}] (repeat 4 {:role "user"}))})

(def doc-users-5-no-admin
  "5 users, no admins."
  {:users (vec (repeat 5 {:role "user"}))})

(def doc-users-100-first-admin
  "100 users, first is admin (early exit)."
  {:users (into [{:role "admin"}] (repeat 99 {:role "user"}))})

(def doc-users-100-last-admin
  "100 users, last is admin (late exit)."
  {:users (conj (vec (repeat 99 {:role "user"})) {:role "admin"})})

(def doc-teams-all-have-lead
  "5 teams, each with members including a lead."
  {:teams (vec (repeat 5 {:members [{:role "dev"} {:role "lead"} {:role "dev"}]}))})

(def doc-teams-one-missing-lead
  "5 teams, one without a lead."
  {:teams (conj (vec (repeat 4 {:members [{:role "lead"}]}))
                {:members [{:role "dev"} {:role "dev"}]})})

;;; ---------------------------------------------------------------------------
;;; Count Function Policies
;;; ---------------------------------------------------------------------------

(def count-simple
  "Count collection size."
  [:>= [:fn/count :doc/users] 5])

(def count-medium
  "Count medium collection."
  [:>= [:fn/count :doc/users] 20])

(def count-large
  "Count large collection."
  [:>= [:fn/count :doc/users] 100])

(def count-nested-path
  "Count nested collection."
  [:>= [:fn/count :doc/org.members] 5])

(def count-with-comparison
  "Count with additional comparison."
  [:and
   [:>= [:fn/count :doc/users] 5]
   [:= :doc/active true]])

;;; ---------------------------------------------------------------------------
;;; Filtered Binding Policies
;;; ---------------------------------------------------------------------------

(def forall-filtered
  "Forall with filtered binding - all active users must have verified profile."
  [:forall [:u :doc/users :where [:= :u/active true]]
   [:= :u/profile.verified true]])

(def exists-filtered
  "Exists with filtered binding - at least one active user is admin."
  [:exists [:u :doc/users :where [:= :u/active true]]
   [:= :u/role "admin"]])

(def count-filtered
  "Count with filtered binding - count active users."
  [:>= [:fn/count [:u :doc/users :where [:= :u/active true]]] 3])

(def count-filtered-complex
  "Count with complex filter - count users with high score."
  [:>= [:fn/count [:u :doc/users :where [:and [:= :u/active true] [:> :u/score 80]]]] 2])

(def nested-filtered
  "Nested quantifiers with filtered bindings."
  [:forall [:team :doc/teams :where [:= :team/active true]]
   [:exists [:m :team/members :where [:> :m/level 5]]
    [:= :m/role "lead"]]])

;;; ---------------------------------------------------------------------------
;;; Count and Filter Documents
;;; ---------------------------------------------------------------------------

(def doc-users-5-all-active-verified
  "5 users, all active with verified profiles."
  {:users (vec (repeat 5 {:active true :role "user" :score 90
                          :profile {:verified true}}))})

(def doc-users-5-mixed-active
  "5 users, 3 active with verified profiles, 2 inactive."
  {:users (into (vec (repeat 3 {:active true :role "user" :score 90
                                :profile {:verified true}}))
                (repeat 2 {:active false :role "user" :score 50
                           :profile {:verified false}}))})

(def doc-users-20-half-active
  "20 users, 10 active, 10 inactive."
  {:users (into (vec (repeat 10 {:active true :role "user" :score 90
                                 :profile {:verified true}}))
                (repeat 10 {:active false :role "user" :score 50
                            :profile {:verified false}}))})

(def doc-users-100-mostly-active
  "100 users, 80 active, 20 inactive."
  {:users (into (vec (repeat 80 {:active true :role "user" :score 90
                                 :profile {:verified true}}))
                (repeat 20 {:active false :role "user" :score 50
                            :profile {:verified false}}))})

(def doc-users-5-active-with-admin
  "5 active users, first is admin."
  {:users (into [{:active true :role "admin" :score 95
                  :profile {:verified true}}]
                (repeat 4 {:active true :role "user" :score 85
                           :profile {:verified true}}))})

(def doc-users-5-active-no-admin
  "5 active users, none are admin."
  {:users (vec (repeat 5 {:active true :role "user" :score 85
                          :profile {:verified true}}))})

(def doc-users-100-active-first-admin
  "100 active users, first is admin."
  {:users (into [{:active true :role "admin" :score 95
                  :profile {:verified true}}]
                (repeat 99 {:active true :role "user" :score 85
                            :profile {:verified true}}))})

(def doc-users-100-active-last-admin
  "100 active users, last is admin."
  {:users (conj (vec (repeat 99 {:active true :role "user" :score 85
                                 :profile {:verified true}}))
                {:active true :role "admin" :score 95
                 :profile {:verified true}})})

(def doc-org-with-members
  "Organization with nested members collection."
  {:org {:name "Acme" :members (vec (repeat 10 {:name "User" :level 5}))}
   :active true})

(def doc-teams-5-active-with-leads
  "5 active teams, each with high-level lead."
  {:teams (vec (repeat 5 {:active true
                          :members [{:role "dev" :level 3}
                                    {:role "lead" :level 8}
                                    {:role "dev" :level 4}]}))})

(def doc-teams-5-active-missing-lead
  "5 active teams, one without high-level lead."
  {:teams (conj (vec (repeat 4 {:active true
                                :members [{:role "lead" :level 8}]}))
                {:active true
                 :members [{:role "dev" :level 3}
                           {:role "dev" :level 4}]})})
