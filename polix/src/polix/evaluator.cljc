(ns polix.evaluator
  "Policy evaluation engine.

  Evaluates AST nodes against documents and contexts, transforming parsed
  policies into concrete values. Uses the Either monad for error handling
  and supports pluggable evaluators via the Evaluator protocol."
  (:require
   [cats.core :as m]
   [cats.monad.either :as either]
   [clojure.walk :as walk]
   [polix.ast :as ast]
   [polix.document :as doc]))

(defprotocol Evaluator
  "Protocol for evaluating AST nodes.

  Evaluators receive an AST `node`, a [[doc/Document]], and optional `context`,
  and return an `Either[error, value]`."

  (eval-node [this node document context]
    "Evaluates an AST `node` with the given `document` and `context`.

    Returns `Either[error-map, value]` - `Right` with result on success, `Left` with error on failure."))

(defmulti default-eval
  "Default evaluation multimethod dispatching on AST node type.

  Does not recurse - recursion is handled by the [[evaluate]] function.
  Takes a `node` (with children already evaluated to thunks), a [[doc/Document]],
  and a `context` map.

  Returns `Either[error-map, value]`."
  (fn [node _document _context] (:type node)))

(defmethod default-eval ::ast/literal
  [node _document _context]
  (m/return either/context (:value node)))

(defmethod default-eval ::ast/doc-accessor
  [node document _context]
  (let [key (:value node)]
    (if (doc/doc-contains? document key)
      (m/return either/context (doc/doc-get document key))
      (either/left {:error :missing-document-key
                    :message (str "Document missing required key: " key)
                    :position (:position node)
                    :key key}))))

(defmethod default-eval ::ast/uri
  [node _document context]
  (let [uri-value (:uri context)]
    (if (nil? uri-value)
      (either/left {:error :missing-uri
                    :message "URI not provided in evaluation context"
                    :position (:position node)})
      (m/return either/context uri-value))))

(defmethod default-eval ::ast/thunk
  [node _document _context]
  (try
    (let [thunk-fn (:value node)
          result   (thunk-fn)]
      (m/return either/context result))
    (catch #?(:clj Exception :cljs :default) e
      (either/left {:error :thunk-evaluation-error
                    :message (str "Error evaluating thunk: " (ex-message e))
                    :position (:position node)
                    :exception e}))))

(defmethod default-eval ::ast/function-call
  [node _document context]
  (let [fn-name    (:value node)
        arg-thunks (:children node)]
    (if-let [operator (get (:environment context) fn-name)]
      (try
        (let [evaluated-args (map #(m/extract (%)) arg-thunks)]
          (m/return (apply operator evaluated-args)))
        (catch #?(:clj Exception :cljs :default) e
          (either/left {:error :operator-error
                        :message (str "Error applying operator " fn-name ": " (ex-message e))
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
  "Evaluates an AST node with a [[doc/Document]] and optional context.

  Uses `postwalk` to traverse the AST, converting each node's children into
  lazy thunks that return evaluated values. Does not recurse in evaluators.

  Takes:
  - `ast` - The AST node to evaluate (typically from a [[polix.policy/Policy]])
  - `document` - The [[doc/Document]] to evaluate against
  - `evaluator` - Optional [[Evaluator]] (defaults to [[default-evaluator]])
  - `context` - Optional context map with `:uri`, `:environment`, etc.

  Returns `Either[error-map, value]` - `Right` with result on success, `Left` with error on failure."
  ([ast document]
   (evaluate ast document default-evaluator {}))
  ([ast document evaluator]
   (evaluate ast document evaluator {}))
  ([ast document evaluator context]
   (let [eval-context (assoc context :evaluator evaluator)
         result       (walk/postwalk
                       (fn [node]
                         (if (and (map? node) (:type node))
                           (let [children (:children node)]
                             (if children
                               (let [child-thunks     (map (fn [child-result]
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
