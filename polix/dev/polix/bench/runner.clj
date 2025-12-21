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
   [polix.compiler :as compiler]
   [polix.engine :as engine]
   [polix.operators :as op]
   [polix.parser :as parser])
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
    [(bench-fn "eval-ast/simple-satisfied" (engine/evaluate simple-ast p/doc-simple-satisfied))
     (bench-fn "eval-ast/simple-contradicted" (engine/evaluate simple-ast p/doc-simple-contradicted))
     (bench-fn "eval-ast/simple-residual" (engine/evaluate simple-ast p/doc-empty))
     (bench-fn "eval-ast/medium-satisfied" (engine/evaluate medium-ast p/doc-medium-satisfied))
     (bench-fn "eval-ast/medium-partial" (engine/evaluate medium-ast p/doc-medium-partial))
     (bench-fn "eval-ast/complex-satisfied" (engine/evaluate complex-ast p/doc-complex-satisfied))
     (bench-fn "eval-ast/complex-partial" (engine/evaluate complex-ast p/doc-complex-partial))]))

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
                  (concat parse-results
                          compile-results
                          ast-results
                          compiled-results
                          operator-results
                          quantifier-results
                          count-results
                          filtered-results))))))))))

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
