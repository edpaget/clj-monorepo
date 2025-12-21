(ns polix.engine
  "Unified policy evaluation engine.

  Combines constraint-based and AST-based evaluation into a single engine
  that returns three-valued results: `true` (satisfied), `false` (contradicted),
  or `{:residual ...}` (partial match with remaining constraints).

  Uses constraint sets as primary representation with AST fallback for complex
  expressions (OR, NOT, nested functions that cannot be normalized to constraints).

  ## Example

      (require '[polix.engine :as engine]
               '[polix.parser :as parser]
               '[polix.result :as r])

      ;; Evaluate parsed AST
      (let [ast (r/unwrap (parser/parse-policy [:= :doc/role \"admin\"]))]
        (engine/evaluate ast {:role \"admin\"}))
      ;; => true

      ;; Missing keys return residuals
      (engine/evaluate ast {})
      ;; => {:residual {[:role] [[:= \"admin\"]]}}

      ;; Nested paths
      (let [ast (r/unwrap (parser/parse-policy [:= :doc/user.name \"Alice\"]))]
        (engine/evaluate ast {:user {:name \"Alice\"}}))
      ;; => true

  Uses [[polix.operators]] registry as primary operator source with support
  for context overrides via the `:operators` option."
  (:require
   [clojure.string :as str]
   [polix.ast :as ast]
   [polix.operators :as op]
   [polix.parser :as parser]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; Result Types
;;; ---------------------------------------------------------------------------

(defn residual?
  "Returns true if x is a residual result."
  [x]
  (and (map? x) (contains? x :residual)))

(defn complex?
  "Returns true if x is a complex (unevaluable) result."
  [x]
  (and (map? x) (contains? x :complex)))

(defn result-type
  "Returns the type of an evaluation result: :satisfied, :contradicted,
   :residual, or :complex."
  [x]
  (cond
    (true? x) :satisfied
    (false? x) :contradicted
    (residual? x) :residual
    (complex? x) :complex
    :else :value))

;;; ---------------------------------------------------------------------------
;;; Path Utilities
;;; ---------------------------------------------------------------------------

(defn path-exists?
  "Returns true if the full path exists in the document.

  Unlike `get-in`, this distinguishes between a nil value at the path
  and a missing key. Returns true only if all keys in the path exist,
  even if the final value is nil.

      (path-exists? {:user {:name \"Alice\"}} [:user :name])  ;=> true
      (path-exists? {:user {:name nil}} [:user :name])       ;=> true
      (path-exists? {:user {}} [:user :name])                ;=> false
      (path-exists? {} [:user :name])                        ;=> false"
  [document path]
  (loop [current   document
         remaining path]
    (if (empty? remaining)
      true
      (let [k (first remaining)]
        (if (and (associative? current) (contains? current k))
          (recur (get current k) (rest remaining))
          false)))))

(defn path->doc-accessor
  "Converts a path vector back to a doc accessor keyword.

      (path->doc-accessor [:role])       ;=> :doc/role
      (path->doc-accessor [:user :name]) ;=> :doc/user.name"
  [path]
  (keyword "doc" (str/join "." (map name path))))

;;; ---------------------------------------------------------------------------
;;; Binding Context
;;; ---------------------------------------------------------------------------

(defn with-binding
  "Adds a binding to the evaluation context.

  Bindings are used by quantifiers to track the current element being iterated."
  [ctx binding-name value]
  (assoc-in ctx [:bindings binding-name] value))

(defn get-binding
  "Gets a binding value from context, or nil if not found."
  [ctx binding-name]
  (get-in ctx [:bindings binding-name]))

;;; ---------------------------------------------------------------------------
;;; Residual Merging
;;; ---------------------------------------------------------------------------

(defn merge-residuals
  "Merges multiple residual results into a single residual."
  [residuals]
  {:residual
   (reduce
    (fn [acc {:keys [residual]}]
      (merge-with into acc residual))
    {}
    residuals)})

(defn- index-residual
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

(defn- merge-residual-paths
  "Merges residual path maps from multiple residual results."
  [acc residual-result]
  (merge-with into acc (:residual residual-result)))

;;; ---------------------------------------------------------------------------
;;; Three-Valued Boolean Logic
;;; ---------------------------------------------------------------------------

(defn eval-and
  "Evaluates AND with three-valued logic.

   - false AND x = false
   - true AND true = true
   - true AND residual = residual
   - residual AND residual = merged residual"
  [results]
  (cond
    (some false? results) false
    (some complex? results) {:complex {:op :and :children (filter complex? results)}}
    (every? true? results) true
    :else (merge-residuals (filter residual? results))))

(defn eval-or
  "Evaluates OR with three-valued logic.

   - true OR x = true
   - false OR false = false
   - false OR residual = residual (single)
   - residual OR residual = complex (cannot simplify)"
  [results]
  (cond
    (some true? results) true
    (every? false? results) false
    ;; Single residual with rest false
    (and (= 1 (count (filter residual? results)))
         (every? #(or (false? %) (residual? %)) results))
    (first (filter residual? results))
    ;; Multiple residuals or complex - cannot simplify
    :else {:complex {:op :or :children (remove false? results)}}))

(defn eval-not
  "Evaluates NOT with three-valued logic.

   - NOT true = false
   - NOT false = true
   - NOT residual = complex (cannot negate residual)"
  [result]
  (cond
    (true? result) false
    (false? result) true
    :else {:complex {:op :not :child result}}))

;;; ---------------------------------------------------------------------------
;;; Quantifier Evaluation
;;; ---------------------------------------------------------------------------

(declare eval-ast-3v)

(defn- resolve-collection
  "Resolves the collection for a quantifier binding.

  Returns `{:ok collection}` if found, or `{:missing path}` if the collection
  path doesn't exist, or `{:invalid value}` if the value is not sequential."
  [binding document ctx]
  (let [{:keys [namespace path]} binding
        source (if (= "doc" namespace)
                 document
                 (get-binding ctx (keyword namespace)))]
    (cond
      (nil? source)
      {:missing path}

      (not (path-exists? source path))
      {:missing path}

      :else
      (let [coll (get-in source path)]
        (if (sequential? coll)
          {:ok coll}
          {:invalid coll})))))

(defn- evaluate-filter
  "Evaluates a filter predicate for a single element.

  Returns:
  - `:include` if filter evaluates to true
  - `:exclude` if filter evaluates to false
  - `{:residual ...}` if filter has missing data"
  [where-ast element document ctx binding-name]
  (let [elem-ctx (with-binding ctx binding-name element)
        result (eval-ast-3v where-ast document elem-ctx)]
    (cond
      (true? result) :include
      (false? result) :exclude
      (residual? result) result
      result :include
      :else :exclude)))

(defn- evaluate-forall
  "Evaluates forall quantifier with optional filter and three-valued logic.

   - All elements satisfy → true
   - Any element contradicts → false (short-circuit)
   - Empty collection → true (vacuous truth)
   - Collection missing → residual
   - Non-sequential value → false (type mismatch)
   - Some elements residual, none false → residual with indexed paths

   With `:where` filter:
   - Elements excluded by filter are skipped
   - Filter residual + body would fail → residual (might include and fail)
   - Filter residual + body passes → continue (safe for forall)"
  [binding body document ctx]
  (let [coll-result (resolve-collection binding document ctx)
        {:keys [name path where]} binding]
    (cond
      (:missing coll-result)
      {:residual {path [[:forall binding body]]}}

      (:invalid coll-result)
      false

      :else
      (let [coll (:ok coll-result)]
        (if (empty? coll)
          true
          (loop [elements (seq coll)
                 index 0
                 residuals {}]
            (if (empty? elements)
              (if (empty? residuals)
                true
                {:residual residuals})
              (let [elem (first elements)]
                (if-not where
                  ;; No filter - original behavior
                  (let [elem-ctx (with-binding ctx name elem)
                        result (eval-ast-3v body document elem-ctx)]
                    (cond
                      (false? result)
                      false

                      (residual? result)
                      (let [indexed (index-residual result path index)]
                        (recur (rest elements)
                               (inc index)
                               (merge-residual-paths residuals indexed)))

                      :else
                      (recur (rest elements) (inc index) residuals)))

                  ;; With filter
                  (let [filter-result (evaluate-filter where elem document ctx name)]
                    (cond
                      ;; Element excluded by filter - skip
                      (= :exclude filter-result)
                      (recur (rest elements) (inc index) residuals)

                      ;; Filter has residual - element might be included
                      (residual? filter-result)
                      (let [elem-ctx (with-binding ctx name elem)
                            body-result (eval-ast-3v body document elem-ctx)]
                        (if (false? body-result)
                          ;; Potential contradiction - record filter residual
                          (let [indexed (index-residual filter-result path index)]
                            (recur (rest elements)
                                   (inc index)
                                   (merge-residual-paths residuals indexed)))
                          ;; Body passes or residual - safe to continue
                          (recur (rest elements) (inc index) residuals)))

                      ;; Element included by filter - evaluate body
                      :else
                      (let [elem-ctx (with-binding ctx name elem)
                            result (eval-ast-3v body document elem-ctx)]
                        (cond
                          (false? result)
                          false

                          (residual? result)
                          (let [indexed (index-residual result path index)]
                            (recur (rest elements)
                                   (inc index)
                                   (merge-residual-paths residuals indexed)))

                          :else
                          (recur (rest elements) (inc index) residuals)))))))))))))

(defn- evaluate-exists
  "Evaluates exists quantifier with optional filter and three-valued logic.

   - Any element satisfies → true (short-circuit)
   - All elements contradict → false
   - Empty collection → false
   - Collection missing → residual
   - Non-sequential value → false (type mismatch)
   - Some elements residual, none true → residual with indexed paths

   With `:where` filter:
   - Elements excluded by filter are skipped
   - Filter residual + body passes → residual (might include and pass)
   - Filter residual + body fails → continue (safe for exists)"
  [binding body document ctx]
  (let [coll-result (resolve-collection binding document ctx)
        {:keys [name path where]} binding]
    (cond
      (:missing coll-result)
      {:residual {path [[:exists binding body]]}}

      (:invalid coll-result)
      false

      :else
      (let [coll (:ok coll-result)]
        (if (empty? coll)
          false
          (loop [elements (seq coll)
                 index 0
                 residuals {}]
            (if (empty? elements)
              (if (empty? residuals)
                false
                {:residual residuals})
              (let [elem (first elements)]
                (if-not where
                  ;; No filter - original behavior
                  (let [elem-ctx (with-binding ctx name elem)
                        result (eval-ast-3v body document elem-ctx)]
                    (cond
                      (true? result)
                      true

                      (residual? result)
                      (let [indexed (index-residual result path index)]
                        (recur (rest elements)
                               (inc index)
                               (merge-residual-paths residuals indexed)))

                      :else
                      (recur (rest elements) (inc index) residuals)))

                  ;; With filter
                  (let [filter-result (evaluate-filter where elem document ctx name)]
                    (cond
                      ;; Element excluded by filter - skip
                      (= :exclude filter-result)
                      (recur (rest elements) (inc index) residuals)

                      ;; Filter has residual - element might be included
                      (residual? filter-result)
                      (let [elem-ctx (with-binding ctx name elem)
                            body-result (eval-ast-3v body document elem-ctx)]
                        (if (true? body-result)
                          ;; Potential satisfaction - record filter residual
                          (let [indexed (index-residual filter-result path index)]
                            (recur (rest elements)
                                   (inc index)
                                   (merge-residual-paths residuals indexed)))
                          ;; Body fails or residual - safe to continue
                          (recur (rest elements) (inc index) residuals)))

                      ;; Element included by filter - evaluate body
                      :else
                      (let [elem-ctx (with-binding ctx name elem)
                            result (eval-ast-3v body document elem-ctx)]
                        (cond
                          (true? result)
                          true

                          (residual? result)
                          (let [indexed (index-residual result path index)]
                            (recur (rest elements)
                                   (inc index)
                                   (merge-residual-paths residuals indexed)))

                          :else
                          (recur (rest elements) (inc index) residuals)))))))))))))

;;; ---------------------------------------------------------------------------
;;; Constraint Evaluation
;;; ---------------------------------------------------------------------------

(defn evaluate-constraint
  "Evaluates a single constraint against a value.

   Takes an operator context `ctx`, a constraint map with `:op` and `:value` keys,
   and the actual `value` from the document.

   Returns true if satisfied, false if contradicted, nil if operator unknown."
  [ctx constraint value]
  (op/eval-in-context ctx constraint value))

(defn evaluate-constraints-for-key
  "Evaluates all constraints for a key against a value.

   Returns:
   - true if all satisfied
   - false if any contradicted
   - `{:residual [...]}` if value missing or operator unknown"
  [ctx constraints value value-present?]
  (if-not value-present?
    {:residual (mapv (fn [c] [(:op c) (:value c)]) constraints)}
    (let [results (map #(evaluate-constraint ctx % value) constraints)]
      (cond
        (some false? results) false
        (every? true? results) true
        :else {:residual (mapv (fn [[_ c]] [(:op c) (:value c)])
                               (filter #(nil? (first %))
                                       (map vector results constraints)))}))))

;;; ---------------------------------------------------------------------------
;;; Constraint Set Evaluation
;;; ---------------------------------------------------------------------------

(defn evaluate-constraint-set
  "Evaluates a constraint set against a document.

   A constraint set is a map of `{path -> [constraints], ::complex -> [nodes]}`.
   Paths are vectors of keywords representing nested document access.

   Returns:
   - true if all constraints satisfied
   - false if any constraint contradicted
   - `{:residual {...}}` if partial match"
  ([constraint-set document]
   (evaluate-constraint-set constraint-set document (op/make-context)))
  ([constraint-set document ctx]
   (loop [paths-to-check    (keys (dissoc constraint-set ::complex))
          residuals         {}
          any-contradicted? false]
     (if (or any-contradicted? (empty? paths-to-check))
       (cond
         any-contradicted? false
         (seq residuals) {:residual residuals}
         :else true)
       (let [path           (first paths-to-check)
             constraints    (get constraint-set path)
             value-present? (path-exists? document path)
             value          (get-in document path)
             result         (evaluate-constraints-for-key ctx constraints value value-present?)]
         (recur
          (rest paths-to-check)
          (if (residual? result)
            (assoc residuals path (:residual result))
            residuals)
          (false? result)))))))

;;; ---------------------------------------------------------------------------
;;; Three-Valued AST Evaluation
;;; ---------------------------------------------------------------------------

(defmulti eval-ast-3v
  "Evaluates an AST node with three-valued semantics.

   Returns true, false, a value, `{:residual ...}`, or `{:complex ...}`."
  (fn [node _document _ctx] (:type node)))

(defmethod eval-ast-3v ::ast/literal
  [node _document _ctx]
  (:value node))

(defmethod eval-ast-3v ::ast/doc-accessor
  [node document ctx]
  (let [path (:value node)
        binding-ns (get-in node [:metadata :binding-ns])]
    (if binding-ns
      ;; Binding accessor - look up in context
      (let [binding-name (keyword binding-ns)
            bound-value (get-binding ctx binding-name)]
        (if (nil? bound-value)
          {:residual {path [[:binding binding-name :any]]}}
          (if (path-exists? bound-value path)
            (get-in bound-value path)
            {:residual {path [[:any]]}})))
      ;; Document accessor
      (if (path-exists? document path)
        (get-in document path)
        {:residual {path [[:any]]}}))))

(defmethod eval-ast-3v ::ast/quantifier
  [node document ctx]
  (let [quantifier-type (:value node)
        binding (get-in node [:metadata :binding])
        body (first (:children node))]
    (case quantifier-type
      :forall (evaluate-forall binding body document ctx)
      :exists (evaluate-exists binding body document ctx))))

(defn- evaluate-count
  "Evaluates :count value function with optional filter."
  [binding document ctx]
  (let [{:keys [namespace path name where]} binding
        source (if (or (nil? namespace) (= "doc" namespace))
                 document
                 (get-binding ctx (keyword namespace)))]
    (cond
      (nil? source)
      {:residual {path [[:fn/count (if where {:binding binding} :all)]]}}

      (not (path-exists? source path))
      {:residual {path [[:fn/count (if where {:binding binding} :all)]]}}

      :else
      (let [coll (get-in source path)]
        (cond
          (not (sequential? coll))
          0

          (nil? where)
          (count coll)

          :else
          (loop [elements (seq coll)
                 index 0
                 counted 0
                 residuals {}]
            (if (empty? elements)
              (if (empty? residuals)
                counted
                {:residual residuals :partial-count counted})
              (let [elem (first elements)
                    filter-result (evaluate-filter where elem document ctx name)]
                (cond
                  (= :exclude filter-result)
                  (recur (rest elements) (inc index) counted residuals)

                  (residual? filter-result)
                  (let [indexed (index-residual filter-result path index)]
                    (recur (rest elements)
                           (inc index)
                           counted
                           (merge-residual-paths residuals indexed)))

                  :else
                  (recur (rest elements) (inc index) (inc counted) residuals))))))))))

(defmethod eval-ast-3v ::ast/value-fn
  [node document ctx]
  (let [fn-type (:value node)
        binding (get-in node [:metadata :binding])]
    (case fn-type
      :count (evaluate-count binding document ctx)
      {:complex {:unknown-value-fn fn-type}})))

(defn- evaluate-children
  "Evaluates all child nodes of a function call."
  [children document ctx]
  (mapv #(eval-ast-3v % document ctx) children))

(defn- resolve-accessor-value
  "Resolves the value for a doc-accessor node, handling both document and binding accessors."
  [accessor-node document ctx]
  (let [path (:value accessor-node)
        binding-ns (get-in accessor-node [:metadata :binding-ns])]
    (if binding-ns
      ;; Binding accessor
      (let [binding-name (keyword binding-ns)
            bound-value (get-binding ctx binding-name)]
        (if (and bound-value (path-exists? bound-value path))
          {:found true :value (get-in bound-value path)}
          {:found false}))
      ;; Document accessor
      (if (path-exists? document path)
        {:found true :value (get-in document path)}
        {:found false}))))

(defn- comparison-to-constraint
  "Converts a comparison function call to a constraint if possible."
  [op-key children document ctx]
  (when (= 2 (count children))
    (let [[left right] children
          left-type    (:type left)
          right-type   (:type right)]
      (cond
        ;; [:= :doc/key value] or [:= :u/key value]
        (and (= ::ast/doc-accessor left-type)
             (= ::ast/literal right-type))
        (let [path       (:value left)
              expected   (:value right)
              resolved   (resolve-accessor-value left document ctx)]
          (if (:found resolved)
            (let [constraint {:op op-key :value expected}
                  result     (evaluate-constraint ctx constraint (:value resolved))]
              (if (nil? result)
                {:complex {:op op-key :ast {:left left :right right}}}
                result))
            {:residual {path [[op-key expected]]}}))

        ;; [:= value :doc/key] - flipped
        (and (= ::ast/literal left-type)
             (= ::ast/doc-accessor right-type))
        (let [path       (:value right)
              expected   (:value left)
              flipped-op (case op-key
                           :< :>
                           :> :<
                           :<= :>=
                           :>= :<=
                           op-key)
              resolved   (resolve-accessor-value right document ctx)]
          (if (:found resolved)
            (let [constraint {:op flipped-op :value expected}
                  result     (evaluate-constraint ctx constraint (:value resolved))]
              (if (nil? result)
                {:complex {:op flipped-op :ast {:left left :right right}}}
                result))
            {:residual {path [[flipped-op expected]]}}))

        :else nil))))

(defmethod eval-ast-3v ::ast/function-call
  [node document ctx]
  (let [op-key   (:value node)
        children (:children node)]
    (case op-key
      ;; Boolean connectives
      :and (eval-and (evaluate-children children document ctx))
      :or (eval-or (evaluate-children children document ctx))
      :not (eval-not (eval-ast-3v (first children) document ctx))

      ;; Try constraint-based evaluation for comparisons
      ;; Use if-some to handle false results correctly (false is a valid result)
      (if-some [result (comparison-to-constraint op-key children document ctx)]
        result
        ;; Fall back to full evaluation for complex cases
        (let [evaluated-args (evaluate-children children document ctx)]
          (if (some #(or (residual? %) (complex? %)) evaluated-args)
            {:complex {:op op-key :children evaluated-args}}
            (if-let [operator (op/get-operator-in-context ctx op-key)]
              (apply op/eval operator evaluated-args)
              {:complex {:op op-key :children evaluated-args}})))))))

(defmethod eval-ast-3v :default
  [node _document _ctx]
  {:complex {:unknown-node-type (:type node) :node node}})

;;; ---------------------------------------------------------------------------
;;; Unified Entry Point
;;; ---------------------------------------------------------------------------

(defn evaluate
  "Evaluates a policy against a document with three-valued semantics.

   Takes:
   - `policy` - AST node, constraint set, or policy expression vector
   - `document` - Associative data structure to evaluate against
   - `opts` - Optional map with `:operators`, `:fallback`, `:strict?`, `:trace?`

   Returns:
   - `true` if fully satisfied
   - `false` if contradicted
   - `{:residual {...}}` if partial evaluation

   ## Examples

       ;; Evaluate AST
       (evaluate ast {:role \"admin\"})
       ;; => true

       ;; With options
       (evaluate ast document {:strict? true})"
  ([policy document]
   (evaluate policy document {}))
  ([policy document opts]
   (let [ctx (op/make-context opts)]
     (cond
       ;; AST node
       (and (map? policy) (:type policy))
       (eval-ast-3v policy document ctx)

       ;; Constraint set (map with keyword keys pointing to constraint vectors)
       (and (map? policy) (not (:type policy)))
       (evaluate-constraint-set policy document ctx)

       ;; Policy expression vector - parse first
       (vector? policy)
       (let [parse-result (r/unwrap (parser/parse-policy policy))]
         (eval-ast-3v parse-result document ctx))

       :else
       (throw (ex-info "Unknown policy type" {:policy policy}))))))

;;; ---------------------------------------------------------------------------
;;; Residual Conversion
;;; ---------------------------------------------------------------------------

(defn residual->constraints
  "Converts a residual map back to policy expressions.

   Takes `{[:level] [[:> 5]], [:user :status] [[:in #{\"a\" \"b\"}]]}`
   Returns `[[:> :doc/level 5] [:in :doc/user.status #{\"a\" \"b\"}]]`"
  [residual]
  (mapcat
   (fn [[path constraints]]
     (map (fn [[op value]]
            [op (path->doc-accessor path) value])
          constraints))
   residual))

(defn result->policy
  "Converts an evaluation result to a simplified policy expression.

   Returns:
   - nil for true (no constraints needed)
   - `[:contradiction]` for false
   - The simplified constraints for residual"
  [result]
  (cond
    (true? result) nil
    (false? result) [:contradiction]
    (residual? result)
    (let [constraints (residual->constraints (:residual result))]
      (if (= 1 (count constraints))
        (first constraints)
        (into [:and] constraints)))
    (complex? result) [:complex (:complex result)]))
