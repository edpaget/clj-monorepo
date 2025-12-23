(ns polix.bench
  "Benchmarks for polix performance analysis.

  Uses Criterium for statistically rigorous benchmarks across three phases:
  parsing, compilation, and evaluation. Run individual benchmarks interactively
  or use [[polix.bench.runner/run-ci]] for CI integration."
  (:require
   [criterium.core :as crit]
   [polix.bench.policies :as p]
   [polix.compiler :as compiler]
   [polix.engine :as engine]
   [polix.negate :as negate]
   [polix.operators :as op]
   [polix.parser :as parser]
   [polix.result :as r]
   [polix.unify :as unify]))

;;; ---------------------------------------------------------------------------
;;; Benchmark Configuration
;;; ---------------------------------------------------------------------------

(def ^:dynamic *quick*
  "When true, uses quick-bench instead of bench for faster iteration."
  false)

(defmacro run-bench
  "Runs a benchmark using either quick-bench or bench based on *quick*."
  [expr]
  `(if *quick*
     (crit/quick-bench ~expr)
     (crit/bench ~expr)))

(defn with-results
  "Runs a benchmark and returns the results map instead of printing."
  [f]
  (crit/benchmark* f {}))

;;; ---------------------------------------------------------------------------
;;; Parsing Benchmarks
;;; ---------------------------------------------------------------------------

(defn bench-parse-simple
  "Benchmarks parsing simple policies (1-2 operators)."
  []
  (println "\n=== Parsing: Simple Equality ===")
  (run-bench (parser/parse-policy p/simple-equality))
  (println "\n=== Parsing: Simple Comparison ===")
  (run-bench (parser/parse-policy p/simple-comparison)))

(defn bench-parse-medium
  "Benchmarks parsing medium policies (5-10 operators)."
  []
  (println "\n=== Parsing: Medium AND ===")
  (run-bench (parser/parse-policy p/medium-and))
  (println "\n=== Parsing: Medium Mixed ===")
  (run-bench (parser/parse-policy p/medium-mixed)))

(defn bench-parse-complex
  "Benchmarks parsing complex policies (20+ operators)."
  []
  (println "\n=== Parsing: Complex Nested ===")
  (run-bench (parser/parse-policy p/complex-nested))
  (println "\n=== Parsing: Complex Wide ===")
  (run-bench (parser/parse-policy p/complex-wide)))

(defn bench-parse-all
  "Runs all parsing benchmarks."
  []
  (bench-parse-simple)
  (bench-parse-medium)
  (bench-parse-complex))

;;; ---------------------------------------------------------------------------
;;; Compilation Benchmarks
;;; ---------------------------------------------------------------------------

(defn bench-compile-simple
  "Benchmarks compiling simple policies."
  []
  (println "\n=== Compile: Simple Equality ===")
  (run-bench (compiler/compile-policies [p/simple-equality])))

(defn bench-compile-medium
  "Benchmarks compiling medium policies."
  []
  (println "\n=== Compile: Medium AND ===")
  (run-bench (compiler/compile-policies [p/medium-and])))

(defn bench-compile-complex
  "Benchmarks compiling complex policies."
  []
  (println "\n=== Compile: Complex Nested ===")
  (run-bench (compiler/compile-policies [p/complex-nested])))

(defn bench-compile-scaling
  "Benchmarks compilation with increasing policy count."
  []
  (println "\n=== Compile: 1 Policy ===")
  (run-bench (compiler/compile-policies (:n1 p/scaling-policies)))
  (println "\n=== Compile: 5 Policies ===")
  (run-bench (compiler/compile-policies (:n5 p/scaling-policies)))
  (println "\n=== Compile: 10 Policies ===")
  (run-bench (compiler/compile-policies (:n10 p/scaling-policies)))
  (println "\n=== Compile: 50 Policies ===")
  (run-bench (compiler/compile-policies (:n50 p/scaling-policies))))

(defn bench-compile-all
  "Runs all compilation benchmarks."
  []
  (bench-compile-simple)
  (bench-compile-medium)
  (bench-compile-complex)
  (bench-compile-scaling))

;;; ---------------------------------------------------------------------------
;;; Evaluation Benchmarks
;;; ---------------------------------------------------------------------------

(defn bench-eval-ast-simple
  "Benchmarks direct AST evaluation for simple policies."
  []
  (let [ast @p/simple-equality-ast]
    (println "\n=== Eval AST: Simple Satisfied ===")
    (run-bench (engine/evaluate ast p/doc-simple-satisfied))
    (println "\n=== Eval AST: Simple Contradicted ===")
    (run-bench (engine/evaluate ast p/doc-simple-contradicted))
    (println "\n=== Eval AST: Simple Residual ===")
    (run-bench (engine/evaluate ast p/doc-empty))))

(defn bench-eval-ast-medium
  "Benchmarks direct AST evaluation for medium policies."
  []
  (let [ast @p/medium-and-ast]
    (println "\n=== Eval AST: Medium Satisfied ===")
    (run-bench (engine/evaluate ast p/doc-medium-satisfied))
    (println "\n=== Eval AST: Medium Partial Residual ===")
    (run-bench (engine/evaluate ast p/doc-medium-partial))))

(defn bench-eval-ast-complex
  "Benchmarks direct AST evaluation for complex policies."
  []
  (let [ast @p/complex-nested-ast]
    (println "\n=== Eval AST: Complex Satisfied ===")
    (run-bench (engine/evaluate ast p/doc-complex-satisfied))
    (println "\n=== Eval AST: Complex Partial ===")
    (run-bench (engine/evaluate ast p/doc-complex-partial))))

(defn bench-eval-compiled-simple
  "Benchmarks compiled function evaluation for simple policies."
  []
  (let [check (compiler/compile-policies [p/simple-equality])]
    (println "\n=== Eval Compiled: Simple Satisfied ===")
    (run-bench (check p/doc-simple-satisfied))
    (println "\n=== Eval Compiled: Simple Contradicted ===")
    (run-bench (check p/doc-simple-contradicted))))

(defn bench-eval-compiled-medium
  "Benchmarks compiled function evaluation for medium policies."
  []
  (let [check (compiler/compile-policies [p/medium-and])]
    (println "\n=== Eval Compiled: Medium Satisfied ===")
    (run-bench (check p/doc-medium-satisfied))
    (println "\n=== Eval Compiled: Medium Partial ===")
    (run-bench (check p/doc-medium-partial))))

(defn bench-eval-compiled-complex
  "Benchmarks compiled function evaluation for complex policies."
  []
  (let [check (compiler/compile-policies [p/complex-nested])]
    (println "\n=== Eval Compiled: Complex Satisfied ===")
    (run-bench (check p/doc-complex-satisfied))))

(defn bench-eval-all
  "Runs all evaluation benchmarks."
  []
  (println "\n--- Direct AST Evaluation ---")
  (bench-eval-ast-simple)
  (bench-eval-ast-medium)
  (bench-eval-ast-complex)
  (println "\n--- Compiled Evaluation ---")
  (bench-eval-compiled-simple)
  (bench-eval-compiled-medium)
  (bench-eval-compiled-complex))

;;; ---------------------------------------------------------------------------
;;; Unify Benchmarks (new residual-based API)
;;; ---------------------------------------------------------------------------

(defn bench-unify-simple
  "Benchmarks unify for simple policies."
  []
  (let [ast @p/simple-equality-ast]
    (println "\n=== Unify: Simple Satisfied ===")
    (run-bench (unify/unify ast p/doc-simple-satisfied))
    (println "\n=== Unify: Simple Contradicted ===")
    (run-bench (unify/unify ast p/doc-simple-contradicted))
    (println "\n=== Unify: Simple Residual ===")
    (run-bench (unify/unify ast p/doc-empty))))

(defn bench-unify-medium
  "Benchmarks unify for medium policies."
  []
  (let [ast @p/medium-and-ast]
    (println "\n=== Unify: Medium Satisfied ===")
    (run-bench (unify/unify ast p/doc-medium-satisfied))
    (println "\n=== Unify: Medium Partial Residual ===")
    (run-bench (unify/unify ast p/doc-medium-partial))))

(defn bench-unify-complex
  "Benchmarks unify for complex policies."
  []
  (let [ast @p/complex-nested-ast]
    (println "\n=== Unify: Complex Satisfied ===")
    (run-bench (unify/unify ast p/doc-complex-satisfied))
    (println "\n=== Unify: Complex Partial ===")
    (run-bench (unify/unify ast p/doc-complex-partial))))

(defn bench-unify-all
  "Runs all unify benchmarks."
  []
  (bench-unify-simple)
  (bench-unify-medium)
  (bench-unify-complex))

;;; ---------------------------------------------------------------------------
;;; Negation Benchmarks
;;; ---------------------------------------------------------------------------

(defn bench-negate
  "Benchmarks policy negation."
  []
  (let [simple-ast  @p/simple-equality-ast
        medium-ast  @p/medium-and-ast
        complex-ast @p/complex-nested-ast]
    (println "\n=== Negate: Simple ===")
    (run-bench (negate/negate simple-ast))
    (println "\n=== Negate: Medium ===")
    (run-bench (negate/negate medium-ast))
    (println "\n=== Negate: Complex ===")
    (run-bench (negate/negate complex-ast))))

;;; ---------------------------------------------------------------------------
;;; Inverse Query Benchmarks
;;; ---------------------------------------------------------------------------

(defn bench-inverse-queries
  "Benchmarks inverse queries (what-satisfies, what-contradicts).

  These use the unified residual model:
  - (unify policy {}) → what constraints must a document satisfy?
  - (unify (negate policy) {}) → what constraints would contradict?"
  []
  (let [simple-ast  @p/simple-equality-ast
        medium-ast  @p/medium-and-ast
        complex-ast @p/complex-nested-ast]
    (println "\n=== What Satisfies (Simple) ===")
    (run-bench (unify/unify simple-ast {}))
    (println "\n=== What Satisfies (Medium) ===")
    (run-bench (unify/unify medium-ast {}))
    (println "\n=== What Satisfies (Complex) ===")
    (run-bench (unify/unify complex-ast {}))
    (println "\n=== What Contradicts (Simple) ===")
    (run-bench (unify/unify (negate/negate simple-ast) {}))
    (println "\n=== What Contradicts (Medium) ===")
    (run-bench (unify/unify (negate/negate medium-ast) {}))
    (println "\n=== What Contradicts (Complex) ===")
    (run-bench (unify/unify (negate/negate complex-ast) {}))))

;;; ---------------------------------------------------------------------------
;;; Operator Benchmarks
;;; ---------------------------------------------------------------------------

(defn bench-operators
  "Benchmarks individual operator dispatch."
  []
  (let [ctx (op/make-context)]
    (println "\n=== Operator: Equality ===")
    (run-bench (op/eval-in-context ctx {:op := :value "admin"} "admin"))
    (println "\n=== Operator: Greater Than ===")
    (run-bench (op/eval-in-context ctx {:op :> :value 5} 10))
    (println "\n=== Operator: In Set ===")
    (run-bench (op/eval-in-context ctx {:op :in :value #{"a" "b" "c"}} "b"))
    (println "\n=== Operator: Regex Match ===")
    (let [pattern #"admin-\d+"]
      (run-bench (op/eval-in-context ctx {:op :matches :value pattern} "admin-123")))))

;;; ---------------------------------------------------------------------------
;;; Quantifier Benchmarks
;;; ---------------------------------------------------------------------------

(defn bench-quantifier-forall
  "Benchmarks forall quantifier evaluation."
  []
  (let [forall-checker (compiler/compile-policies [p/forall-simple])]
    (println "\n=== Quantifier: Forall Small Satisfied ===")
    (run-bench (forall-checker p/doc-users-5-all-active))
    (println "\n=== Quantifier: Forall Small Contradicted ===")
    (run-bench (forall-checker p/doc-users-5-one-inactive)))

  (let [forall-nested (compiler/compile-policies [p/forall-nested-path])]
    (println "\n=== Quantifier: Forall Medium Satisfied ===")
    (run-bench (forall-nested p/doc-users-20-all-verified)))

  (let [forall-simple (compiler/compile-policies [p/forall-simple])]
    (println "\n=== Quantifier: Forall Large Satisfied ===")
    (run-bench (forall-simple p/doc-users-100-all-active))))

(defn bench-quantifier-exists
  "Benchmarks exists quantifier evaluation."
  []
  (let [exists-checker (compiler/compile-policies [p/exists-simple])]
    (println "\n=== Quantifier: Exists Small Satisfied ===")
    (run-bench (exists-checker p/doc-users-5-first-admin))
    (println "\n=== Quantifier: Exists Small Contradicted ===")
    (run-bench (exists-checker p/doc-users-5-no-admin))
    (println "\n=== Quantifier: Exists Large Early Exit ===")
    (run-bench (exists-checker p/doc-users-100-first-admin))
    (println "\n=== Quantifier: Exists Large Late Exit ===")
    (run-bench (exists-checker p/doc-users-100-last-admin))))

(defn bench-quantifier-nested
  "Benchmarks nested quantifier evaluation."
  []
  (let [nested-checker (compiler/compile-policies [p/nested-forall-exists])]
    (println "\n=== Quantifier: Nested Satisfied ===")
    (run-bench (nested-checker p/doc-teams-all-have-lead))
    (println "\n=== Quantifier: Nested Contradicted ===")
    (run-bench (nested-checker p/doc-teams-one-missing-lead))))

(defn bench-quantifiers
  "Runs all quantifier benchmarks."
  []
  (println "\n--- Forall Quantifier ---")
  (bench-quantifier-forall)
  (println "\n--- Exists Quantifier ---")
  (bench-quantifier-exists)
  (println "\n--- Nested Quantifiers ---")
  (bench-quantifier-nested))

;;; ---------------------------------------------------------------------------
;;; Full Benchmark Suite
;;; ---------------------------------------------------------------------------

(defn bench-all
  "Runs the complete benchmark suite."
  []
  (println "\n========================================")
  (println "POLIX BENCHMARK SUITE")
  (println "========================================")
  (println "\n### PARSING ###")
  (bench-parse-all)
  (println "\n### COMPILATION ###")
  (bench-compile-all)
  (println "\n### EVALUATION ###")
  (bench-eval-all)
  (println "\n### UNIFY (Residual-Based) ###")
  (bench-unify-all)
  (println "\n### NEGATION ###")
  (bench-negate)
  (println "\n### INVERSE QUERIES ###")
  (bench-inverse-queries)
  (println "\n### OPERATORS ###")
  (bench-operators)
  (println "\n### QUANTIFIERS ###")
  (bench-quantifiers)
  (println "\n========================================")
  (println "BENCHMARK COMPLETE")
  (println "========================================"))

(defn quick-all
  "Runs the complete benchmark suite with quick-bench for faster iteration."
  []
  (binding [*quick* true]
    (bench-all)))

;;; ---------------------------------------------------------------------------
;;; Comparison Utilities
;;; ---------------------------------------------------------------------------

(defn compare-ast-vs-compiled
  "Compares AST evaluation vs compiled evaluation for a policy.
   Returns ratio of AST time to compiled time."
  [policy-expr document]
  (let [ast             (r/unwrap (parser/parse-policy policy-expr))
        check           (compiler/compile-policies [policy-expr])
        ast-result      (with-results #(engine/evaluate ast document))
        compiled-result (with-results #(check document))
        ast-mean        (first (:mean ast-result))
        compiled-mean   (first (:mean compiled-result))]
    {:ast-ns       (* ast-mean 1e9)
     :compiled-ns  (* compiled-mean 1e9)
     :speedup      (/ ast-mean compiled-mean)}))
