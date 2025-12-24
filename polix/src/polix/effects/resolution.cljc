(ns polix.effects.resolution
  "Reference resolution for effects.

  Effects can use symbolic references that are resolved at application time.
  This namespace provides the [[IResolver]] protocol and [[DefaultResolver]]
  implementation for resolving references to concrete values.

  Reference types:
  - Keyword (`:self`, `:target`) - resolved from context bindings
  - State path (`[:state :path :to :value]`) - resolved from state
  - Context path (`[:ctx :bindings :key]`) - resolved from context
  - Param path (`[:param :key]`) - resolved from effect parameters")

;;; ---------------------------------------------------------------------------
;;; Protocol
;;; ---------------------------------------------------------------------------

(defprotocol IResolver
  "Protocol for resolving references in effects.

  A resolver transforms symbolic references into concrete values
  by looking them up in state, context, or parameters."

  (resolve-ref [this ref ctx]
    "Resolves a reference to a concrete value within the given context.

    The context map contains:
    - `:state` - the current application state
    - `:bindings` - map of symbolic bindings (`:self`, `:target`, etc.)
    - `:params` - effect parameters

    Returns the resolved value, or the original ref if not a reference type."))

;;; ---------------------------------------------------------------------------
;;; Built-in Functions
;;; ---------------------------------------------------------------------------

(def builtin-fns
  "Built-in functions available for `:update-in` effects.

  These safe, common functions can be referenced by keyword in effect
  definitions rather than passing actual function references."
  {:+ +
   :- -
   :* *
   :inc inc
   :dec dec
   :not not
   :identity identity
   :conj conj
   :disj disj
   :assoc assoc
   :dissoc dissoc
   :merge merge
   :into into
   :empty empty
   :vec vec
   :set set
   :first first
   :rest rest
   :count count
   :str str})

;;; ---------------------------------------------------------------------------
;;; Function Resolution
;;; ---------------------------------------------------------------------------

(defn resolve-fn
  "Resolves a function reference to an actual function.

  Accepts:
  - A function - returned as-is
  - A keyword - looked up in [[builtin-fns]]
  - A symbol - converted to keyword and looked up

  Returns the resolved function, or nil if not found."
  [f]
  (cond
    (fn? f) f
    (keyword? f) (get builtin-fns f)
    (symbol? f) (get builtin-fns (keyword f))
    :else nil))

;;; ---------------------------------------------------------------------------
;;; Predicate Resolution
;;; ---------------------------------------------------------------------------

(defn resolve-predicate
  "Resolves a predicate specification to a function.

  Accepts:
  - A function - used directly
  - A keyword - returns a function that gets that key from items
  - A map - returns a function that matches all key-value pairs

  Returns a predicate function suitable for use with `filter` or `remove`."
  [pred]
  (cond
    (fn? pred) pred
    (keyword? pred) (fn [item] (get item pred))
    (map? pred) (fn [item] (every? (fn [[k v]] (= (get item k) v)) pred))
    :else (constantly false)))

;;; ---------------------------------------------------------------------------
;;; Default Resolver
;;; ---------------------------------------------------------------------------

(defrecord DefaultResolver []
  IResolver
  (resolve-ref [_ ref ctx]
    (cond
      ;; Keyword - lookup in bindings, return as-is if not found
      (keyword? ref)
      (get-in ctx [:bindings ref] ref)

      ;; [:state :path :to :value]
      (and (vector? ref) (= :state (first ref)))
      (get-in (:state ctx) (rest ref))

      ;; [:ctx :path :to :value]
      (and (vector? ref) (= :ctx (first ref)))
      (get-in ctx (rest ref))

      ;; [:param :key]
      (and (vector? ref) (= :param (first ref)))
      (get-in ctx [:params (second ref)])

      ;; Not a reference - return as-is
      :else ref)))

(def default-resolver
  "The default resolver instance."
  (->DefaultResolver))

;;; ---------------------------------------------------------------------------
;;; Path Resolution
;;; ---------------------------------------------------------------------------

(defn resolve-path
  "Resolves a path, handling references within path segments.

  If the path itself is a reference (starts with `:state`, `:ctx`, or `:param`),
  resolves the entire path. Otherwise, resolves each segment individually."
  [resolver path ctx]
  (if (and (vector? path)
           (#{:state :ctx :param} (first path)))
    (resolve-ref resolver path ctx)
    (mapv #(resolve-ref resolver % ctx) path)))
