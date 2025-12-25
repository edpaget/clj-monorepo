(ns polix.bench.runner
  "CI-compatible benchmark runner with JSON output.

  Runs benchmarks and outputs results as JSON for CI regression detection.

  ## Usage

      clojure -X:bench-ci

  Or with custom output:

      clojure -X:bench-ci :output '\"custom-results.json\"'"
  (:require
   [cheshire.core :as json]
   [criterium.core :as crit]
   [polix.bench.policies :as p]
   [polix.bytecode.class-generator :as bytecode]
   [polix.compiler :as compiler]
   [polix.negate :as negate]
   [polix.operators :as op]
   [polix.optimized.cache :as optimized-cache]
   [polix.optimized.evaluator :as optimized]
   [polix.parser :as parser]
   [polix.unify :as unify])
  (:import
   [java.time Instant]))

;;; ---------------------------------------------------------------------------
;;; Benchmark Execution
;;; ---------------------------------------------------------------------------

(defn run-single
  "Runs a single benchmark and returns results map."
  [f]
  (let [results (crit/quick-benchmark* f {})]
    {:mean-ns   (long (* (first (:mean results)) 1e9))
     :std-dev   (long (* (Math/sqrt (first (:variance results))) 1e9))
     :lower-q   (long (* (first (:lower-q results)) 1e9))
     :upper-q   (long (* (first (:upper-q results)) 1e9))
     :samples   (:sample-count results)
     :gc-count  (:gc-count results)}))

