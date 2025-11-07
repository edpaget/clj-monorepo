(ns polix.core
  "Core functionality for polix - a DSL for writing declarative policies.

  Polix provides a vector-based DSL for defining policies that evaluate
  against documents. Policies support document accessors (`:doc/key`),
  URI accessors (`:uri/resource`), function calls, and literals.

  ## Quick Start

  Define a policy:

      (require '[polix.core :as polix])

      (polix/defpolicy AdminOnly
        \"Only admins can access\"
        [:= :doc/role \"admin\"])

  Evaluate a policy:

      (let [document (polix/map-document {:role \"admin\"})
            result (polix/evaluate (:ast AdminOnly) document)]
        (cats.core/extract result))
        ;=> true

  ## Main Concepts

  - **Document**: Key-value store for policy evaluation data
  - **Policy**: Declarative rule defined via `defpolicy`
  - **AST**: Abstract syntax tree representation of policies
  - **Evaluator**: Evaluates AST nodes against documents

  ## Namespaces

  The implementation is split across multiple namespaces:

  - [[polix.document]] - Document protocol and implementations
  - [[polix.ast]] - AST data structures
  - [[polix.parser]] - Policy DSL parser
  - [[polix.evaluator]] - Evaluation engine
  - [[polix.policy]] - Policy definition macros"
  (:require
   [polix.ast :as ast]
   [polix.document :as document]
   [polix.evaluator :as evaluator]
   [polix.parser :as parser]
   [polix.policy :as policy])
  (:import
   (polix.ast ASTNode)
   (polix.document MapDocument)
   (polix.evaluator DefaultEvaluator)
   (polix.policy Policy)))

;; Re-export Document protocol
(def Document document/Document)
(def doc-get document/doc-get)
(def doc-keys document/doc-keys)
(def doc-project document/doc-project)
(def doc-merge document/doc-merge)

;; Re-export Document functions
(def doc-contains? document/doc-contains?)

;; Re-export Document constructors
(def map-document document/map-document)
(def ->MapDocument document/->MapDocument)
(def map->MapDocument document/map->MapDocument)

;; Re-export AST constructors
(def ast-node ast/ast-node)
(def ->ASTNode ast/->ASTNode)
(def map->ASTNode ast/map->ASTNode)

;; Re-export parser functions
(def parse-policy parser/parse-policy)
(def extract-doc-keys parser/extract-doc-keys)
(def doc-accessor? parser/doc-accessor?)
(def uri-accessor? parser/uri-accessor?)
(def thunkable? parser/thunkable?)
(def classify-token parser/classify-token)

;; Re-export evaluator
(def Evaluator evaluator/Evaluator)
(def eval-node evaluator/eval-node)
(def evaluate evaluator/evaluate)
(def default-evaluator evaluator/default-evaluator)
(def ->DefaultEvaluator evaluator/->DefaultEvaluator)
(def map->DefaultEvaluator evaluator/map->DefaultEvaluator)

;; Re-export policy constructors (Policy record is imported above)
(def ->Policy policy/->Policy)
(def map->Policy policy/map->Policy)

;; Re-export macro with proper syntax
(defmacro defpolicy
  "Defines a policy with a `name`, optional `docstring`, and policy expression.

  See [[polix.policy/defpolicy]] for full documentation."
  [name & args]
  `(policy/defpolicy ~name ~@args))

;; Re-export AST node type keywords for convenience
(def literal ::ast/literal)
(def doc-accessor ::ast/doc-accessor)
(def uri ::ast/uri)
(def function-call ::ast/function-call)
(def thunk ::ast/thunk)
