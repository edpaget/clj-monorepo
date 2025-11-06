(ns polix.core
  "Core functionality for polix - a DSL for writing declarative policies"
  (:require
   [cats.core :as m]
   [cats.monad.either :as either]
   [clojure.walk :as walk]))

(defprotocol Document
  "Protocol for key-value document interface.

  A Document provides access to key-value data and can be backed by
  static data structures or dynamic sources like databases."

  (doc-get [this key]
    "Returns the value associated with `key`, or `nil` if not found.")

  (doc-keys [this]
    "Returns a collection of all keys available in this document.")

  (doc-project [this ks]
    "Returns a new document containing only the specified keys from `ks`.")

  (doc-merge [this other]
    "Merges this document with `other`, with `other`'s values taking precedence.

    Returns a new merged document."))

(defrecord MapDocument [data]
  Document

  (doc-get [_ key]
    (get data key))

  (doc-keys [_]
    (keys data))

  (doc-project [_ ks]
    (->MapDocument (select-keys data ks)))

  (doc-merge [_ other]
    (->MapDocument (merge data (:data other)))))

(defn map-document
  "Creates a [[Document]] from a map `m`.

  Returns a `MapDocument` wrapping the provided map."
  [m]
  (->MapDocument m))

(defrecord ASTNode [type value position children]
  Object
  (toString [_]
    (str "{:type " type " :value " value " :position " position " :children " children "}")))

(defn ast-node
  "Creates an AST node with position tracking.

  Takes a `type` (one of `::literal`, `::doc-accessor`, `::uri`, `::function-call`,
  or `::thunk`), a `value`, a `position` vector `[start-index end-index]` in the
  original expression, and optional `children` (vector of child AST nodes).

  Returns an `ASTNode` record."
  ([type value position]
   (->ASTNode type value position nil))
  ([type value position children]
   (->ASTNode type value position children)))

(defn doc-accessor?
  "Returns `true` if `k` is a document accessor keyword.

  Document accessors are namespaced keywords with namespace `\"doc\"`,
  such as `:doc/actor-role`."
  [k]
  (and (keyword? k)
       (= "doc" (namespace k))))

(defn uri-accessor?
  "Returns `true` if `k` is a URI accessor keyword.

  URI accessors are namespaced keywords with namespace `\"uri\"`,
  such as `:uri/resource`."
  [k]
  (and (keyword? k)
       (= "uri" (namespace k))))

(defn thunkable?
  "Returns `true` if `form` should be wrapped in a thunk for delayed evaluation.

  Vars and non-empty lists (function calls) should be thunked."
  [form]
  (boolean
   (or (var? form)
       (and (list? form) (seq form)))))

(defn classify-token
  "Classifies a `token` into its AST node type based on `position`.

  Returns an [[ASTNode]] with the appropriate type:
  - `::doc-accessor` for document accessors
  - `::uri` for URI accessors
  - `::thunk` for thunkable forms
  - `::literal` for all other values"
  [token position]
  (cond
    (doc-accessor? token)
    (ast-node ::doc-accessor (keyword (name token)) position)

    (uri-accessor? token)
    (ast-node ::uri (keyword (name token)) position)

    (thunkable? token)
    (ast-node ::thunk (fn [] token) position)

    :else
    (ast-node ::literal token position)))

(defn valid-function-name?
  "Returns `true` if `v` is a valid function name for the policy DSL.

  Valid function names are keywords or symbols."
  [v]
  (or (keyword? v) (symbol? v)))

(defn parse-policy
  "Parses a policy DSL expression `expr` into an AST with Either monad error handling.

  The DSL supports:
  - Document accessors: `:doc/key-name`
  - URI accessors: `:uri/uri`
  - Function calls: `[:fn-name arg1 arg2 ...]`
  - Literals: strings, numbers, keywords, etc.
  - Thunks: Clojure vars and function calls wrapped for delayed evaluation

  Takes an optional `position` vector `[start end]` for tracking location (defaults to `[0 0]`).

  Returns `Either[error-map, ASTNode]` - `Right` with AST on success, `Left` with error on failure."
  ([expr]
   (parse-policy expr [0 0]))
  ([expr position]
   (cond
     (vector? expr)
     (if (empty? expr)
       (m/return either/context (ast-node ::literal expr position))
       (let [fn-name (first expr)]
         (if-not (valid-function-name? fn-name)
           (either/left {:error :invalid-function-name
                         :message (str "Function name must be a keyword or symbol, got: " (pr-str fn-name))
                         :position position
                         :value fn-name})
           (let [args (rest expr)]
             (m/mlet [parsed-args (m/sequence
                                   (map-indexed
                                    (fn [idx arg]
                                      (let [arg-pos [(first position) (+ (second position) idx 1)]]
                                        (parse-policy arg arg-pos)))
                                    args))]
                     (m/return (ast-node ::function-call
                                         fn-name
                                         position
                                         (vec parsed-args))))))))

     :else
     (m/return either/context (classify-token expr position)))))

(defn extract-doc-keys
  "Extracts all document accessor keys from a policy `ast`.

  Returns a set of document accessor keywords without the `:doc/` namespace."
  [ast]
  (->> (tree-seq :children :children ast)
       (filter #(= ::doc-accessor (:type %)))
       (map :value)
       (into #{})))

(defrecord Policy [name docstring schema ast])

(defmacro defpolicy
  "Defines a policy with a `name`, optional `docstring`, and policy expression.

  A policy is a declarative rule that evaluates to boolean true/false.
  The macro parses the policy expression into an AST and extracts the
  required document schema.

  Examples:

      (defpolicy MyPolicy
        \"Optional docstring\"
        [:= :doc/actor-role \"admin\"])

      (defpolicy AnotherPolicy
        [:or [:= :doc/role \"admin\"]
             [:= :doc/role \"user\"]])

  Returns a `def` form that creates a [[Policy]] record, or throws on parse error."
  [name & args]
  (let [[docstring expr] (if (string? (first args))
                           [(first args) (second args)]
                           [nil (first args)])
        ast (-> (parse-policy expr)
                (either/branch-left

                 (fn [error]
                   (throw (ex-info (str "Policy parse error: " (:message error))
                                   (assoc error :policy-name name)))))
                (m/extract))
        schema (extract-doc-keys ast)]
    `(def ~name
       ~@(when docstring [docstring])
       (->Policy '~name ~docstring ~schema '~ast))))

(defprotocol Evaluator
  "Protocol for evaluating AST nodes.

  Evaluators receive an AST `node`, a [[Document]], and optional `context`,
  and return an `Either[error, value]`."

  (eval-node [this node document context]
    "Evaluates an AST `node` with the given `document` and `context`.

    Returns `Either[error-map, value]` - `Right` with result on success, `Left` with error on failure."))

(defmulti default-eval
  "Default evaluation multimethod dispatching on AST node type.

  Does not recurse - recursion is handled by the [[evaluate]] function.
  Takes a `node` (with children already evaluated to thunks), a [[Document]],
  and a `context` map.

  Returns `Either[error-map, value]`."
  (fn [node _document _context] (:type node)))

(defmethod default-eval ::literal
  [node _document _context]
  (m/return either/context (:value node)))

(defmethod default-eval ::doc-accessor
  [node document context]
  (let [key (:value node)
        value (doc-get document key)]
    (if (nil? value)
      (either/left {:error :missing-document-key
                    :message (str "Document missing required key: " key)
                    :position (:position node)
                    :key key})
      (m/return either/context value))))

(defmethod default-eval ::uri
  [node _document context]
  (let [uri-value (:uri context)]
    (if (nil? uri-value)
      (either/left {:error :missing-uri
                    :message "URI not provided in evaluation context"
                    :position (:position node)})
      (m/return either/context uri-value))))

(defmethod default-eval ::thunk
  [node _document _context]
  (try
    (let [thunk-fn (:value node)
          result (thunk-fn)]
      (m/return either/context result))
    (catch Exception e
      (either/left {:error :thunk-evaluation-error
                    :message (str "Error evaluating thunk: " (.getMessage e))
                    :position (:position node)
                    :exception e}))))

(defmethod default-eval ::function-call
  [node _document context]
  (let [fn-name (:value node)
        arg-thunks (:children node)]
    (if-let [operator (get (:environment context) fn-name)]
      (try
        (let [evaluated-args (map #(m/extract (%)) arg-thunks)]
          (m/return (apply operator evaluated-args)))
        (catch Exception e
          (either/left {:error :operator-error
                        :message (str "Error applying operator " fn-name ": " (.getMessage e))
                        :position (:position node)
                        :operator fn-name
                        :exception e})))
      (either/left {:error :unknown-operator
                    :message (str "Unknown operator: " fn-name)
                    :position (:position node)
                    :operator fn-name}))))

(defrecord DefaultEvaluator []
  Evaluator
  (eval-node [_this node document context]
    (default-eval node document context)))

(def default-evaluator
  "The default [[Evaluator]] instance."
  (->DefaultEvaluator))

(defn evaluate
  "Evaluates an AST node with a [[Document]] and optional context.

  Uses `postwalk` to traverse the AST, converting each node's children into
  lazy thunks that return evaluated values. Does not recurse in evaluators.

  Takes:
  - `ast` - The AST node to evaluate (typically from a [[Policy]])
  - `document` - The [[Document]] to evaluate against
  - `evaluator` - Optional [[Evaluator]] (defaults to [[default-evaluator]])
  - `context` - Optional context map with `:uri`, `:environment`, etc.

  Returns `Either[error-map, value]` - `Right` with result on success, `Left` with error on failure."
  ([ast document]
   (evaluate ast document default-evaluator {}))
  ([ast document evaluator]
   (evaluate ast document evaluator {}))
  ([ast document evaluator context]
   (let [eval-context (assoc context :evaluator evaluator)
         result (walk/postwalk
                 (fn [node]
                   (if (and (map? node) (:type node))
                     (let [children (:children node)]
                       (if children
                         (let [child-thunks (map (fn [child-result]
                                                   (fn []
                                                     (if (either/right? child-result)
                                                       child-result
                                                       (throw (ex-info "Child evaluation failed"
                                                                       (m/extract child-result))))))
                                                 children)
                               node-with-thunks (assoc node :children (lazy-seq child-thunks))]
                           (eval-node evaluator node-with-thunks document eval-context))
                         (eval-node evaluator node document eval-context)))
                     node))
                 ast)]
     result)))
