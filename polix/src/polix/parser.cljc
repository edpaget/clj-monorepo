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
  namespace that is not a reserved namespace (`doc`, `fn`, `self`, `param`, `event`)."
  [k]
  (and (keyword? k)
       (some? (namespace k))
       (not (contains? #{"doc" "fn" "self" "param" "event"} (namespace k)))))

(defn fn-accessor?
  "Returns `true` if `k` is a function accessor keyword.

  Function accessors are namespaced keywords with namespace `\"fn\"`,
  such as `:fn/count` or `:fn/sum`."
  [k]
  (and (keyword? k)
       (= "fn" (namespace k))))

(defn self-accessor?
  "Returns `true` if `k` is a self accessor keyword.

  Self accessors reference values bound in let expressions,
  such as `:self/computed-value`."
  [k]
  (and (keyword? k)
       (= "self" (namespace k))))

(defn param-accessor?
  "Returns `true` if `k` is a parameter accessor keyword.

  Parameter accessors reference policy parameters,
  such as `:param/role` or `:param/min-level`."
  [k]
  (and (keyword? k)
       (= "param" (namespace k))))

(defn event-accessor?
  "Returns `true` if `k` is an event accessor keyword.

  Event accessors reference event data in triggers,
  such as `:event/target-id` or `:event/amount`."
  [k]
  (and (keyword? k)
       (= "event" (namespace k))))

(defn quantifier-op?
  "Returns `true` if `op` is a quantifier operator (`:forall` or `:exists`)."
  [op]
  (contains? #{:forall :exists} op))

(defn policy-reference?
  "Returns `true` if `form` is a policy reference.

  Policy references are vectors starting with a namespaced keyword
  that is not a built-in operator or accessor:
  - `[:auth/admin]`
  - `[:auth/has-role {:role \"editor\"}]`"
  [form]
  (and (vector? form)
       (seq form)
       (keyword? (first form))
       (some? (namespace (first form)))
       (not (contains? #{"doc" "fn"} (namespace (first form))))
       (not (quantifier-op? (first form)))))

(defn let-binding?
  "Returns `true` if `form` is a let binding expression.

  Let bindings have the form `[:let [bindings...] body]`."
  [form]
  (and (vector? form)
       (= :let (first form))))

(declare parse-policy)

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
  "Parses a quantifier binding form.

  Supports two forms:
  - `[name collection-path]` - basic binding
  - `[name collection-path :where predicate]` - filtered binding

  The binding form specifies a variable name and the collection to iterate over.
  The name can be a symbol or keyword, and the collection path must be a
  `:doc/` accessor or a binding accessor (for nested quantifiers).

      (parse-binding '[u :doc/users] [0 0])
      ;=> {:ok {:name :u, :namespace \"doc\", :path [:users]}}

      (parse-binding '[u :doc/users :where [:= :u/active true]] [0 0])
      ;=> {:ok {:name :u, :namespace \"doc\", :path [:users], :where <ast>}}

  Returns `{:ok binding-map}` on success or `{:error error-map}` on failure."
  [binding-form position]
  (cond
    (not (vector? binding-form))
    (r/error {:error :invalid-binding
              :message "Binding must be a vector"
              :position position
              :value binding-form})

    (< (count binding-form) 2)
    (r/error {:error :invalid-binding
              :message "Binding must have at least 2 elements [name collection-path]"
              :position position
              :value binding-form})

    (> (count binding-form) 4)
    (r/error {:error :invalid-binding
              :message "Binding has too many elements"
              :position position
              :value binding-form})

    :else
    (let [[binding-name coll-path & rest-args] binding-form
          has-where?                           (= :where (first rest-args))]
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

        (and has-where? (nil? (second rest-args)))
        (r/error {:error :invalid-where-clause
                  :message ":where clause requires a predicate expression"
                  :position position
                  :value binding-form})

        (and (not has-where?) (seq rest-args))
        (r/error {:error :invalid-binding
                  :message "Unexpected elements in binding form (did you mean to use :where?)"
                  :position position
                  :value binding-form})

        :else
        (let [path-result (parse-doc-path (name coll-path))]
          (if (r/error? path-result)
            (r/error (assoc (r/unwrap path-result) :position position))
            (let [base-binding {:name (if (symbol? binding-name)
                                        (keyword binding-name)
                                        binding-name)
                                :namespace (namespace coll-path)
                                :path (r/unwrap path-result)}]
              (if-not has-where?
                (r/ok base-binding)
                (let [where-expr   (second rest-args)
                      where-result (parse-policy where-expr
                                                 [(first position) (+ (second position) 3)])]
                  (if (r/error? where-result)
                    where-result
                    (r/ok (assoc base-binding :where (r/unwrap where-result)))))))))))))

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
  - `::ast/self-accessor` for self accessors (value is a path vector)
  - `::ast/param-accessor` for param accessors (value is keyword)
  - `::ast/event-accessor` for event accessors (value is a path vector)
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

    (self-accessor? token)
    (let [path-result (parse-doc-path (name token))]
      (if (r/error? path-result)
        (r/error (assoc (r/unwrap path-result) :position position))
        (r/ok (ast/ast-node ::ast/self-accessor (r/unwrap path-result) position))))

    (param-accessor? token)
    (r/ok (ast/ast-node ::ast/param-accessor (keyword (name token)) position))

    (event-accessor? token)
    (let [path-result (parse-doc-path (name token))]
      (if (r/error? path-result)
        (r/error (assoc (r/unwrap path-result) :position position))
        (r/ok (ast/ast-node ::ast/event-accessor (r/unwrap path-result) position))))

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
    (let [binding-form   (first args)
          body-expr      (second args)
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

(defn- parse-value-fn
  "Parses a value function expression like `[:fn/count ...]`.

  Value functions take a single argument which is either:
  - A simple collection path: `:doc/users`
  - A filtered binding: `[:u :doc/users :where [...]]`

  Returns `{:ok ASTNode}` with type `::ast/value-fn` on success."
  [fn-name args position]
  (let [fn-type (keyword (name fn-name))]
    (if (not= 1 (count args))
      (r/error {:error :invalid-value-fn
                :message (str fn-name " takes exactly 1 argument (collection path or binding)")
                :position position})
      (let [arg (first args)]
        (cond
          ;; Simple path: [:fn/count :doc/users]
          (or (doc-accessor? arg) (binding-accessor? arg))
          (let [path-result (parse-doc-path (name arg))]
            (if (r/error? path-result)
              (r/error (assoc (r/unwrap path-result) :position position))
              (r/ok (ast/ast-node ::ast/value-fn
                                  fn-type
                                  position
                                  nil
                                  {:binding {:namespace (namespace arg)
                                             :path (r/unwrap path-result)}}))))

          ;; Filtered binding: [:fn/count [:u :doc/users :where [...]]]
          (vector? arg)
          (let [binding-result (parse-binding arg [(first position) (inc (second position))])]
            (if (r/error? binding-result)
              binding-result
              (r/ok (ast/ast-node ::ast/value-fn
                                  fn-type
                                  position
                                  nil
                                  {:binding (r/unwrap binding-result)}))))

          :else
          (r/error {:error :invalid-value-fn-arg
                    :message (str fn-name " argument must be a collection path or filtered binding")
                    :position position
                    :value arg}))))))

(defn- parse-policy-reference
  "Parses a policy reference `[:ns/policy]` or `[:ns/policy {:params}]`.

  Returns `{:ok ASTNode}` with type `::ast/policy-reference` on success."
  [form position]
  (let [policy-kw (first form)
        params    (second form)
        ns-key    (keyword (namespace policy-kw))
        name-key  (keyword (name policy-kw))]
    (cond
      (> (count form) 2)
      (r/error {:error :invalid-policy-reference
                :message "Policy reference takes at most 1 argument (params map)"
                :position position
                :value form})

      (and params (not (map? params)))
      (r/error {:error :invalid-policy-params
                :message "Policy parameters must be a map"
                :position position
                :value params})

      :else
      (r/ok (ast/ast-node ::ast/policy-reference
                          {:namespace ns-key
                           :name name-key}
                          position
                          nil
                          (when params {:params params}))))))

(defn- parse-let-binding
  "Parses a let binding `[:let [name1 expr1 name2 expr2 ...] body]`.

  Returns `{:ok ASTNode}` with type `::ast/let-binding` on success."
  [form position]
  (cond
    (< (count form) 3)
    (r/error {:error :invalid-let
              :message ":let requires bindings vector and body"
              :position position
              :value form})

    (> (count form) 3)
    (r/error {:error :invalid-let
              :message ":let takes exactly 2 arguments: bindings and body"
              :position position
              :value form})

    :else
    (let [bindings-form (second form)
          body-form     (nth form 2)]
      (cond
        (not (vector? bindings-form))
        (r/error {:error :invalid-let-bindings
                  :message "Let bindings must be a vector"
                  :position position
                  :value bindings-form})

        (odd? (count bindings-form))
        (r/error {:error :invalid-let-bindings
                  :message "Let bindings must have even number of forms (name-value pairs)"
                  :position position
                  :value bindings-form})

        :else
        (let [pairs           (partition 2 bindings-form)
              parse-pair      (fn [[idx [bname bexpr]]]
                                (cond
                                  (not (or (symbol? bname) (keyword? bname)))
                                  (r/error {:error :invalid-let-binding-name
                                            :message "Binding name must be a symbol or keyword"
                                            :position [(first position) (+ (second position) 1 (* idx 2))]
                                            :value bname})

                                  :else
                                  (let [expr-result (parse-policy bexpr
                                                                  [(first position)
                                                                   (+ (second position) 2 (* idx 2))])]
                                    (if (r/error? expr-result)
                                      expr-result
                                      (r/ok {:name (if (symbol? bname)
                                                     (keyword bname)
                                                     bname)
                                             :expr (r/unwrap expr-result)})))))
              binding-results (r/sequence-results
                               (map-indexed (fn [idx pair]
                                              (parse-pair [idx pair]))
                                            pairs))]
          (if (r/error? binding-results)
            binding-results
            (let [body-result (parse-policy body-form
                                            [(first position) (+ (second position) 2)])]
              (if (r/error? body-result)
                body-result
                (r/ok (ast/ast-node ::ast/let-binding
                                    nil
                                    position
                                    [(r/unwrap body-result)]
                                    {:bindings (r/unwrap binding-results)}))))))))))

(defn parse-policy
  "Parses a policy DSL expression `expr` into an AST.

  The DSL supports:
  - Document accessors: `:doc/key-name`
  - Self accessors: `:self/computed-value` (from let bindings)
  - Parameter accessors: `:param/role` (policy parameters)
  - Event accessors: `:event/target-id` (trigger event data)
  - Binding accessors: `:u/field` (within quantifier bodies)
  - Function calls: `[:fn-name arg1 arg2 ...]`
  - Quantifiers: `[:forall [u :doc/users] body]`, `[:exists [t :doc/teams] body]`
  - Value functions: `[:fn/count :doc/users]`, `[:fn/count [:u :doc/users :where [...]]]`
  - Policy references: `[:auth/admin]`, `[:auth/has-role {:role \"editor\"}]`
  - Let bindings: `[:let [x :doc/value] [:= :self/x 5]]`
  - Literals: strings, numbers, keywords, etc.
  - Thunks: Clojure vars and function calls wrapped for delayed evaluation

  Takes an optional `position` vector `[start end]` for tracking location (defaults to `[0 0]`).

  Returns `{:ok ASTNode}` on success or `{:error error-map}` on failure."
  ([expr]
   (parse-policy expr [0 0]))
  ([expr position]
   (cond
     (vector? expr)
     (cond
       (empty? expr)
       (r/ok (ast/ast-node ::ast/literal expr position))

       (let-binding? expr)
       (parse-let-binding expr position)

       (policy-reference? expr)
       (parse-policy-reference expr position)

       :else
       (let [fn-name (first expr)]
         (if-not (valid-function-name? fn-name)
           (r/error {:error :invalid-function-name
                     :message (str "Function name must be a keyword or symbol, got: " (pr-str fn-name))
                     :position position
                     :value fn-name})
           (cond
             (quantifier-op? fn-name)
             (parse-quantifier fn-name (rest expr) position)

             (fn-accessor? fn-name)
             (parse-value-fn fn-name (rest expr) position)

             :else
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

(defn extract-param-keys
  "Extracts all parameter keys from a policy `ast`.

  Returns a set of keywords representing required parameters.
  Traverses the entire AST including quantifier bodies and let bindings.

      (extract-param-keys (r/unwrap (parse-policy [:= :doc/role :param/role])))
      ;=> #{:role}

      (extract-param-keys (r/unwrap (parse-policy [:and
                                                    [:= :doc/role :param/role]
                                                    [:> :doc/level :param/min-level]])))
      ;=> #{:role :min-level}"
  [ast]
  (->> (tree-seq (fn [node]
                   (or (:children node)
                       (get-in node [:metadata :bindings])
                       (get-in node [:metadata :binding :where])))
                 (fn [node]
                   (concat (:children node)
                           (map :expr (get-in node [:metadata :bindings]))
                           (when-let [where (get-in node [:metadata :binding :where])]
                             [where])))
                 ast)
       (filter #(= ::ast/param-accessor (:type %)))
       (map :value)
       (into #{})))
