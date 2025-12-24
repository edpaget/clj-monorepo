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
      ;=> {[:role] [[:conflict [:= \"admin\"] \"guest\"]]}  ; conflict

      (polix/unify (:ast AdminOnly) {})
      ;=> {[:role] [[:= \"admin\"]]}  ; open residual

  ## Compiled Policies

  For optimized evaluation with constraint merging and simplification:

      (def checker (polix/compile-policies
                     [[:= :doc/role \"admin\"]
                      [:> :doc/level 5]]))

      (checker {:role \"admin\" :level 10})  ;=> {}
      (checker {:role \"guest\"})            ;=> {[:role] [[:conflict [:= \"admin\"] \"guest\"]]}
      (checker {:role \"admin\"})            ;=> {[:level] [[:> 5]]}

  ## Result Types

  Unification and compiled policies return one of three result types:

  - `{}` (empty map) — satisfied, all constraints met
  - `{:path [constraints]}` — open residual, awaiting more data
  - `{:path [[:conflict C witness]]}` — conflict, constraint violated

  Use [[satisfied?]], [[residual?]], [[has-conflicts?]], and [[open-residual?]]
  predicates to check result types.

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
  - [[polix.compiler]] - Policy compilation and constraint solving
  - [[polix.registry]] - Namespace registry for policy resolution
  - [[polix.loader]] - Module loading with dependency resolution"
  (:require
   [clojure.set :as set]
   [polix.ast :as ast]
   [polix.compiler :as compiler]
   [polix.loader :as loader]
   [polix.negate :as negate]
   [polix.parser :as parser]
   [polix.policy :as policy]
   [polix.registry :as registry]
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
  - `{:path [constraints]}` — open residual (awaiting more data)
  - `{:path [[:conflict C w]]}` — conflict (constraint violated by witness w)

  Example:

      (unify [:= :doc/role \"admin\"] {:role \"admin\"})
      ;=> {}

      (unify [:= :doc/role \"admin\"] {:role \"guest\"})
      ;=> {[:role] [[:conflict [:= \"admin\"] \"guest\"]]}

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

(def has-conflicts?
  "Returns true if result contains conflict constraints.

  A conflict indicates a constraint was evaluated against concrete data
  and failed. Use this instead of checking for nil."
  res/has-conflicts?)

(def open-residual?
  "Returns true if result is an open residual (no conflicts).

  An open residual contains constraints awaiting evaluation but no
  definite failures."
  res/open-residual?)

(def all-conflicts?
  "Returns true if all constraints in the residual are conflicts.

  Used to determine if NOT of a fully-conflicted residual should be satisfied."
  res/all-conflicts?)

(def conflict
  "Creates a conflict constraint tuple.

      (conflict [:< 10] 11)
      ;=> [:conflict [:< 10] 11]"
  res/conflict)

(def conflict?
  "Returns true if x is a conflict constraint tuple."
  res/conflict?)

(def conflict-constraint
  "Extracts the inner constraint from a conflict."
  res/conflict-constraint)

(def conflict-witness
  "Extracts the witness value from a conflict."
  res/conflict-witness)

(def merge-residuals
  "Merges two residuals with AND semantics.

  Returns:
  - `nil` if either is nil (legacy)
  - The merged residual otherwise"
  res/merge-residuals)

(def combine-residuals
  "Combines two residuals with OR semantics.

  Returns:
  - `{}` if either is satisfied
  - Combined residual otherwise"
  res/combine-residuals)

(def conflict-residual
  "Creates a residual with a single conflict constraint.

      (conflict-residual [:x] [:< 10] 15)
      ;=> {[:x] [[:conflict [:< 10] 15]]}"
  res/conflict-residual)

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
(def merge-policies compiler/merge-policies)
(def residual->constraints compiler/residual->constraints)
(def result->policy compiler/result->policy)

;;; ---------------------------------------------------------------------------
;;; Registry API
;;; ---------------------------------------------------------------------------

(def create-registry
  "Creates a new registry with built-in namespace entries.

  The registry manages namespace resolution for policy evaluation,
  including document accessors (`:doc/`), function references (`:fn/`),
  and user-defined modules.

  Example:

      (create-registry)
      ;=> #RegistryRecord{:entries {...} :version 0}"
  registry/create-registry)

(def register-module
  "Registers a module namespace in the registry.

  Modules define named policies that can be referenced by other modules.
  Returns a new registry with the module added.

  Example:

      (-> (create-registry)
          (register-module :auth {:policies {:admin [:= :doc/role \"admin\"]}}))"
  registry/register-module)

(def register-alias
  "Registers an alias from one namespace to another.

  Returns a new registry with the alias added.

  Example:

      (-> (create-registry)
          (register-alias :a :auth))"
  registry/register-alias)

(def resolve-namespace
  "Resolves a namespace key to its entry, following aliases.

  Returns the namespace entry or nil if not found."
  registry/resolve-namespace)

(def resolve-policy
  "Resolves a policy from a module namespace.

  Returns the policy AST or nil if not found.

  Example:

      (resolve-policy registry :auth :admin)
      ;=> [:= :doc/role \"admin\"]"
  registry/resolve-policy)

(def module-namespaces
  "Returns the set of user-defined module namespaces in the registry."
  registry/module-namespaces)

;;; ---------------------------------------------------------------------------
;;; Module Loader API
;;; ---------------------------------------------------------------------------

(def load-module
  "Loads a single module definition into a registry.

  Returns the updated registry. Does not validate imports."
  loader/load-module)

(def load-modules
  "Loads multiple modules into a registry with dependency resolution.

  Validates all module definitions, checks for circular imports, verifies
  that all imports exist, and loads modules in topological order
  (dependencies first).

  Returns `{:ok registry}` on success, `{:error error-map}` on failure.

  Example:

      (load-modules (create-registry)
                    [{:namespace :common
                      :policies {:active [:= :doc/status \"active\"]}}
                     {:namespace :auth
                      :imports [:common]
                      :policies {:admin [:= :doc/role \"admin\"]}}])"
  loader/load-modules)

(def detect-cycle
  "Detects if a dependency graph contains a cycle.

  Returns nil if no cycle, or a vector representing the cycle path."
  loader/detect-cycle)

(def topological-sort
  "Returns nodes in dependency order (dependencies first).

  Uses DFS-based topological sort."
  loader/topological-sort)

;;; ---------------------------------------------------------------------------
;;; Parser Predicates (new)
;;; ---------------------------------------------------------------------------

(def self-accessor?
  "Returns true if keyword is a self-accessor (`:self/key`)."
  parser/self-accessor?)

(def param-accessor?
  "Returns true if keyword is a parameter accessor (`:param/key`)."
  parser/param-accessor?)

(def event-accessor?
  "Returns true if keyword is an event accessor (`:event/key`)."
  parser/event-accessor?)

(def policy-reference?
  "Returns true if form is a policy reference (`[:ns/policy]`)."
  parser/policy-reference?)

(def let-binding?
  "Returns true if form is a let binding (`[:let [...] body]`)."
  parser/let-binding?)

;;; ---------------------------------------------------------------------------
;;; Policy Analysis API
;;; ---------------------------------------------------------------------------

(def analyze-policy
  "Analyzes a policy to determine its requirements and characteristics.

  Returns a map with:
  - `:params` — set of required parameter keys
  - `:doc-keys` — set of document paths accessed
  - `:parameterized?` — true if policy requires any params

  Example:

      (analyze-policy [:= :doc/role :param/role])
      ;=> {:params #{:role}
      ;    :doc-keys #{[:role]}
      ;    :parameterized? true}"
  policy/analyze-policy)

(def required-params
  "Returns the set of required parameter keys for a policy.

  Example:

      (required-params [:= :doc/role :param/role])
      ;=> #{:role}"
  policy/required-params)

(def extract-param-keys
  "Extracts all parameter keys from a policy AST.

  Lower-level function that works directly on parsed AST nodes.
  For most use cases, prefer [[required-params]] which works on
  policy expressions directly."
  parser/extract-param-keys)

;;; ---------------------------------------------------------------------------
;;; Parameter Binding API
;;; ---------------------------------------------------------------------------

(defn bind-params
  "Partially binds parameters to a policy, returning a policy context.

  Takes a policy expression and a map of param bindings. Returns a map
  with `:policy` and `:params` that can be used with [[unify]].

  Example:

      ;; Create a partial binding
      (def bound (bind-params [:auth/has-role] {:role \"admin\"}))
      ;=> {:policy [:auth/has-role] :params {:role \"admin\"}}

      ;; Evaluate with the bound params
      (unify (:policy bound) document {:params (:params bound)})"
  [policy params]
  {:policy policy
   :params params})

(defn validate-params
  "Validates that all required params are provided for a policy.

  Returns `{:ok params}` if all required params are present,
  `{:error {:missing #{...}}}` if any are missing.

  Example:

      (validate-params [:= :doc/role :param/role] {:role \"admin\"})
      ;=> {:ok {:role \"admin\"}}

      (validate-params [:= :doc/role :param/role] {})
      ;=> {:error {:missing #{:role}}}"
  [policy params]
  (let [required (policy/required-params policy)
        provided (set (keys params))
        missing  (set/difference required provided)]
    (if (empty? missing)
      {:ok params}
      {:error {:missing missing}})))

;;; ---------------------------------------------------------------------------
;;; Registry Policy Info API
;;; ---------------------------------------------------------------------------

(def policy-info
  "Returns information about a policy in the registry.

  Returns a map with:
  - `:expr` — the policy expression
  - `:params` — set of required parameter keys
  - `:param-defs` — map of param key to definition
  - `:defaults` — map of param key to default value
  - `:description` — policy description if provided
  - `:parameterized?` — true if policy requires params

  Returns nil if the policy is not found.

  Example:

      (policy-info registry :auth :has-role)
      ;=> {:expr [:= :doc/role :param/role]
      ;    :params #{:role}
      ;    :defaults {}
      ;    :parameterized? true}"
  registry/policy-info)

(def param-defaults
  "Returns default values for a policy's parameters.

  Returns a map of param-key to default value.

  Example:

      (param-defaults registry :auth :min-level)
      ;=> {:min 0}"
  registry/param-defaults)

(def parameterized-policies
  "Returns all parameterized policies in a module.

  Returns a map of policy-key to param info.

  Example:

      (parameterized-policies registry :auth)
      ;=> {:has-role {:params #{:role} :defaults {} :description nil}}"
  registry/parameterized-policies)
