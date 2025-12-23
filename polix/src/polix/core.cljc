(ns polix.core
  "Core functionality for polix - a DSL for writing declarative policies.

  Polix provides a vector-based DSL for defining policies that evaluate
  against documents. Policies support document accessors (`:doc/key`),
  function calls, and literals.

  ## Quick Start

  Define a policy:

      (require '[polix.core :as polix])

      (polix/defpolicy AdminOnly
        \"Only admins can access\"
        [:= :doc/role \"admin\"])

  Unify a policy with a document:

      (polix/unify (:ast AdminOnly) {:role \"admin\"})
      ;=> {}  ; satisfied

      (polix/unify (:ast AdminOnly) {:role \"guest\"})
      ;=> nil  ; contradiction

      (polix/unify (:ast AdminOnly) {})
      ;=> {[:role] [[:= \"admin\"]]}  ; residual

  ## Compiled Policies

  For optimized evaluation with constraint merging and simplification:

      (def checker (polix/compile-policies
                     [[:= :doc/role \"admin\"]
                      [:> :doc/level 5]]))

      (checker {:role \"admin\" :level 10})  ;=> {}
      (checker {:role \"guest\"})            ;=> nil
      (checker {:role \"admin\"})            ;=> {[:level] [[:> 5]]}

  ## Result Types

  Unification and compiled policies return one of three result types:

  - `{}` (empty map) — satisfied, all constraints met
  - `{:path [constraints]}` — residual, some constraints remain
  - `nil` — contradiction, no document can satisfy the policy

  Use [[satisfied?]], [[residual?]], and [[contradiction?]] predicates
  to check result types.

  ## Main Concepts

  - **Document**: Any associative data structure (map, record, etc.)
  - **Policy**: Declarative rule defined via `defpolicy`
  - **AST**: Abstract syntax tree representation of policies
  - **Unify**: Evaluates policy against document, returns residual
  - **Compiler**: Merges and optimizes policies before evaluation

  ## Namespaces

  The implementation is split across multiple namespaces:

  - [[polix.ast]] - AST data structures
  - [[polix.parser]] - Policy DSL parser
  - [[polix.unify]] - Residual-based unification engine
  - [[polix.residual]] - Result type predicates and combinators
  - [[polix.negate]] - AST negation
  - [[polix.policy]] - Policy definition macros
  - [[polix.compiler]] - Policy compilation and constraint solving"
  (:require
   [polix.ast :as ast]
   [polix.compiler :as compiler]
   [polix.engine :as engine]
   [polix.negate :as negate]
   [polix.parser :as parser]
   [polix.policy :as policy]
   [polix.residual :as res]
   [polix.result :as result]
   [polix.unify :as unify]))

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
(def thunkable? parser/thunkable?)
(def classify-token parser/classify-token)

;;; ---------------------------------------------------------------------------
;;; Unification API (new)
;;; ---------------------------------------------------------------------------

(def unify
  "Unifies a policy with a document, returning a residual.

  Takes a policy (AST, constraint set, or policy expression vector) and
  a document. Returns:

  - `{}` — satisfied (all constraints met)
  - `{:path [constraints]}` — residual (some constraints remain)
  - `nil` — contradiction (no document can satisfy)

  Example:

      (unify [:= :doc/role \"admin\"] {:role \"admin\"})
      ;=> {}

      (unify [:= :doc/role \"admin\"] {:role \"guest\"})
      ;=> nil

      (unify [:= :doc/role \"admin\"] {})
      ;=> {[:role] [[:= \"admin\"]]}"
  unify/unify)

;;; ---------------------------------------------------------------------------
;;; Residual Predicates and Combinators
;;; ---------------------------------------------------------------------------

(def satisfied?
  "Returns true if result represents a satisfied policy (empty residual).

  A satisfied result is `{}` (empty map)."
  res/satisfied?)

(def residual?
  "Returns true if result is a residual (non-empty constraint map).

  A residual contains path keys with remaining constraints."
  res/residual?)

(def contradiction?
  "Returns true if result represents a contradiction.

  A contradiction is `nil`."
  res/contradiction?)

(def merge-residuals
  "Merges two residuals with AND semantics.

  Returns:
  - `nil` if either is a contradiction
  - The merged residual otherwise"
  res/merge-residuals)

(def combine-residuals
  "Combines two residuals with OR semantics.

  Returns:
  - `{}` if either is satisfied
  - Combined residual otherwise"
  res/combine-residuals)

;;; ---------------------------------------------------------------------------
;;; AST Negation
;;; ---------------------------------------------------------------------------

(def negate
  "Negates an AST node, returning its logical complement.

  Example:

      (negate [:= :doc/role \"admin\"])
      ;=> [:!= :doc/role \"admin\"]

      (negate [:and [:= :doc/a 1] [:= :doc/b 2]])
      ;=> [:or [:!= :doc/a 1] [:!= :doc/b 2]]"
  negate/negate)

;;; ---------------------------------------------------------------------------
;;; Legacy API (deprecated)
;;; ---------------------------------------------------------------------------

(def ^{:deprecated "Use unify instead"}
  evaluate
  "DEPRECATED: Use [[unify]] instead.

  Evaluates an AST against a document. Returns true/false/{:residual ...}.

  This function uses the old result format. For new code, use [[unify]]
  which returns `{}`, `nil`, or `{:path [constraints]}`."
  engine/evaluate)

(def ^{:deprecated "Use (unify policy {}) or (unify (negate policy) {}) instead"}
  implied
  "DEPRECATED: Use `(unify policy {})` or `(unify (negate policy) {})` instead.

  Extracts implied constraints from a policy.

  For extracting constraints when result is true, use `(unify policy {})`.
  For extracting constraints when result is false, use `(unify (negate policy) {})`."
  engine/implied)

(def ^{:deprecated "Use residual? from polix.residual instead"}
  legacy-residual?
  "DEPRECATED: Use [[residual?]] for new residual format.

  Checks for old-format residual `{:residual ...}`."
  engine/residual?)

(def complex?
  "Returns true if result contains complex (non-simplifiable) constraints."
  engine/complex?)

(def result-type
  "Returns the type of an evaluation result.

  Returns :satisfied, :contradicted, :residual, or :complex.
  Note: Uses old format (true/false/{:residual})."
  engine/result-type)

;; Re-export policy constructors
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
(def function-call ::ast/function-call)
(def thunk ::ast/thunk)

;; Re-export compiler functions
(def compile-policies compiler/compile-policies)
(def compile-policies-legacy compiler/compile-policies-legacy)
(def merge-policies compiler/merge-policies)
(def residual->constraints compiler/residual->constraints)
(def result->policy compiler/result->policy)
