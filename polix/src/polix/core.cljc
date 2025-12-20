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

      (let [result (polix/evaluate (:ast AdminOnly) {:role \"admin\"})]
        (polix/unwrap result))
        ;=> true

  ## Compiled Policies

  For optimized evaluation with three-valued logic:

      (def checker (polix/compile-policies
                     [[:= :doc/role \"admin\"]
                      [:> :doc/level 5]]))

      (checker {:role \"admin\" :level 10})  ;=> true
      (checker {:role \"guest\"})            ;=> false
      (checker {:role \"admin\"})            ;=> {:residual {:level [[:> 5]]}}

  ## Main Concepts

  - **Document**: Any associative data structure (map, record, etc.)
  - **Policy**: Declarative rule defined via `defpolicy`
  - **AST**: Abstract syntax tree representation of policies
  - **Evaluator**: Evaluates AST nodes against documents
  - **Compiler**: Merges and optimizes policies for three-valued evaluation

  ## Namespaces

  The implementation is split across multiple namespaces:

  - [[polix.ast]] - AST data structures
  - [[polix.parser]] - Policy DSL parser
  - [[polix.evaluator]] - Evaluation engine
  - [[polix.policy]] - Policy definition macros
  - [[polix.compiler]] - Policy compilation and constraint solving"
  (:require
   [polix.ast :as ast]
   [polix.compiler :as compiler]
   [polix.evaluator :as evaluator]
   [polix.parser :as parser]
   [polix.policy :as policy]
   [polix.result :as result]))

;; Re-export AST constructors
(def ast-node ast/ast-node)
(def ->ASTNode ast/->ASTNode)
(def map->ASTNode ast/map->ASTNode)

;; Re-export result functions
(def ok result/ok)
(def error result/error)
(def ok? result/ok?)
(def error? result/error?)
(def unwrap result/unwrap)

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

;; Re-export compiler functions
(def compile-policies compiler/compile-policies)
(def merge-policies compiler/merge-policies)
(def residual->constraints compiler/residual->constraints)
(def result->policy compiler/result->policy)
