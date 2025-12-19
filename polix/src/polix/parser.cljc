(ns polix.parser
  "Parser for the policy DSL.

  Transforms policy expressions (vector-based S-expressions) into AST nodes
  that can be evaluated. Supports document accessors, URI accessors, function
  calls, literals, and thunks for delayed evaluation."
  (:require
   [cats.core :as m]
   [cats.monad.either :as either]
   [polix.ast :as ast]))

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
   (or #?(:clj (var? form) :cljs false)
       (and (list? form) (seq form)))))

(defn classify-token
  "Classifies a `token` into its AST node type based on `position`.

  Returns an [[ast/ASTNode]] with the appropriate type:
  - `::ast/doc-accessor` for document accessors
  - `::ast/uri` for URI accessors
  - `::ast/thunk` for thunkable forms
  - `::ast/literal` for all other values"
  [token position]
  (cond
    (doc-accessor? token)
    (ast/ast-node ::ast/doc-accessor (keyword (name token)) position)

    (uri-accessor? token)
    (ast/ast-node ::ast/uri (keyword (name token)) position)

    (thunkable? token)
    (ast/ast-node ::ast/thunk (fn [] token) position)

    :else
    (ast/ast-node ::ast/literal token position)))

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
       (m/return either/context (ast/ast-node ::ast/literal expr position))
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
                     (m/return (ast/ast-node ::ast/function-call
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
       (filter #(= ::ast/doc-accessor (:type %)))
       (map :value)
       (into #{})))
