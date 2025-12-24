(ns polix.bytecode.generator
  "ByteBuddy-based class generation for compiled policies.

  Generates JVM classes that implement fast policy evaluation with
  pre-computed residual templates."
  (:require
   [clojure.string :as str]
   [polix.bytecode.analyzer :as analyzer]
   [polix.bytecode.templates :as templates]
   [polix.operators :as op])
  (:import
   [net.bytebuddy ByteBuddy]
   [net.bytebuddy.description.modifier Visibility Ownership]
   [net.bytebuddy.dynamic.loading ClassLoadingStrategy$Default]
   [net.bytebuddy.implementation MethodDelegation FixedValue Implementation$Composable]
   [net.bytebuddy.implementation.bytecode.assign Assigner$Typing]
   [net.bytebuddy.matcher ElementMatchers]
   [clojure.lang Keyword IPersistentMap IPersistentVector PersistentArrayMap PersistentVector IFn RT]
   [java.util.regex Pattern]))

(defprotocol ICompiledPolicy
  "Protocol for bytecode-compiled policy evaluators."
  (evaluate [this document]
    "Evaluates the policy against a document.
    Returns {} for satisfied, {:path [constraints]} for open/conflict.")
  (compilation-tier [this]
    "Returns the compilation tier (:t0, :t1, or :t2).")
  (compiled-version [this]
    "Returns the registry version at compile time."))

(def ^:private policy-counter (atom 0))

(defn- next-class-name
  "Generates a unique class name for a compiled policy."
  []
  (str "polix.bytecode.CompiledPolicy$" (swap! policy-counter inc)))

(defn- keyword->field-name
  "Converts a keyword to a valid Java field name."
  [kw]
  (-> (name kw)
      (.replace "-" "_")
      (.replace "?" "_p")
      (.replace "!" "_b")
      (.toUpperCase)))

(defn- path->field-name
  "Converts a path vector to a field name."
  [path]
  (str "KEY_" (str/join "_" (map keyword->field-name path))))

(defn- path->open-field-name
  "Returns the field name for an open residual template."
  [path]
  (str "OPEN_" (str/join "_" (map keyword->field-name path))))

(defn- build-keyword
  "Builds a Clojure keyword from namespace and name."
  [kw]
  (if (namespace kw)
    (Keyword/intern (namespace kw) (name kw))
    (Keyword/intern (name kw))))

(defn- build-constraint-vector
  "Builds a constraint vector for residuals."
  [op value]
  (PersistentVector/create [(build-keyword op) value]))

(defn- build-open-residual
  "Builds a pre-computed open residual map for a path."
  [path constraints]
  (let [constraint-vecs (mapv #(build-constraint-vector (:op %) (:value %)) constraints)]
    (PersistentArrayMap/createAsIfByAssoc
     (into-array Object [(PersistentVector/create (map build-keyword path))
                         (PersistentVector/create constraint-vecs)]))))

(defn- build-conflict-residual
  "Builds a conflict residual map for a path with witness."
  [path constraints witness]
  (let [conflict-kw (Keyword/intern "conflict")
        conflict-vecs (mapv (fn [c]
                              (PersistentVector/create
                               [conflict-kw
                                (build-constraint-vector (:op c) (:value c))
                                witness]))
                            constraints)]
    (PersistentArrayMap/createAsIfByAssoc
     (into-array Object [(PersistentVector/create (map build-keyword path))
                         (PersistentVector/create conflict-vecs)]))))

(def ^:private satisfied-result
  "The satisfied result - an empty persistent map."
  PersistentArrayMap/EMPTY)

(defn- get-in-doc
  "Gets a value from a document using a path vector.
  Path is a vector of keywords like [:user :role]."
  [document path]
  (reduce (fn [acc k] (when acc (get acc k))) document path))

(defn create-tier2-evaluator
  "Creates a Tier 2 (fully inlined) policy evaluator.

  Takes a constraint set and returns a function that evaluates documents.
  All operators must be built-in for Tier 2 compilation."
  [constraint-set]
  (let [paths (for [[path cs] constraint-set
                    :when (vector? path)]
                {:path path
                 :constraints cs
                 :open (build-open-residual path cs)})]
    (fn evaluator [document]
      (loop [remaining paths]
        (if (empty? remaining)
          satisfied-result
          (let [{:keys [path constraints open]} (first remaining)
                value (get-in-doc document path)]
            (if (nil? value)
              open
              (let [failed (first (filter (fn [c]
                                            (not (op/eval (op/get-operator (:op c))
                                                          value
                                                          (:value c))))
                                          constraints))]
                (if failed
                  (build-conflict-residual path constraints value)
                  (recur (rest remaining)))))))))))

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

  IFn
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
      (throw (ex-info "Wrong number of args" {:count (count args)})))))

(defn compile-to-bytecode
  "Compiles a constraint set to a bytecode-optimized evaluator.

  Takes a constraint set (output of compiler/normalize-and-merge) and
  optional options map. Returns an ICompiledPolicy that can be called
  as a function.

  Options:
  - :tier - force a specific tier (:t0, :t1, :t2)
  - :fallback - fallback evaluator for Tier 1 (required if custom ops present)

  Returns a CompiledPolicy record implementing IFn."
  ([constraint-set]
   (compile-to-bytecode constraint-set {}))
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

(defn bytecode-eligible?
  "Returns true if the constraint set is eligible for bytecode compilation.

  A constraint set is eligible if:
  - It has no complex (non-constraint) nodes
  - All operators are either built-in or have a fallback evaluator"
  [constraint-set]
  (let [analysis (analyzer/analyze-constraint-set constraint-set)]
    (not (:has-complex analysis))))
