(ns polix.collection-ops
  "Extensible collection operator system for quantifiers and aggregations.

  Collection operators define how policies traverse and aggregate over
  collections. This includes quantifiers like `:forall` and `:exists`,
  as well as aggregations like `:count`, `:sum`, `:avg`, etc.

  ## Built-in Collection Operators

  Quantifiers: `:forall`, `:exists`
  Aggregations: `:count`, `:sum`

  ## Defining Custom Collection Operators

  Use `defcollectionop` to define new operators:

      (defcollectionop :every
        :op-type :quantifier
        :empty-result true
        :init-state (fn [] {})
        :process-element (fn [state _elem result _idx]
                           (if (false? result)
                             {:short-circuit false}
                             {:state state}))
        :finalize (fn [_state residuals]
                    (if (empty? residuals) true {:residual residuals})))

  Or use `register-collection-op!` for programmatic registration."
  (:refer-clojure :exclude [eval])
  #?(:clj (:require [clojure.spec.alpha :as s])
     :cljs (:require [cljs.spec.alpha :as s])))

;;; ---------------------------------------------------------------------------
;;; Collection Operator Protocol
;;; ---------------------------------------------------------------------------

(defprotocol ICollectionOp
  "Protocol for collection-based operations (quantifiers and aggregations).

  Collection operators traverse collections with optional filtering and
  accumulate results using the protocol methods. The traversal function
  handles binding context, filter evaluation, and residual tracking."

  (op-type [this]
    "Returns the operator type: `:quantifier` or `:aggregation`.

    Quantifiers evaluate a body predicate for each element (forall, exists).
    Aggregations extract values from elements (count, sum, avg).")

  (empty-result [this]
    "Returns the result for an empty collection after filtering.

    Examples:
    - `:forall` returns `true` (vacuous truth)
    - `:exists` returns `false`
    - `:count` returns `0`
    - `:sum` returns `0`")

  (init-state [this]
    "Returns the initial accumulator state for the operation.

    The state is an arbitrary map that gets threaded through
    `process-element` calls and passed to `finalize`.")

  (process-element [this state elem-value body-result index]
    "Processes a single element into the accumulator.

    Takes:
    - `state` - current accumulator state
    - `elem-value` - the element itself (for aggregations like sum)
    - `body-result` - result of body predicate (for quantifiers) or filter
    - `index` - element index in collection

    Returns one of:
    - `{:state new-state}` - continue with updated state
    - `{:short-circuit result}` - stop iteration and return result")

  (finalize [this state residuals]
    "Finalizes the accumulated state into the final result.

    Called when all elements have been processed without short-circuit.
    The `residuals` map contains any residual constraints from elements
    that couldn't be fully evaluated."))

;;; ---------------------------------------------------------------------------
;;; Tracing Protocol
;;; ---------------------------------------------------------------------------

(defprotocol ICollectionOpTrace
  "Protocol for tracing collection operator evaluation.

  Operators can optionally implement this protocol to provide
  detailed trace information during evaluation."

  (trace-start [this binding ctx]
    "Called when traversal starts. Returns updated ctx with trace entry.")

  (trace-element [this ctx elem idx filter-result body-result]
    "Called for each element processed. Appends element trace if enabled.")

  (trace-end [this ctx result trace-entry]
    "Called when traversal completes. Finalizes trace entry."))

;;; ---------------------------------------------------------------------------
;;; Simplification Protocol (Optional)
;;; ---------------------------------------------------------------------------

(defprotocol ICollectionOpSimplify
  "Optional protocol for compile-time simplification of collection operators.

  Implement this protocol to enable the compiler to optimize policies
  containing collection operators."

  (can-merge? [this other-op-key]
    "Returns true if this operator can be merged with another on the same path.

    Example: Two `:forall` operators on the same collection can be merged
    into a single forall with an AND-ed body.")

  (merge-bodies [this body1 body2]
    "Merges two body ASTs when operators are combined.

    For `:forall`, this typically means AND-ing the bodies.
    For `:exists`, this typically means OR-ing the bodies.")

  (simplify-comparison [this comparison-op expected-value]
    "Simplifies when the collection op result is used in a comparison.

    Returns nil if no simplification is possible, otherwise returns
    a simplified AST node.

    Example: `[:> [:fn/count :doc/users] 0]` can simplify to
    `[:exists [_ :doc/users] true]`"))

;;; ---------------------------------------------------------------------------
;;; Operator Spec (Validation)
;;; ---------------------------------------------------------------------------

(s/def ::op-type #{:quantifier :aggregation})
(s/def ::empty-result any?)
(s/def ::init-state fn?)
(s/def ::process-element fn?)
(s/def ::finalize fn?)

(s/def ::collection-op-spec
  (s/keys :req-un [::op-type ::empty-result ::init-state ::process-element ::finalize]))

(defn validate-collection-op-spec!
  "Validates collection operator spec against schema, throws on invalid."
  [op-key spec]
  (when-not (s/valid? ::collection-op-spec spec)
    (throw (ex-info "Invalid collection operator specification"
                    {:op-key op-key
                     :spec spec
                     :problems (s/explain-data ::collection-op-spec spec)}))))

;;; ---------------------------------------------------------------------------
;;; Default Collection Operator Implementation
;;; ---------------------------------------------------------------------------

(defrecord CollectionOp [op-key
                         op-type-val
                         empty-result-val
                         init-state-fn
                         process-element-fn
                         finalize-fn
                         can-merge-fn
                         merge-bodies-fn
                         simplify-comparison-fn]
  ICollectionOp
  (op-type [_] op-type-val)
  (empty-result [_] empty-result-val)
  (init-state [_] (init-state-fn))
  (process-element [_ state elem body-result idx]
    (process-element-fn state elem body-result idx))
  (finalize [_ state residuals]
    (finalize-fn state residuals))

  ICollectionOpTrace
  (trace-start [_ binding ctx]
    (if (:trace? ctx)
      (let [entry {:op op-key
                   :path (:path binding)
                   :elements-processed 0
                   :filter-excluded 0
                   :residuals 0
                   :short-circuited? false}]
        (assoc ctx ::current-trace entry))
      ctx))

  (trace-element [_ ctx _elem _idx filter-result body-result]
    (if-let [entry (::current-trace ctx)]
      (let [updated (cond-> entry
                      true (update :elements-processed inc)
                      (= :exclude filter-result) (update :filter-excluded inc)
                      (map? body-result) (update :residuals inc))]
        (assoc ctx ::current-trace updated))
      ctx))

  (trace-end [_ ctx result trace-entry]
    (when (and (:trace? ctx) (:trace ctx))
      (let [final-entry (assoc (or trace-entry (::current-trace ctx))
                               :result result
                               :short-circuited? (contains? #{true false} result))]
        (swap! (:trace ctx) conj final-entry)))
    ctx)

  ICollectionOpSimplify
  (can-merge? [_ other-op-key]
    (if can-merge-fn
      (can-merge-fn other-op-key)
      false))

  (merge-bodies [_ body1 body2]
    (if merge-bodies-fn
      (merge-bodies-fn body1 body2)
      nil))

  (simplify-comparison [_ comparison-op expected-value]
    (if simplify-comparison-fn
      (simplify-comparison-fn comparison-op expected-value)
      nil)))

;;; ---------------------------------------------------------------------------
;;; Collection Operator Registry
;;; ---------------------------------------------------------------------------

(defonce ^:private registry (atom {}))

(defn register-collection-op!
  "Registers a collection operator in the global registry.

  `op-key` is the operator keyword (e.g., `:forall`, `:count`).

  `spec` is a map with required and optional keys:
  - `:op-type` - (required) `:quantifier` or `:aggregation`
  - `:empty-result` - (required) result for empty collection
  - `:init-state` - (required) `(fn [] -> state)`
  - `:process-element` - (required) `(fn [state elem result idx] -> {:state ...} | {:short-circuit ...})`
  - `:finalize` - (required) `(fn [state residuals] -> result)`
  - `:can-merge?` - (optional) `(fn [other-op-key] -> boolean)`
  - `:merge-bodies` - (optional) `(fn [body1 body2] -> merged-body)`
  - `:simplify-comparison` - (optional) `(fn [comp-op expected] -> ast | nil)`

  Throws if spec is invalid."
  [op-key spec]
  (validate-collection-op-spec! op-key spec)
  (let [operator (->CollectionOp
                  op-key
                  (:op-type spec)
                  (:empty-result spec)
                  (:init-state spec)
                  (:process-element spec)
                  (:finalize spec)
                  (:can-merge? spec)
                  (:merge-bodies spec)
                  (:simplify-comparison spec))]
    (swap! registry assoc op-key operator)
    operator))

(defn get-collection-op
  "Returns the collection operator for `op-key`, or nil if not found."
  [op-key]
  (get @registry op-key))

(defn collection-op-keys
  "Returns all registered collection operator keys."
  []
  (keys @registry))

(defn clear-registry!
  "Clears all registered collection operators. Useful for testing."
  []
  (reset! registry {}))

;;; ---------------------------------------------------------------------------
;;; Macro for Defining Collection Operators
;;; ---------------------------------------------------------------------------

(defmacro defcollectionop
  "Defines and registers a collection operator.

  Example:

      (defcollectionop :every
        :op-type :quantifier
        :empty-result true
        :init-state (fn [] {})
        :process-element (fn [state _elem result _idx]
                           (if (false? result)
                             {:short-circuit false}
                             {:state state}))
        :finalize (fn [_state residuals]
                    (if (empty? residuals) true {:residual residuals})))"
  [op-key & {:keys [op-type empty-result init-state process-element finalize
                    can-merge? merge-bodies simplify-comparison]}]
  `(register-collection-op!
    ~op-key
    (cond-> {:op-type ~op-type
             :empty-result ~empty-result
             :init-state ~init-state
             :process-element ~process-element
             :finalize ~finalize}
      ~can-merge? (assoc :can-merge? ~can-merge?)
      ~merge-bodies (assoc :merge-bodies ~merge-bodies)
      ~simplify-comparison (assoc :simplify-comparison ~simplify-comparison))))

;;; ---------------------------------------------------------------------------
;;; Residual Helpers
;;; ---------------------------------------------------------------------------

(defn residual?
  "Returns true if x is a residual result."
  [x]
  (and (map? x) (contains? x :residual)))

(defn index-residual
  "Transforms residual paths to include collection index.

  Takes a residual result and prefixes all paths with the collection path
  and element index, e.g., `{[:role] [...]}` becomes `{[:users 0 :role] [...]}`."
  [residual-result coll-path index]
  (let [indexed-path (conj coll-path index)]
    {:residual
     (into {}
           (map (fn [[path constraints]]
                  [(vec (concat indexed-path path)) constraints])
                (:residual residual-result)))}))

(defn merge-residual-paths
  "Merges residual path maps from multiple residual results."
  [acc residual-result]
  (merge-with into acc (:residual residual-result)))

;;; ---------------------------------------------------------------------------
;;; Collection Resolution
;;; ---------------------------------------------------------------------------

(defn resolve-collection
  "Resolves the collection for a quantifier binding.

  Returns `{:ok collection}` if found, or `{:missing path}` if the collection
  path doesn't exist, or `{:invalid value}` if the value is not sequential.

  The `get-binding-fn` should be `(fn [ctx name] -> value)` for looking up
  bound variables in the evaluation context."
  [binding document ctx get-binding-fn path-exists-fn]
  (let [{:keys [namespace path]} binding
        source                   (if (= "doc" namespace)
                                   document
                                   (get-binding-fn ctx (keyword namespace)))]
    (cond
      (nil? source)
      {:missing path}

      (not (path-exists-fn source path))
      {:missing path}

      :else
      (let [coll (get-in source path)]
        (if (sequential? coll)
          {:ok coll}
          {:invalid coll})))))

;;; ---------------------------------------------------------------------------
;;; Collection Traversal
;;; ---------------------------------------------------------------------------

(defn traverse-collection
  "Generic collection traversal for quantifiers and aggregations.

  This is the core iteration function that handles:
  - Collection resolution from binding
  - Optional filter evaluation (`:where` clause)
  - Body evaluation (for quantifiers)
  - Binding context management
  - Residual tracking with indexed paths
  - Short-circuit returns
  - Tracing

  Parameters:
  - `coll-op` - the collection operator (implements ICollectionOp)
  - `binding` - binding map with `:namespace`, `:path`, `:name`, `:where`
  - `body` - body AST node (for quantifiers) or nil (for aggregations)
  - `document` - the document being evaluated
  - `ctx` - evaluation context with bindings and trace info
  - `eval-ast-fn` - `(fn [ast document ctx] -> result)` for evaluating AST
  - `with-binding-fn` - `(fn [ctx name value] -> ctx)` for adding bindings
  - `get-binding-fn` - `(fn [ctx name] -> value)` for getting bindings
  - `path-exists-fn` - `(fn [doc path] -> boolean)` for path existence check

  Returns three-valued result: true, false, value, or {:residual ...}."
  [coll-op binding body document ctx
   {:keys [eval-ast-fn with-binding-fn get-binding-fn path-exists-fn]}]
  (let [coll-result               (resolve-collection binding document ctx get-binding-fn path-exists-fn)
        {:keys [name path where]} binding
        op-key                    (:op-key coll-op)]

    ;; Start tracing
    (let [ctx (if (satisfies? ICollectionOpTrace coll-op)
                (trace-start coll-op binding ctx)
                ctx)]

      (cond
        ;; Collection missing - return residual
        (:missing coll-result)
        (let [result {:residual {path [[op-key binding body]]}}]
          (when (satisfies? ICollectionOpTrace coll-op)
            (trace-end coll-op ctx result (::current-trace ctx)))
          result)

        ;; Invalid collection type - return false for quantifiers, empty-result for aggregations
        (:invalid coll-result)
        (let [result (if (= :quantifier (op-type coll-op))
                       false
                       (empty-result coll-op))]
          (when (satisfies? ICollectionOpTrace coll-op)
            (trace-end coll-op ctx result (::current-trace ctx)))
          result)

        :else
        (let [coll (:ok coll-result)]
          (if (empty? coll)
            ;; Empty collection - return empty result
            (let [result (empty-result coll-op)]
              (when (satisfies? ICollectionOpTrace coll-op)
                (trace-end coll-op ctx result (::current-trace ctx)))
              result)

            ;; Fast path: count without filter uses native count (O(1) for vectors)
            (if (and (= :count op-key) (nil? where) (nil? body))
              (let [result (count coll)]
                (when (satisfies? ICollectionOpTrace coll-op)
                  (trace-end coll-op ctx result (::current-trace ctx)))
                result)

              ;; Iterate over collection
              (loop [elements    (seq coll)
                     index       0
                     state       (init-state coll-op)
                     residuals   {}
                     current-ctx ctx]
                (if (empty? elements)
                ;; All elements processed - finalize
                  (let [result (finalize coll-op state residuals)]
                    (when (satisfies? ICollectionOpTrace coll-op)
                      (trace-end coll-op current-ctx result (::current-trace current-ctx)))
                    result)

                  (let [elem     (first elements)
                        elem-ctx (with-binding-fn current-ctx name elem)]

                  ;; Evaluate filter if present
                    (let [filter-result (if where
                                          (let [r (eval-ast-fn where document elem-ctx)]
                                            (cond
                                              (true? r) :include
                                              (false? r) :exclude
                                              (residual? r) r
                                              r :include
                                              :else :exclude))
                                          :include)]

                      (cond
                      ;; Element excluded by filter
                        (= :exclude filter-result)
                        (let [traced-ctx (if (satisfies? ICollectionOpTrace coll-op)
                                           (trace-element coll-op current-ctx elem index :exclude nil)
                                           current-ctx)]
                          (recur (rest elements) (inc index) state residuals traced-ctx))

                      ;; Filter has residual
                        (residual? filter-result)
                        (if (= :quantifier (op-type coll-op))
                        ;; For quantifiers, evaluate body to see if it matters
                          (let [body-result    (eval-ast-fn body document elem-ctx)
                                process-result (process-element coll-op state elem body-result index)]
                            (if (contains? process-result :short-circuit)
                            ;; Short circuit - but we have filter residual
                            ;; Record the residual since filter resolution might change outcome
                              (let [short-circuit   (:short-circuit process-result)
                                    indexed         (index-residual filter-result path index)
                                    final-residuals (merge-residual-paths residuals indexed)
                                    result          (if (empty? final-residuals)
                                                      short-circuit
                                                      {:residual final-residuals})]
                                (when (satisfies? ICollectionOpTrace coll-op)
                                  (trace-end coll-op current-ctx result (::current-trace current-ctx)))
                                result)
                            ;; Body passes or has residual - safe to continue without recording filter residual
                              (let [traced-ctx (if (satisfies? ICollectionOpTrace coll-op)
                                                 (trace-element coll-op current-ctx elem index filter-result body-result)
                                                 current-ctx)]
                                (recur (rest elements)
                                       (inc index)
                                       (:state process-result)
                                       residuals
                                       traced-ctx))))
                        ;; For aggregations with filter residual, track it
                          (let [indexed    (index-residual filter-result path index)
                                traced-ctx (if (satisfies? ICollectionOpTrace coll-op)
                                             (trace-element coll-op current-ctx elem index filter-result nil)
                                             current-ctx)]
                            (recur (rest elements)
                                   (inc index)
                                   state
                                   (merge-residual-paths residuals indexed)
                                   traced-ctx)))

                      ;; Element included - evaluate body
                        :else
                        (let [body-result    (if body
                                               (eval-ast-fn body document elem-ctx)
                                               true)
                              process-result (process-element coll-op state elem body-result index)]

                          (if (contains? process-result :short-circuit)
                          ;; Short circuit
                            (let [short-circuit (:short-circuit process-result)]
                              (when (satisfies? ICollectionOpTrace coll-op)
                                (trace-end coll-op current-ctx short-circuit (::current-trace current-ctx)))
                              short-circuit)

                          ;; Continue iteration
                            (let [new-residuals (if (residual? body-result)
                                                  (let [indexed (index-residual body-result path index)]
                                                    (merge-residual-paths residuals indexed))
                                                  residuals)
                                  traced-ctx    (if (satisfies? ICollectionOpTrace coll-op)
                                                  (trace-element coll-op current-ctx elem index :include body-result)
                                                  current-ctx)]
                              (recur (rest elements)
                                     (inc index)
                                     (:state process-result)
                                     new-residuals
                                     traced-ctx))))))))))))))))

;;; ---------------------------------------------------------------------------
;;; Built-in Collection Operators
;;; ---------------------------------------------------------------------------

(defn register-builtins!
  "Registers all built-in collection operators."
  []

  ;; Forall: all elements must satisfy body predicate
  (register-collection-op!
   :forall
   {:op-type :quantifier
    :empty-result true
    :init-state (fn [] {})
    :process-element (fn [state _elem body-result _idx]
                       (if (false? body-result)
                         {:short-circuit false}
                         {:state state}))
    :finalize (fn [_state residuals]
                (if (empty? residuals)
                  true
                  {:residual residuals}))
    :can-merge? (fn [other-op-key] (= :forall other-op-key))
    :merge-bodies (fn [body1 body2]
                    {:type :polix.ast/function-call
                     :value :and
                     :children [body1 body2]})})

  ;; Exists: at least one element must satisfy body predicate
  (register-collection-op!
   :exists
   {:op-type :quantifier
    :empty-result false
    :init-state (fn [] {})
    :process-element (fn [state _elem body-result _idx]
                       (if (true? body-result)
                         {:short-circuit true}
                         {:state state}))
    :finalize (fn [_state residuals]
                (if (empty? residuals)
                  false
                  {:residual residuals}))
    :can-merge? (fn [other-op-key] (= :exists other-op-key))
    :merge-bodies (fn [body1 body2]
                    {:type :polix.ast/function-call
                     :value :or
                     :children [body1 body2]})})

  ;; Count: count elements matching filter (or all if no filter)
  (register-collection-op!
   :count
   {:op-type :aggregation
    :empty-result 0
    :init-state (fn [] {:n 0})
    :process-element (fn [state _elem filter-result _idx]
                       {:state (if (true? filter-result)
                                 (update state :n inc)
                                 state)})
    :finalize (fn [{:keys [n]} residuals]
                (if (empty? residuals)
                  n
                  {:partial-count n :residual residuals}))
    :simplify-comparison
    (fn [comparison-op expected-value]
      (cond
        ;; [:> [:fn/count path] 0] -> [:exists ...]
        (and (= :> comparison-op) (= 0 expected-value))
        :simplify-to-exists

        ;; [:>= [:fn/count path] 1] -> [:exists ...]
        (and (= :>= comparison-op) (= 1 expected-value))
        :simplify-to-exists

        ;; [:= [:fn/count path] 0] -> [:not [:exists ...]]
        (and (= := comparison-op) (= 0 expected-value))
        :simplify-to-not-exists

        :else nil))})

  ;; Sum: sum numeric values from elements
  (register-collection-op!
   :sum
   {:op-type :aggregation
    :empty-result 0
    :init-state (fn [] {:total 0})
    :process-element (fn [state elem filter-result _idx]
                       {:state (if (and (true? filter-result) (number? elem))
                                 (update state :total + elem)
                                 state)})
    :finalize (fn [{:keys [total]} residuals]
                (if (empty? residuals)
                  total
                  {:partial-total total :residual residuals}))}))

;; Register builtins on namespace load
(register-builtins!)
