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
    "Get the value associated with key.

    Args:
      this: The document
      key: The key to look up

    Returns:
      The value associated with key, or nil if not found")

  (doc-keys [this]
    "Get all available keys in this document.

    Args:
      this: The document

    Returns:
      A collection of all keys available in the document")

  (doc-project [this ks]
    "Project the document to only include specified keys.

    Args:
      this: The document
      ks: Collection of keys to project

    Returns:
      A new document containing only the specified keys")

  (doc-merge [this other]
    "Merge this document with another, left-to-right.

    Args:
      this: The first document (left)
      other: The second document (right)

    Returns:
      A new merged document where other's values override this's values"))

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
  "Create a Document from a map.

  Args:
    m: A map of key-value pairs

  Returns:
    A MapDocument wrapping the provided map"
  [m]
  (->MapDocument m))

(defrecord ASTNode [type value position children]
  Object
  (toString [_]
    (str "{:type " type " :value " value " :position " position " :children " children "}")))

(defn ast-node
  "Create an AST node with position tracking.

  Args:
    type: The node type (::literal, ::doc-accessor, ::uri, ::function-call, ::thunk)
    value: The node value
    position: Vector of [start-index end-index] in original expression
    children: Optional vector of child AST nodes

  Returns:
    An ASTNode record"
  ([type value position]
   (->ASTNode type value position nil))
  ([type value position children]
   (->ASTNode type value position children)))

(defn doc-accessor?
  "Check if a keyword is a document accessor.

  Document accessors are namespaced keywords starting with 'doc'.

  Args:
    k: The value to check

  Returns:
    true if k is a document accessor keyword"
  [k]
  (and (keyword? k)
       (= "doc" (namespace k))))

(defn uri-accessor?
  "Check if a keyword is a URI accessor.

  URI accessors are namespaced keywords starting with 'uri'.

  Args:
    k: The value to check

  Returns:
    true if k is a URI accessor keyword"
  [k]
  (and (keyword? k)
       (= "uri" (namespace k))))

(defn thunkable?
  "Check if a form should be wrapped in a thunk.

  Vars and lists (function calls) should be thunked to delay evaluation.

  Args:
    form: The form to check

  Returns:
    true if form should be wrapped in a thunk"
  [form]
  (boolean
   (or (var? form)
       (and (list? form) (seq form)))))

(defn classify-token
  "Classify a token into its AST node type.

  Args:
    token: The token to classify
    position: The position [start end] of the token

  Returns:
    An ASTNode representing the classified token"
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
  "Check if a value is a valid function name for the policy DSL.

  Valid function names are keywords or symbols.

  Args:
    v: The value to check

  Returns:
    true if v is a valid function name"
  [v]
  (or (keyword? v) (symbol? v)))

(defn parse-policy
  "Parse a policy DSL expression into an AST with Either monad error handling.

  The DSL supports:
  - Document accessors: :doc/key-name
  - URI accessors: :uri/uri
  - Function calls: [:fn-name arg1 arg2 ...]
  - Literals: strings, numbers, keywords, etc.
  - Thunks: Clojure vars and function calls wrapped for delayed eval

  Args:
    expr: The policy expression (vector s-expression)
    position: Optional starting position (default [0 0])

  Returns:
    Either[error-map, ASTNode] - Right with AST on success, Left with error on failure"
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
  "Extract all document accessor keys from a policy AST.

  Args:
    ast: The policy AST node

  Returns:
    A set of document accessor keywords (without :doc/ namespace)"
  [ast]
  (->> (tree-seq :children :children ast)
       (filter #(= ::doc-accessor (:type %)))
       (map :value)
       (into #{})))

(defrecord Policy [name docstring schema ast])

(defmacro defpolicy
  "Define a policy with a name, optional docstring, and policy expression.

  A policy is a declarative rule that evaluates to boolean true/false.
  The macro parses the policy expression into an AST and extracts the
  required document schema.

  Usage:
    (defpolicy MyPolicy
      \"Optional docstring\"
      [:= :doc/actor-role \"admin\"])

    (defpolicy AnotherPolicy
      [:or [:= :doc/role \"admin\"]
           [:= :doc/role \"user\"]])

  Args:
    name: The policy name (symbol)
    docstring: Optional docstring (string)
    expr: The policy expression (vector DSL)

  Returns:
    A def form that creates a Policy record, or throws on parse error"
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
  
  Evaluators receive an AST node, a document, and optional context,
  and return an Either[error, value]."

  (eval-node [this node document context]
    "Evaluate an AST node with the given document and context.
    
    Args:
      this: The evaluator
      node: The AST node to evaluate
      document: The Document to evaluate against
      context: Optional evaluation context map
      
    Returns:
      Either[error-map, value] - Right with result on success, Left with error on failure"))

(defmulti default-eval
  "Default evaluation multimethod dispatching on AST node type.
  
  Does not recurse - recursion is handled by the evaluate function.
  
  Args:
    node: The AST node to evaluate (children already evaluated to thunks)
    document: The Document to evaluate against
    context: Evaluation context map
    
  Returns:
    Either[error-map, value]"
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
  "The default evaluator instance."
  (->DefaultEvaluator))

(defn evaluate
  "Evaluate an AST node with a document and optional context.
  
  Uses postwalk to traverse the AST, converting each node's children into
  lazy thunks that return evaluated values. Does not recurse in evaluators.
  
  Args:
    ast: The AST node to evaluate (typically from a Policy)
    document: The Document to evaluate against
    evaluator: Optional evaluator (defaults to default-evaluator)
    context: Optional context map with :uri, :environment, etc.
    
  Returns:
    Either[error-map, value] - Right with result on success, Left with error on failure"
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
                                                                       (either/extract child-result))))))
                                                 children)
                               node-with-thunks (assoc node :children (lazy-seq child-thunks))]
                           (eval-node evaluator node-with-thunks document eval-context))
                         (eval-node evaluator node document eval-context)))
                     node))
                 ast)]
     result)))