(defmacro bench-fn
  "Benchmarks a form and returns results with name."
  [bench-name expr]
  `{:name ~bench-name
    :results (run-single (fn [] ~expr))})

;;; ---------------------------------------------------------------------------
;;; Benchmark Categories
;;; ---------------------------------------------------------------------------

(defn parse-benchmarks
  "Runs parsing benchmarks."
  []
  [(bench-fn "parse/simple-equality" (parser/parse-policy p/simple-equality))
   (bench-fn "parse/simple-comparison" (parser/parse-policy p/simple-comparison))
   (bench-fn "parse/medium-and" (parser/parse-policy p/medium-and))
   (bench-fn "parse/medium-mixed" (parser/parse-policy p/medium-mixed))
   (bench-fn "parse/complex-nested" (parser/parse-policy p/complex-nested))
   (bench-fn "parse/complex-wide" (parser/parse-policy p/complex-wide))])

(defn compile-benchmarks
  "Runs compilation benchmarks."
  []
  [(bench-fn "compile/simple" (compiler/compile-policies [p/simple-equality]))
   (bench-fn "compile/medium" (compiler/compile-policies [p/medium-and]))
   (bench-fn "compile/complex" (compiler/compile-policies [p/complex-nested]))
   (bench-fn "compile/n1" (compiler/compile-policies (:n1 p/scaling-policies)))
   (bench-fn "compile/n5" (compiler/compile-policies (:n5 p/scaling-policies)))
   (bench-fn "compile/n10" (compiler/compile-policies (:n10 p/scaling-policies)))
   (bench-fn "compile/n50" (compiler/compile-policies (:n50 p/scaling-policies)))])

(defn eval-ast-benchmarks
  "Runs AST evaluation benchmarks."
  []
  (let [simple-ast  @p/simple-equality-ast
        medium-ast  @p/medium-and-ast
        complex-ast @p/complex-nested-ast]
    [(bench-fn "eval-ast/simple-satisfied" (unify/unify simple-ast p/doc-simple-satisfied))
     (bench-fn "eval-ast/simple-contradicted" (unify/unify simple-ast p/doc-simple-contradicted))
     (bench-fn "eval-ast/simple-residual" (unify/unify simple-ast p/doc-empty))
     (bench-fn "eval-ast/medium-satisfied" (unify/unify medium-ast p/doc-medium-satisfied))
     (bench-fn "eval-ast/medium-partial" (unify/unify medium-ast p/doc-medium-partial))
     (bench-fn "eval-ast/complex-satisfied" (unify/unify complex-ast p/doc-complex-satisfied))
     (bench-fn "eval-ast/complex-partial" (unify/unify complex-ast p/doc-complex-partial))]))

(defn eval-compiled-benchmarks
  "Runs compiled evaluation benchmarks."
  []
  (let [simple-check  (compiler/compile-policies [p/simple-equality])
        medium-check  (compiler/compile-policies [p/medium-and])
        complex-check (compiler/compile-policies [p/complex-nested])]
    [(bench-fn "eval-compiled/simple-satisfied" (simple-check p/doc-simple-satisfied))
     (bench-fn "eval-compiled/simple-contradicted" (simple-check p/doc-simple-contradicted))
     (bench-fn "eval-compiled/medium-satisfied" (medium-check p/doc-medium-satisfied))
     (bench-fn "eval-compiled/medium-partial" (medium-check p/doc-medium-partial))
     (bench-fn "eval-compiled/complex-satisfied" (complex-check p/doc-complex-satisfied))]))

(defn operator-benchmarks
  "Runs operator benchmarks."
  []
  (let [ctx     (op/make-context)
        pattern #"admin-\d+"]
    [(bench-fn "operator/equality" (op/eval-in-context ctx {:op := :value "admin"} "admin"))
     (bench-fn "operator/greater-than" (op/eval-in-context ctx {:op :> :value 5} 10))
     (bench-fn "operator/in-set" (op/eval-in-context ctx {:op :in :value #{"a" "b" "c"}} "b"))
     (bench-fn "operator/regex" (op/eval-in-context ctx {:op :matches :value pattern} "admin-123"))]))

(defn conflict-benchmarks
  "Runs conflict-specific benchmarks.

  Measures the performance of conflict residual construction which was
  identified as a bottleneck in the optimized evaluator."
  []
  (let [single-checker (compiler/compile-policies [p/conflict-single-constraint])
        multi-checker  (compiler/compile-policies [p/conflict-multi-constraint])
        nested-checker (compiler/compile-policies [p/conflict-nested-path])
        paths-checker  (compiler/compile-policies [p/conflict-multi-path])]
    [;; Single constraint conflicts
     (bench-fn "conflict/single-satisfied" (single-checker {:role "admin"}))
     (bench-fn "conflict/single-conflict" (single-checker p/doc-conflict-single))
     ;; Multiple constraints on same path
     (bench-fn "conflict/multi-first" (multi-checker p/doc-conflict-multi-first))
     (bench-fn "conflict/multi-last" (multi-checker p/doc-conflict-multi-last))
     (bench-fn "conflict/multi-satisfied" (multi-checker {:role "admin" :level 10 :status "active"}))
     ;; Nested path conflicts
     (bench-fn "conflict/nested-satisfied" (nested-checker {:user {:profile {:role "admin"}}}))
     (bench-fn "conflict/nested-conflict" (nested-checker p/doc-conflict-nested))
     ;; Multiple path conflicts
     (bench-fn "conflict/paths-first" (paths-checker p/doc-conflict-multi-path-first))
     (bench-fn "conflict/paths-last" (paths-checker p/doc-conflict-multi-path-last))
     (bench-fn "conflict/paths-satisfied" (paths-checker {:role "admin" :department "engineering" :status "active"}))]))

(defn quantifier-benchmarks
  "Runs quantifier benchmarks."
  []
  (let [forall-checker (compiler/compile-policies [p/forall-simple])
        forall-nested  (compiler/compile-policies [p/forall-nested-path])
        exists-checker (compiler/compile-policies [p/exists-simple])
        nested-checker (compiler/compile-policies [p/nested-forall-exists])]
    [;; Forall benchmarks
     (bench-fn "quantifier/forall-small-satisfied"
               (forall-checker p/doc-users-5-all-active))
     (bench-fn "quantifier/forall-small-contradicted"
               (forall-checker p/doc-users-5-one-inactive))
     (bench-fn "quantifier/forall-medium-satisfied"
               (forall-nested p/doc-users-20-all-verified))
     (bench-fn "quantifier/forall-large-satisfied"
               (forall-checker p/doc-users-100-all-active))
     ;; Exists benchmarks
     (bench-fn "quantifier/exists-small-satisfied"
               (exists-checker p/doc-users-5-first-admin))
     (bench-fn "quantifier/exists-small-contradicted"
               (exists-checker p/doc-users-5-no-admin))
     (bench-fn "quantifier/exists-large-early-exit"
               (exists-checker p/doc-users-100-first-admin))
     (bench-fn "quantifier/exists-large-late-exit"
               (exists-checker p/doc-users-100-last-admin))
     ;; Nested quantifier benchmarks
     (bench-fn "quantifier/nested-satisfied"
               (nested-checker p/doc-teams-all-have-lead))
     (bench-fn "quantifier/nested-contradicted"
               (nested-checker p/doc-teams-one-missing-lead))]))

(defn count-benchmarks
  "Runs count function benchmarks."
  []
  (let [count-simple-checker  (compiler/compile-policies [p/count-simple])
        count-medium-checker  (compiler/compile-policies [p/count-medium])
        count-large-checker   (compiler/compile-policies [p/count-large])
        count-nested-checker  (compiler/compile-policies [p/count-nested-path])
        count-compare-checker (compiler/compile-policies [p/count-with-comparison])]
    [(bench-fn "count/simple-5-satisfied"
               (count-simple-checker p/doc-users-5-all-active))
     (bench-fn "count/simple-5-contradicted"
               (count-simple-checker {:users (vec (repeat 3 {:active true}))}))
     (bench-fn "count/medium-20-satisfied"
               (count-medium-checker p/doc-users-20-all-verified))
     (bench-fn "count/large-100-satisfied"
               (count-large-checker p/doc-users-100-all-active))
     (bench-fn "count/nested-path"
               (count-nested-checker p/doc-org-with-members))
     (bench-fn "count/with-comparison"
               (count-compare-checker (assoc p/doc-users-5-all-active :active true)))]))

(defn filtered-binding-benchmarks
  "Runs filtered binding benchmarks."
  []
  (let [forall-filtered-checker (compiler/compile-policies [p/forall-filtered])
        exists-filtered-checker (compiler/compile-policies [p/exists-filtered])
        count-filtered-checker  (compiler/compile-policies [p/count-filtered])
        count-complex-checker   (compiler/compile-policies [p/count-filtered-complex])
        nested-filtered-checker (compiler/compile-policies [p/nested-filtered])]
    [;; Forall with filter
     (bench-fn "filtered/forall-small-satisfied"
               (forall-filtered-checker p/doc-users-5-all-active-verified))
     (bench-fn "filtered/forall-small-mixed"
               (forall-filtered-checker p/doc-users-5-mixed-active))
     (bench-fn "filtered/forall-medium"
               (forall-filtered-checker p/doc-users-20-half-active))
     (bench-fn "filtered/forall-large"
               (forall-filtered-checker p/doc-users-100-mostly-active))
     ;; Exists with filter
     (bench-fn "filtered/exists-small-satisfied"
               (exists-filtered-checker p/doc-users-5-active-with-admin))
     (bench-fn "filtered/exists-small-contradicted"
               (exists-filtered-checker p/doc-users-5-active-no-admin))
     (bench-fn "filtered/exists-large-early"
               (exists-filtered-checker p/doc-users-100-active-first-admin))
     (bench-fn "filtered/exists-large-late"
               (exists-filtered-checker p/doc-users-100-active-last-admin))
     ;; Count with filter
     (bench-fn "filtered/count-simple"
               (count-filtered-checker p/doc-users-5-mixed-active))
     (bench-fn "filtered/count-medium"
               (count-filtered-checker p/doc-users-20-half-active))
     (bench-fn "filtered/count-large"
               (count-filtered-checker p/doc-users-100-mostly-active))
     (bench-fn "filtered/count-complex"
               (count-complex-checker p/doc-users-100-mostly-active))
     ;; Nested with filter
     (bench-fn "filtered/nested-satisfied"
               (nested-filtered-checker p/doc-teams-5-active-with-leads))
     (bench-fn "filtered/nested-contradicted"
               (nested-filtered-checker p/doc-teams-5-active-missing-lead))]))

(defn unify-benchmarks
  "Runs unify (residual-based evaluation) benchmarks."
  []
  (let [simple-ast  @p/simple-equality-ast
        medium-ast  @p/medium-and-ast
        complex-ast @p/complex-nested-ast]
    [(bench-fn "unify/simple-satisfied" (unify/unify simple-ast p/doc-simple-satisfied))
     (bench-fn "unify/simple-contradicted" (unify/unify simple-ast p/doc-simple-contradicted))
     (bench-fn "unify/simple-residual" (unify/unify simple-ast p/doc-empty))
     (bench-fn "unify/medium-satisfied" (unify/unify medium-ast p/doc-medium-satisfied))
     (bench-fn "unify/medium-partial" (unify/unify medium-ast p/doc-medium-partial))
     (bench-fn "unify/complex-satisfied" (unify/unify complex-ast p/doc-complex-satisfied))
     (bench-fn "unify/complex-partial" (unify/unify complex-ast p/doc-complex-partial))]))

(defn negate-benchmarks
  "Runs policy negation benchmarks."
  []
  (let [simple-ast  @p/simple-equality-ast
        medium-ast  @p/medium-and-ast
        complex-ast @p/complex-nested-ast]
    [(bench-fn "negate/simple" (negate/negate simple-ast))
     (bench-fn "negate/medium" (negate/negate medium-ast))
     (bench-fn "negate/complex" (negate/negate complex-ast))]))

(defn inverse-query-benchmarks
  "Runs inverse query benchmarks.

  These use the unified residual model:
  - (unify policy {}) → what constraints must a document satisfy?
  - (unify (negate policy) {}) → what constraints would contradict?"
  []
  (let [simple-ast  @p/simple-equality-ast
        medium-ast  @p/medium-and-ast
        complex-ast @p/complex-nested-ast]
    [(bench-fn "inverse/what-satisfies-simple" (unify/unify simple-ast {}))
     (bench-fn "inverse/what-satisfies-medium" (unify/unify medium-ast {}))
     (bench-fn "inverse/what-satisfies-complex" (unify/unify complex-ast {}))
     (bench-fn "inverse/what-contradicts-simple" (unify/unify (negate/negate simple-ast) {}))
     (bench-fn "inverse/what-contradicts-medium" (unify/unify (negate/negate medium-ast) {}))
     (bench-fn "inverse/what-contradicts-complex" (unify/unify (negate/negate complex-ast) {}))]))

(defn optimized-benchmarks
  "Runs optimized evaluator benchmarks.

  Compares optimized-closure policies against interpreted evaluation
  for simple constraint-only policies. Only benchmarks policies that
  are optimized-eligible (no quantifiers or complex nodes)."
  []
  (optimized-cache/clear-cache!)
  (let [merged-simple (:simplified (compiler/merge-policies [p/simple-equality]))
        merged-medium (:simplified (compiler/merge-policies [p/medium-and]))
        ;; Interpreted evaluators for comparison
        int-simple    (compiler/compile-policies [p/simple-equality] {:optimized false})
        int-medium    (compiler/compile-policies [p/medium-and] {:optimized false})]
    ;; Only benchmark optimized-eligible policies
    (if (and (optimized/optimized-eligible? merged-simple)
             (optimized/optimized-eligible? merged-medium))
      (let [opt-simple (optimized/compile-policy merged-simple {})
            opt-medium (optimized/compile-policy merged-medium {})]
        [;; Optimized tier info
         {:name "optimized/tier-simple" :tier (optimized/compilation-tier opt-simple)}
         {:name "optimized/tier-medium" :tier (optimized/compilation-tier opt-medium)}
         ;; Optimized evaluation
         (bench-fn "optimized/simple-satisfied" (opt-simple p/doc-simple-satisfied))
         (bench-fn "optimized/simple-contradicted" (opt-simple p/doc-simple-contradicted))
         (bench-fn "optimized/simple-residual" (opt-simple p/doc-empty))
         (bench-fn "optimized/medium-satisfied" (opt-medium p/doc-medium-satisfied))
         (bench-fn "optimized/medium-partial" (opt-medium p/doc-medium-partial))
         ;; Interpreted comparison (for same policies with optimized disabled)
         (bench-fn "interpreted/simple-satisfied" (int-simple p/doc-simple-satisfied))
         (bench-fn "interpreted/simple-contradicted" (int-simple p/doc-simple-contradicted))
         (bench-fn "interpreted/medium-satisfied" (int-medium p/doc-medium-satisfied))])
      (do
        (println "Warning: Some policies not optimized-eligible, skipping optimized benchmarks")
        []))))

(defn bytecode-benchmarks
  "Runs JVM bytecode (T3) vs closure (T2) comparison benchmarks.

  Compares bytecode-compiled policies against optimized Clojure closures
  for bytecode-eligible policies (simple constraints, no quantifiers)."
  []
  (let [merged-simple (:simplified (compiler/merge-policies [p/simple-equality]))
        merged-medium (:simplified (compiler/merge-policies [p/medium-and]))]
    ;; Only benchmark bytecode-eligible policies
    (if (and (bytecode/bytecode-eligible? merged-simple)
             (bytecode/bytecode-eligible? merged-medium))
      (let [;; T3 bytecode compiled
            bc-simple (bytecode/generate-policy-class merged-simple)
            bc-medium (bytecode/generate-policy-class merged-medium)
            ;; T2 closure compiled
            t2-simple (optimized/compile-policy merged-simple {:tier :t2})
            t2-medium (optimized/compile-policy merged-medium {:tier :t2})]
        [;; Simple policy - bytecode (T3)
         (bench-fn "bytecode/simple-satisfied" (bc-simple p/doc-simple-satisfied))
         (bench-fn "bytecode/simple-contradicted" (bc-simple p/doc-simple-contradicted))
         (bench-fn "bytecode/simple-residual" (bc-simple p/doc-empty))
         ;; Medium policy - bytecode (T3)
         (bench-fn "bytecode/medium-satisfied" (bc-medium p/doc-medium-satisfied))
         (bench-fn "bytecode/medium-partial" (bc-medium p/doc-medium-partial))
         ;; Simple policy - closure (T2) for comparison
         (bench-fn "closure/simple-satisfied" (optimized/evaluate t2-simple p/doc-simple-satisfied))
         (bench-fn "closure/simple-contradicted" (optimized/evaluate t2-simple p/doc-simple-contradicted))
         (bench-fn "closure/simple-residual" (optimized/evaluate t2-simple p/doc-empty))
         ;; Medium policy - closure (T2) for comparison
         (bench-fn "closure/medium-satisfied" (optimized/evaluate t2-medium p/doc-medium-satisfied))
         (bench-fn "closure/medium-partial" (optimized/evaluate t2-medium p/doc-medium-partial))])
      (do
        (println "Warning: Some policies not bytecode-eligible, skipping bytecode benchmarks")
        []))))

;;; ---------------------------------------------------------------------------
;;; Regression Detection
;;; ---------------------------------------------------------------------------

(defn check-regression
  "Compares current results against baseline.
   Returns list of regressions exceeding threshold."
  [baseline current threshold]
  (let [baseline-map (into {} (map (juxt :name identity) baseline))
        current-map  (into {} (map (juxt :name identity) current))]
    (for [[name result] current-map
          :let          [base (get baseline-map name)
                         current-mean (get-in result [:results :mean-ns])
                         base-mean (when base (get-in base [:results :mean-ns]))
                         delta (when base-mean
                                 (double (/ (- current-mean base-mean) base-mean)))]
          :when         (and delta (> delta threshold))]
      {:name name
       :baseline-ns base-mean
       :current-ns current-mean
       :regression-pct (* 100 delta)})))

(defn load-baseline
  "Loads baseline results from a JSON file."
  [path]
  (try
    (let [data (json/parse-string (slurp path) true)]
      (:benchmarks data))
    (catch Exception _
      nil)))

;;; ---------------------------------------------------------------------------
;;; CI Runner
;;; ---------------------------------------------------------------------------

(defn run-all-benchmarks
  "Runs all benchmark categories."
  []
  (println "Running benchmarks...")
  (print "  Parsing...") (flush)
  (let [parse-results (parse-benchmarks)]
    (println " done")
    (print "  Compilation...") (flush)
    (let [compile-results (compile-benchmarks)]
      (println " done")
      (print "  AST Evaluation...") (flush)
      (let [ast-results (eval-ast-benchmarks)]
        (println " done")
        (print "  Compiled Evaluation...") (flush)
        (let [compiled-results (eval-compiled-benchmarks)]
          (println " done")
          (print "  Unify...") (flush)
          (let [unify-results (unify-benchmarks)]
            (println " done")
            (print "  Negation...") (flush)
            (let [negate-results (negate-benchmarks)]
              (println " done")
              (print "  Inverse Queries...") (flush)
              (let [inverse-results (inverse-query-benchmarks)]
                (println " done")
                (print "  Operators...") (flush)
                (let [operator-results (operator-benchmarks)]
                  (println " done")
                  (print "  Quantifiers...") (flush)
                  (let [quantifier-results (quantifier-benchmarks)]
                    (println " done")
                    (print "  Count...") (flush)
                    (let [count-results (count-benchmarks)]
                      (println " done")
                      (print "  Filtered Bindings...") (flush)
                      (let [filtered-results (filtered-binding-benchmarks)]
                        (println " done")
                        (print "  Optimized...") (flush)
                        (let [optimized-results (optimized-benchmarks)]
                          (println " done")
                          (print "  Conflicts...") (flush)
                          (let [conflict-results (conflict-benchmarks)]
                            (println " done")
                            (print "  Bytecode...") (flush)
                            (let [bytecode-results (bytecode-benchmarks)]
                              (println " done")
                              (concat parse-results
                                      compile-results
                                      ast-results
                                      compiled-results
                                      unify-results
                                      negate-results
                                      inverse-results
                                      operator-results
                                      quantifier-results
                                      count-results
                                      filtered-results
                                      optimized-results
                                      conflict-results
                                      bytecode-results))))))))))))))))

(defn run-ci
  "Main entry point for CI benchmark runner.

   Options:
   - `:output` - JSON output file path (default: \"benchmark-results.json\")
   - `:baseline` - Baseline file for regression detection (optional)
   - `:threshold` - Regression threshold as decimal (default: 0.1 = 10%)"
  [{:keys [output baseline threshold]
    :or {output "benchmark-results.json"
         threshold 0.1}}]
  (println "Polix Benchmark Runner")
  (println "======================")
  (let [benchmarks (run-all-benchmarks)
        result     {:timestamp  (.toString (Instant/now))
                    :benchmarks (vec benchmarks)}]
    (spit output (json/generate-string result {:pretty true}))
    (println (str "\nResults written to: " output))

    (when baseline
      (println (str "\nChecking against baseline: " baseline))
      (if-let [baseline-data (load-baseline baseline)]
        (let [regressions (check-regression baseline-data benchmarks threshold)]
          (if (seq regressions)
            (do
              (println (str "\nWARNING: " (count regressions) " regression(s) detected (>" (* 100 threshold) "%):\n"))
              (doseq [{:keys [name baseline-ns current-ns regression-pct]} regressions]
                (println (format "  %s: %dns -> %dns (+%.1f%%)"
                                 name baseline-ns current-ns regression-pct)))
              (System/exit 1))
            (println "\nNo regressions detected.")))
        (println "WARNING: Could not load baseline file")))

    (println "\nBenchmark summary:")
    (doseq [{:keys [name results]} benchmarks]
      (println (format "  %-40s %,10d ns (std: %,d)"
                       name
                       (:mean-ns results)
                       (:std-dev results))))))

;;; ---------------------------------------------------------------------------
;;; Quick Development Runner
;;; ---------------------------------------------------------------------------

(defn quick
  "Quick benchmark run for development - prints results to console."
  []
  (run-ci {:output "benchmark-results.json"}))
