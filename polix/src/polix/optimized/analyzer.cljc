(ns polix.optimized.analyzer
  "Policy analysis for optimized evaluation tier selection.

  Provides type inference from constraint literals, operator classification,
  and tier selection for the optimized evaluation pipeline.")

(def builtin-ops
  "Set of built-in operators that can be fully optimized."
  #{:= :!= :> :< :>= :<= :in :not-in :matches :not-matches})

(defn builtin-op?
  "Returns true if the operator is a built-in that can be fully optimized."
  [op-key]
  (contains? builtin-ops op-key))

(defn classify-operator
  "Classifies an operator as `:builtin` or `:custom`."
  [op-key]
  (if (builtin-op? op-key) :builtin :custom))

(defn- regex-pattern?
  "Returns true if value is a regex pattern."
  [value]
  #?(:clj  (instance? java.util.regex.Pattern value)
     :cljs (regexp? value)))

(defn infer-type-from-value
  "Infers a type from a literal value.

  Returns one of:
  - `:string` - String
  - `:long` - integer/long
  - `:double` - floating point
  - `:boolean` - boolean
  - `:set` - set collection
  - `:pattern` - regex pattern
  - `:keyword` - keyword
  - `:unknown` - cannot determine type"
  [value]
  (cond
    (string? value) :string
    (integer? value) :long
    #?(:clj  (float? value)
       :cljs (and (number? value) (not (integer? value)))) :double
    (boolean? value) :boolean
    (set? value) :set
    (regex-pattern? value) :pattern
    (keyword? value) :keyword
    :else :unknown))

(defn infer-element-type
  "Infers the element type of a set."
  [s]
  (if (empty? s)
    :unknown
    (let [types (set (map infer-type-from-value s))]
      (if (= 1 (count types))
        (first types)
        :unknown))))

(defn infer-type-from-op
  "Infers the expected document type based on operator and expected value.

  For example:
  - `[:= path \"admin\"]` -> `:string`
  - `[:> path 5]` -> `:long` or `:double`
  - `[:in path #{\"a\" \"b\"}]` -> element type of set
  - `[:matches path #\".*\"]` -> `:string`"
  [op-key expected-value]
  (case op-key
    (:= :!=)
    (infer-type-from-value expected-value)

    (:> :< :>= :<=)
    (cond
      (integer? expected-value) :long
      (number? expected-value) :double
      :else :unknown)

    (:in :not-in)
    (if (set? expected-value)
      (infer-element-type expected-value)
      :unknown)

    (:matches :not-matches)
    :string

    :unknown))

(defn compatible-types?
  "Returns true if two types are compatible for unification."
  [t1 t2]
  (or (= t1 t2)
      (= t1 :unknown)
      (= t2 :unknown)
      (and (#{:long :double} t1) (#{:long :double} t2))))

(defn narrowest-type
  "Returns the narrowest (most specific) of two compatible types."
  [t1 t2]
  (cond
    (= t1 :unknown) t2
    (= t2 :unknown) t1
    (and (= t1 :long) (= t2 :double)) :long
    (and (= t1 :double) (= t2 :long)) :long
    :else t1))

(defprotocol ITypeEnv
  "Protocol for type environment operations."
  (get-type [this path] "Returns the inferred type for a document path.")
  (unify-type [this path new-type] "Unifies a new type constraint, returns updated env or error."))

(defrecord TypeEnv [types]
  ITypeEnv
  (get-type [_ path]
    (get types path))

  (unify-type [_ path new-type]
    (if-let [existing (get types path)]
      (if (compatible-types? existing new-type)
        (->TypeEnv (assoc types path (narrowest-type existing new-type)))
        {:error {:path path :conflict [existing new-type]}})
      (->TypeEnv (assoc types path new-type)))))

(defn empty-type-env
  "Creates an empty type environment."
  []
  (->TypeEnv {}))

(defn analyze-constraint
  "Analyzes a single constraint and returns type information.

  Returns a map with:
  - `:path` - document path
  - `:op` - operator keyword
  - `:expected` - expected value
  - `:type` - inferred type
  - `:op-class` - `:builtin` or `:custom`"
  [constraint]
  (let [path (:key constraint)
        op (:op constraint)
        expected (:value constraint)]
    {:path path
     :op op
     :expected expected
     :type (infer-type-from-op op expected)
     :op-class (classify-operator op)}))

(defn analyze-constraint-set
  "Analyzes a constraint set and builds a type environment.

  Returns a map with:
  - `:type-env` - TypeEnv with path -> type mappings
  - `:constraints` - vector of analyzed constraints
  - `:has-custom-ops` - true if any custom operators found
  - `:has-complex` - true if complex (non-constraint) nodes found
  - `:errors` - vector of type errors (empty if none)"
  [constraint-set]
  (let [constraints (for [[path cs] constraint-set
                          :when (vector? path)
                          c cs]
                      (assoc (analyze-constraint c) :path path))
        has-complex (contains? constraint-set :polix.compiler/complex)
        has-custom-ops (some #(= :custom (:op-class %)) constraints)]
    (loop [env (empty-type-env)
           errors []
           remaining constraints]
      (if (empty? remaining)
        {:type-env env
         :constraints (vec constraints)
         :has-custom-ops (boolean has-custom-ops)
         :has-complex has-complex
         :errors errors}
        (let [c (first remaining)
              result (unify-type env (:path c) (:type c))]
          (if (:error result)
            (recur env (conj errors (:error result)) (rest remaining))
            (recur result errors (rest remaining))))))))

(defn select-tier
  "Selects the evaluation tier based on analysis results.

  Returns:
  - `:t2` - Optimized closures (all builtin operators, no complex nodes)
  - `:t1` - Guarded closures (has custom operators, needs version guards)
  - `:t0` - Interpreted (has complex nodes or type errors)"
  [{:keys [has-custom-ops has-complex errors]}]
  (cond
    (or has-complex (seq errors)) :t0
    has-custom-ops :t1
    :else :t2))

(defn analyze-policy
  "Analyzes a policy for optimized evaluation.

  Takes a constraint set (output of `compiler/normalize-and-merge`) and
  returns analysis results including type environment, tier selection,
  and operator classification.

  Returns a map with:
  - `:tier` - `:t0`, `:t1`, or `:t2`
  - `:type-env` - TypeEnv with inferred types
  - `:constraints` - analyzed constraints
  - `:operators` - `{:builtin [...] :custom [...]}`
  - `:errors` - type errors (if any)"
  [constraint-set]
  (let [analysis (analyze-constraint-set constraint-set)
        tier (select-tier analysis)
        ops (group-by :op-class (:constraints analysis))]
    (assoc analysis
           :tier tier
           :operators {:builtin (vec (map :op (:builtin ops)))
                       :custom (vec (map :op (:custom ops)))})))
