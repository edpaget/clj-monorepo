(ns polix.optimized.evaluator
  "Optimized policy evaluator using pre-computed templates.

  Creates fast evaluation functions that avoid AST traversal by
  pre-computing residual templates at compile time. This is a
  cross-platform (CLJ/CLJS) implementation using Clojure closures."
  (:require
   [polix.operators :as op]
   [polix.optimized.analyzer :as analyzer]
   [polix.optimized.templates :as templates]))

(defprotocol ICompiledPolicy
  "Protocol for compiled policy evaluators."
  (evaluate [this document]
    "Evaluates the policy against a document.
    Returns {} for satisfied, {:path [constraints]} for open/conflict.")
  (compilation-tier [this]
    "Returns the compilation tier (:t0, :t1, or :t2).")
  (compiled-version [this]
    "Returns the registry version at compile time."))

(def ^:private satisfied-result
  "The satisfied result - an empty map."
  {})

(defn- get-in-doc
  "Gets a value from a document using a path vector.
  Path is a vector of keywords like [:user :role]."
  [document path]
  (reduce (fn [acc k] (when acc (get acc k))) document path))

(defn- check-path-constraints
  "Checks all constraint evaluators for a path against a document value.

  Returns nil if all constraints pass, or the conflict result if one fails."
  [constraint-evaluators doc-value]
  (loop [idx 0]
    (if (>= idx (count constraint-evaluators))
      nil
      (let [cev (nth constraint-evaluators idx)
            op-fn (op/get-operator (:op cev))]
        (if (op/eval op-fn doc-value (:value cev))
          (recur (inc idx))
          ((:make-conflict cev) doc-value))))))

(defn create-tier2-evaluator
  "Creates a Tier 2 (optimized) policy evaluator.

  Takes a constraint set and returns a function that evaluates documents.
  All operators must be built-in for Tier 2 compilation.

  Pre-computes open residuals and per-constraint conflict makers at creation
  time, so evaluation only needs to:
  1. Look up document values
  2. Check constraints in a tight loop
  3. Return pre-computed residuals on failure"
  [constraint-set]
  (let [ts (templates/extract-templates constraint-set)
        path-templates (:path-templates ts)]
    (fn evaluator [document]
      (loop [idx 0]
        (if (>= idx (count path-templates))
          satisfied-result
          (let [{:keys [path open constraint-evaluators]} (nth path-templates idx)
                doc-value (get-in-doc document path)]
            (if (nil? doc-value)
              ;; Value missing: return pre-computed open residual
              open
              ;; Value present: check constraints
              (if-let [conflict (check-path-constraints constraint-evaluators doc-value)]
                conflict
                (recur (inc idx))))))))))

(defn create-tier1-evaluator
  "Creates a Tier 1 (guarded) policy evaluator.

  Takes a constraint set and returns a function that evaluates documents.
  Includes version guards for custom operators with fallback to Tier 0."
  [constraint-set tier0-evaluator]
  (let [compiled-version (op/registry-version)
        tier2-evaluator (create-tier2-evaluator constraint-set)]
    (fn evaluator [document]
      (if (= compiled-version (op/registry-version))
        (tier2-evaluator document)
        (tier0-evaluator document)))))

(defrecord CompiledPolicy [eval-fn fallback-fn tier version]
  ICompiledPolicy
  (evaluate [_ document]
    (eval-fn document))
  (compilation-tier [_]
    tier)
  (compiled-version [_]
    version)

  #?@(:clj
      [clojure.lang.IFn
       (invoke [_ document]
         (eval-fn document))
       (invoke [_ document opts]
         (if fallback-fn
           (fallback-fn document opts)
           (throw (ex-info "No fallback evaluator for options evaluation" {:opts opts}))))
       (applyTo [_ args]
         (case (count args)
           1 (eval-fn (first args))
           2 (if fallback-fn
               (fallback-fn (first args) (second args))
               (throw (ex-info "No fallback evaluator for options evaluation" {:opts (second args)})))
           (throw (ex-info "Wrong number of args" {:count (count args)}))))]
      :cljs
      [IFn
       (-invoke [_ document]
         (eval-fn document))
       (-invoke [_ document opts]
         (if fallback-fn
           (fallback-fn document opts)
           (throw (ex-info "No fallback evaluator for options evaluation" {:opts opts}))))]))

(defn compile-policy
  "Compiles a constraint set to an optimized evaluator.

  Takes a constraint set (output of compiler/normalize-and-merge) and
  optional options map. Returns an ICompiledPolicy that can be called
  as a function.

  Options:
  - :tier - force a specific tier (:t0, :t1, :t2)
  - :fallback - fallback evaluator for Tier 1 (required if custom ops present)

  Returns a CompiledPolicy record implementing IFn."
  ([constraint-set]
   (compile-policy constraint-set {}))
  ([constraint-set opts]
   (let [analysis (analyzer/analyze-constraint-set constraint-set)
         tier (or (:tier opts) (analyzer/select-tier analysis))
         version (op/registry-version)
         fallback-fn (:fallback opts)]
     (case tier
       :t2
       (->CompiledPolicy (create-tier2-evaluator constraint-set) fallback-fn :t2 version)

       :t1
       (let [tier0-fallback (or fallback-fn
                                (create-tier2-evaluator constraint-set))]
         (->CompiledPolicy (create-tier1-evaluator constraint-set tier0-fallback)
                           fallback-fn :t1 version))

       :t0
       (if fallback-fn
         (->CompiledPolicy fallback-fn fallback-fn :t0 version)
         (throw (ex-info "Tier 0 requires a fallback evaluator"
                         {:constraint-set constraint-set
                          :analysis analysis})))))))

(defn optimized-eligible?
  "Returns true if the constraint set is eligible for optimized evaluation.

  A constraint set is eligible if:
  - It has no complex (non-constraint) nodes
  - All operators are either built-in or have a fallback evaluator"
  [constraint-set]
  (let [analysis (analyzer/analyze-constraint-set constraint-set)]
    (not (:has-complex analysis))))
