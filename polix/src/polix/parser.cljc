(ns polix.parser
  "Parser for the policy DSL.

  Transforms policy expressions (vector-based S-expressions) into AST nodes
  that can be evaluated. Supports document accessors, function calls, literals,
  and thunks for delayed evaluation."
  (:require
   [clojure.string :as str]
   [polix.ast :as ast]
   [polix.result :as r]))

(defn doc-accessor?
  "Returns `true` if `k` is a document accessor keyword.

  Document accessors are namespaced keywords with namespace `\"doc\"`,
  such as `:doc/actor-role`."
  [k]
  (and (keyword? k)
       (= "doc" (namespace k))))

(defn binding-accessor?
  "Returns `true` if `k` is a binding accessor keyword.

  Binding accessors are namespaced keywords that reference a bound variable
  from a quantifier, such as `:u/role` or `:team/members`. They have a
  namespace that is not `\"doc\"` or `\"fn\"`."
  [k]
  (and (keyword? k)
       (some? (namespace k))
       (not= "doc" (namespace k))
       (not= "fn" (namespace k))))

(defn quantifier-op?
  "Returns `true` if `op` is a quantifier operator (`:forall` or `:exists`)."
  [op]
  (contains? #{:forall :exists} op))

(defn parse-doc-path
  "Parses a dot-separated path string into a vector of keywords.

  Takes a path string from a document accessor keyword and returns a vector
  of keywords representing the nested path.

      (parse-doc-path \"role\")              ;=> [:role]
      (parse-doc-path \"user.name\")         ;=> [:user :name]
      (parse-doc-path \"user.profile.email\") ;=> [:user :profile :email]

  Returns `{:ok path-vector}` on success or `{:error error-map}` on failure
  for malformed paths (empty segments, leading/trailing dots)."
  [path-str]
  (cond
    (str/blank? path-str)
    (r/error {:error :invalid-path :message "Path cannot be empty" :path path-str})

    (str/starts-with? path-str ".")
    (r/error {:error :invalid-path :message "Path cannot start with a dot" :path path-str})

    (str/ends-with? path-str ".")
    (r/error {:error :invalid-path :message "Path cannot end with a dot" :path path-str})

    (str/includes? path-str "..")
    (r/error {:error :invalid-path :message "Path cannot contain empty segments" :path path-str})

    :else
    (r/ok (mapv keyword (str/split path-str #"\.")))))

(defn parse-binding
  "Parses a quantifier binding form `[name collection-path]`.

  The binding form specifies a variable name and the collection to iterate over.
  The name can be a symbol or keyword, and the collection path must be a
  `:doc/` accessor or a binding accessor (for nested quantifiers).

      (parse-binding '[u :doc/users] [0 0])
      ;=> {:ok {:name :u, :namespace \"doc\", :path [:users]}}

      (parse-binding '[m :team/members] [0 0])
      ;=> {:ok {:name :m, :namespace \"team\", :path [:members]}}

  Returns `{:ok binding-map}` on success or `{:error error-map}` on failure."
  [binding-form position]
  (cond
    (not (vector? binding-form))
    (r/error {:error :invalid-binding
              :message "Binding must be a vector [name collection-path]"
              :position position
              :value binding-form})

    (not= 2 (count binding-form))
    (r/error {:error :invalid-binding
              :message "Binding must have exactly 2 elements [name collection-path]"
              :position position
              :value binding-form})

    :else
    (let [[binding-name coll-path] binding-form]
      (cond
        (not (or (symbol? binding-name) (keyword? binding-name)))
        (r/error {:error :invalid-binding-name
                  :message "Binding name must be a symbol or keyword"
                  :position position
                  :value binding-name})

        (not (or (doc-accessor? coll-path) (binding-accessor? coll-path)))
        (r/error {:error :invalid-collection-path
                  :message "Collection path must be a :doc/ or binding accessor"
                  :position position
                  :value coll-path})

        :else
        (let [path-result (parse-doc-path (name coll-path))]
          (if (r/error? path-result)
            (r/error (assoc (r/unwrap path-result) :position position))
            (r/ok {:name (if (symbol? binding-name)
                           (keyword binding-name)
                           binding-name)
                   :namespace (namespace coll-path)
                   :path (r/unwrap path-result)})))))))

(defn thunkable?
  "Returns `true` if `form` should be wrapped in a thunk for delayed evaluation.

  Vars and non-empty lists (function calls) should be thunked."
  [form]
  (boolean
   (or #?(:clj (var? form) :cljs false)
       (and (list? form) (seq form)))))

(defn classify-token
  "Classifies a `token` into its AST node type based on `position`.

  Returns a Result containing an [[ast/ASTNode]] with the appropriate type:
  - `::ast/doc-accessor` for document accessors (value is a path vector)
  - `::ast/doc-accessor` for binding accessors (value is path, metadata has namespace)
  - `::ast/thunk` for thunkable forms
  - `::ast/literal` for all other values

  For document accessors, the token's name is parsed as a dot-separated path:
  - `:doc/role` becomes `[:role]`
  - `:doc/user.name` becomes `[:user :name]`

  For binding accessors, the namespace is preserved in metadata:
  - `:u/role` becomes `[:role]` with metadata `{:binding-ns \"u\"}`

  Returns `{:ok ast-node}` on success or `{:error error-map}` on failure."
  [token position]
  (cond
    (doc-accessor? token)
    (let [path-result (parse-doc-path (name token))]
      (if (r/error? path-result)
        (r/error (assoc (r/unwrap path-result) :position position))
        (r/ok (ast/ast-node ::ast/doc-accessor (r/unwrap path-result) position))))

    (binding-accessor? token)
    (let [path-result (parse-doc-path (name token))]
      (if (r/error? path-result)
        (r/error (assoc (r/unwrap path-result) :position position))
        (r/ok (ast/ast-node ::ast/doc-accessor
                           (r/unwrap path-result)
                           position
                           nil
                           {:binding-ns (namespace token)}))))

    (thunkable? token)
    (r/ok (ast/ast-node ::ast/thunk (fn [] token) position))

    :else
    (r/ok (ast/ast-node ::ast/literal token position))))

(defn valid-function-name?
  "Returns `true` if `v` is a valid function name for the policy DSL.

  Valid function names are keywords or symbols."
  [v]
  (or (keyword? v) (symbol? v)))

(declare parse-policy)

(defn- parse-quantifier
  "Parses a quantifier expression `[:forall [name path] body]` or `[:exists ...]`.

  Returns `{:ok ASTNode}` with type `::ast/quantifier` on success."
  [quantifier-op args position]
  (cond
    (< (count args) 2)
    (r/error {:error :invalid-quantifier
              :message (str quantifier-op " requires a binding and body expression")
              :position position})

    (> (count args) 2)
    (r/error {:error :invalid-quantifier
              :message (str quantifier-op " takes exactly 2 arguments: binding and body")
              :position position})

    :else
    (let [binding-form (first args)
          body-expr (second args)
          binding-result (parse-binding binding-form [(first position) (inc (second position))])]
      (if (r/error? binding-result)
        binding-result
        (let [body-result (parse-policy body-expr [(first position) (+ (second position) 2)])]
          (if (r/error? body-result)
            body-result
            (r/ok (ast/ast-node ::ast/quantifier
                                quantifier-op
                                position
                                [(r/unwrap body-result)]
                                {:binding (r/unwrap binding-result)}))))))))

(defn parse-policy
  "Parses a policy DSL expression `expr` into an AST.

  The DSL supports:
  - Document accessors: `:doc/key-name`
  - Binding accessors: `:u/field` (within quantifier bodies)
  - Function calls: `[:fn-name arg1 arg2 ...]`
  - Quantifiers: `[:forall [u :doc/users] body]`, `[:exists [t :doc/teams] body]`
  - Literals: strings, numbers, keywords, etc.
  - Thunks: Clojure vars and function calls wrapped for delayed evaluation

  Takes an optional `position` vector `[start end]` for tracking location (defaults to `[0 0]`).

  Returns `{:ok ASTNode}` on success or `{:error error-map}` on failure."
  ([expr]
   (parse-policy expr [0 0]))
  ([expr position]
   (cond
     (vector? expr)
     (if (empty? expr)
       (r/ok (ast/ast-node ::ast/literal expr position))
       (let [fn-name (first expr)]
         (if-not (valid-function-name? fn-name)
           (r/error {:error :invalid-function-name
                     :message (str "Function name must be a keyword or symbol, got: " (pr-str fn-name))
                     :position position
                     :value fn-name})
           (if (quantifier-op? fn-name)
             (parse-quantifier fn-name (rest expr) position)
             (let [args        (rest expr)
                   parsed-args (r/sequence-results
                                (map-indexed
                                 (fn [idx arg]
                                   (let [arg-pos [(first position) (+ (second position) idx 1)]]
                                     (parse-policy arg arg-pos)))
                                 args))]
               (if (r/error? parsed-args)
                 parsed-args
                 (r/ok (ast/ast-node ::ast/function-call
                                     fn-name
                                     position
                                     (vec (r/unwrap parsed-args))))))))))

     :else
     (classify-token expr position))))

(defn extract-doc-keys
  "Extracts all document accessor paths from a policy `ast`.

  Returns a set of path vectors representing nested document access paths.
  Each path is a vector of keywords.

      (extract-doc-keys (parse-policy [:= :doc/role \"admin\"]))
      ;=> #{[:role]}

      (extract-doc-keys (parse-policy [:= :doc/user.name \"Alice\"]))
      ;=> #{[:user :name]}"
  [ast]
  (->> (tree-seq :children :children ast)
       (filter #(= ::ast/doc-accessor (:type %)))
       (map :value)
       (into #{})))
