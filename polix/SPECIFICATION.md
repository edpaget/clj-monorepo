# Polix Language Specification

**Version**: Draft 0.1
**Status**: Working Document

---

## Introduction

Polix is a constraint-based policy language for Clojure and ClojureScript. At its core, polix treats policies, documents, triggers, and effects as a unified system of constraints expressed as plain data. The language enables bidirectional reasoning through a single operation: unify a policy with a document to produce a residual. Complete documents yield definite results; partial documents reveal what constraints remain.

This specification describes the unified polix language, integrating what were previously separate concerns (policy evaluation, triggers, effects) into a coherent whole. The guiding principle is that everything is data, and constraint solving operates uniformly across all constructs.

---

## Design Philosophy

### Data First

Every construct in polix is representable as plain Clojure data structures. Policies are vectors. Triggers are maps. Effects are maps. There is no distinction between "code" and "configuration"—the same policy can be stored in a database, transmitted over a network, or evaluated directly. This enables tooling, analysis, and transformation that would be impossible with opaque function objects.

### Policies and Documents are Equivalent

A policy is a document with constraints. A document is a policy with concrete values. This equivalence is not metaphorical—the two representations are interconvertible.

Evaluating a policy against an empty document yields a residual. That residual is the document form of the policy. Conversely, a document with only concrete values is a policy consisting entirely of equality constraints.

This equivalence means evaluation is unification. When you evaluate a policy against a document, you are unifying two constraint systems. Compatible constraints merge. Contradictory constraints fail. Unresolved constraints become residuals.

The practical consequence: there is no privileged "data" that policies operate on. Policies and documents are peers in a constraint algebra.

### Residuals as the Universal Result

Policy evaluation always produces a residual — a map of remaining constraints. There are no special boolean result types; booleans are derived from residual structure:

- **Empty residual `{}`** — no constraints remain, the policy is satisfied
- **Open constraints `{:x [[:< 10]]}`** — constraints remain unevaluated, satisfiability unknown
- **Conflicts `{:x [[:conflict [:< 10] 11]]}`** — constraints evaluated and failed, unsatisfiable

This unification is consistent with policy-document equivalence. If evaluation is constraint unification, the result should be a constraint system, not a boolean. An empty constraint system means "nothing left to satisfy." Open constraints mean "these requirements remain." Conflicts mean "these requirements were violated."

The key distinction is between *open* and *ground* constraints:

- **Open constraints** like `[:< 10]` await evaluation — they could be satisfied or violated depending on future information
- **Ground constraints** like `[:conflict [:< 10] 11]` are fully evaluated — they record both the requirement and the witness that violated it

A conflict `[:conflict C w]` states that constraint `C` was evaluated against witness value `w` and failed. The conflict preserves the original constraint (enabling reasoning about what would satisfy it) alongside the evidence of failure (enabling diagnosis and recovery guidance).

The residual carries structured information about what would be needed to satisfy the policy. This transforms policy evaluation from a yes/no question into a conversation about requirements — and when requirements are violated, a conversation about what went wrong and how to fix it.

### Unified Evaluation Model

Traditional policy engines answer "is this allowed?" Polix also answers "what would make this allowed?" — and crucially, these are the same operation.

Because policies and documents are equivalent constraint systems, and evaluation is unification, there is only one operation: unify a policy with a document and return the residual. Different "queries" are achieved by varying the document:

- **"Is this allowed?"** — Unify with complete document → `{}` or conflict
- **"What would satisfy this?"** — Unify with empty document → full residual
- **"What else is needed?"** — Unify with partial document → remaining residual
- **"What would contradict this?"** — Negate policy, then unify with empty document

This unification eliminates the need for separate `evaluate`, `implied`, and `query` functions. A compiled policy is simply a function from document to residual.

```clojure
;; All the same operation: constraint unification
(policy {:role "admin" :level 10})  ; → {} (satisfied)
(policy {:role "admin"})            ; → {:level [[:> 5]]} (open)
(policy {})                         ; → {:role [[:= "admin"]] :level [[:> 5]]} (open)
(policy {:role "guest"})            ; → {:role [[:conflict [:= "admin"] "guest"]]} (conflict)
```

This bidirectionality enables use cases beyond simple authorization: driving user interfaces to show available options, computing step-up authentication requirements, generating database queries, and reasoning about policy composition.

### Unified Policy Model

Policies in polix can be pure constraints (for authorization) or can include event bindings and effects (for reactive rules). The same evaluation engine handles both cases. A policy with event bindings becomes a trigger; a trigger is simply a policy that activates in response to events and produces effects rather than boolean results.

---

## Core Concepts

### Documents

A document is any associative data structure that policies evaluate against. The simplest document is a map:

```clojure
{:role "admin"
 :level 10
 :status "active"}
```

Documents can be nested, and policies can access nested values using dot notation in accessors. A document need not be complete—missing keys result in residual constraints rather than errors.

### Policy-Document Equivalence

Policies and documents are equivalent representations of the same underlying constraint system. This equivalence is a core design principle, not merely an implementation detail.

A concrete document value is syntactic sugar for an equality constraint. The document `{:role "admin"}` is equivalent to the policy `[:= :doc/role "admin"]`. Both express the same constraint: the role must equal "admin."

Conversely, any policy is equivalent to a document containing residuals. The policy:

```clojure
[:exists [:u :doc/users :where [:= :u/role "writer"]]
 [:> :u/account-level 1000]]
```

is equivalent to a document with constraints on the `:users` key:

```clojure
{:users [:exists [:u :where [:= :u/role "writer"]]
         [:> :u/account-level 1000]]}
```

The equivalent document is precisely the residual of evaluating the policy against an empty document. Some policies produce complex residuals that cannot be simplified to document form, but the equivalence holds conceptually.

This equivalence means that evaluating a policy against a document is unifying two constraint systems. The document provides concrete constraints (equalities with known values). The policy provides abstract constraints. Unification either succeeds (the constraints are compatible), fails (they contradict), or produces a residual (some constraints remain unresolved).

### Self-References

Documents can contain self-references using the `:self/` prefix. This enables computed or derived values within a document:

```clojure
{:users [{:role "writer" :account-level 1500}
         {:role "writer" :account-level 500}
         {:role "reader" :account-level 100}]
 :high-level-writers [:filter [:u :self/users]
                       [:and [:= :u/role "writer"]
                             [:> :u/account-level 1000]]]}
```

Self-references allow documents to express relationships between their parts. The constraint on `:high-level-writers` depends on the value of `:users`. When `:users` is concrete, `:high-level-writers` can be computed. When `:users` is itself constrained, `:high-level-writers` carries derived constraints.

### Policies

A policy is a constraint expression describing requirements on a document. Policies use a vector-based DSL where the first element is an operator and subsequent elements are operands:

```clojure
[:= :doc/role "admin"]
```

The `:doc/` prefix indicates a document accessor—a reference to a value that will be looked up in the document at evaluation time. Policies compose using boolean operators:

```clojure
[:and
 [:= :doc/role "admin"]
 [:> :doc/level 5]
 [:in :doc/status #{"active" "pending"}]]
```

Policies are pure data. They can be parsed, analyzed, transformed, serialized, and combined without evaluation.

### Unification

Unifying a policy with a document produces a residual — a map of remaining constraints. Three outcomes are possible:

- **Satisfied**: All constraints resolved successfully → empty residual `{}`
- **Open**: Some constraints could not be evaluated (missing keys) → residual with open constraints
- **Conflict**: Some constraints evaluated and failed → residual with conflict markers

When a document provides a value that violates a constraint, the residual records a conflict:

```clojure
;; Policy requires level > 5
[:> :doc/level 5]

;; Document provides level = 3
{:level 3}

;; Residual shows conflict with witness
{:level [[:conflict [:> 5] 3]]}
```

The conflict `[:conflict [:> 5] 3]` preserves both the original constraint and the value that violated it. This is more informative than a simple failure — it tells you what was required, what was provided, and enables reasoning about remediation.

The residual is not merely a marker of incompleteness. It is the policy in document form, reduced by whatever constraints the input document satisfied. A residual of `{:level [[:> 5]]}` means "the policy would be satisfied if `level` is greater than 5." A residual of `{:level [[:conflict [:> 5] 3]]}` means "the policy required `level` > 5, but `level` was 3."

### Residuals as Requirements

Residuals enable a query-oriented approach to policy evaluation. Instead of asking "does this document satisfy this policy?" one can ask "what would this document need to satisfy this policy?"

Consider a game UI that needs to show which entities a player can target. Rather than iterating through candidates and checking each, the UI can evaluate the targeting policy with an incomplete document (missing the target), examine the residual, and use those constraints to filter or highlight valid targets.

Similarly, an authorization system can evaluate an access policy against a request with partial credentials. If the result is a residual indicating stronger authentication is needed, the system can prompt for that specific factor rather than rejecting outright.

The residual transforms policy evaluation from a gate into a guide.

### Policy Negation

The `negate` function inverts a policy's constraints. Where the original policy describes what must be true, the negated policy describes what would contradict it.

```clojure
;; Original policy
[:and [:= :doc/role "admin"] [:> :doc/level 5]]

;; Negated (De Morgan's law applied)
[:or [:!= :doc/role "admin"] [:<= :doc/level 5]]
```

Negation is a pure transformation on policy data — it returns a new policy, not a residual. To find "what would contradict this policy?", negate first, then unify with an empty document:

```clojure
;; What constraints would satisfy the policy?
(policy {})
;; → {:role [[:= "admin"]] :level [[:> 5]]}

;; What constraints would contradict the policy?
((negate policy) {})
;; → {:role [[:!= "admin"]] :level [[:<= 5]]}  ; Either condition suffices
```

Negation enables reasoning about policy violations, generating counter-examples, and computing what would cause denial in authorization scenarios.

Each operator defines its own negation. Simple operators have direct inverses (`:=` ↔ `:!=`, `:>` ↔ `:<=`). Boolean connectives follow De Morgan's laws. Quantifiers swap (`forall` ↔ `exists` with negated body). Complex or non-invertible operators produce `:complex` markers in the negated form.

### Conflict Negation

Conflicts are *ground* constraints — they represent fully evaluated facts, not open requirements. A conflict `[:conflict [:< 10] 11]` states "the constraint `[:< 10]` was evaluated against value `11` and failed." This is a witnessed fact, not a constraint awaiting evaluation.

**Conflicts are fixed points under negation**:

```clojure
(negate [:conflict C w])  ; → [:conflict C w]
```

The rationale: negating a fact doesn't change the fact. The conflict records what happened during evaluation. You cannot "un-witness" a contradiction by negation.

This differs from open constraints, which negate normally:

```clojure
(negate [:< 10])           ; → [:>= 10]
(negate [:conflict [:< 10] 11])  ; → [:conflict [:< 10] 11]
```

If you need to reason about "what would satisfy the violated constraint?", extract the inner constraint from the conflict:

```clojure
;; Conflict tells you what failed
{:mfa-age-minutes [[:conflict [:< 10] 11]]}

;; Inner constraint tells you what would satisfy
[:< 10]  ; → need mfa-age-minutes < 10

;; Negated inner constraint tells you what would also fail
[:>= 10]  ; → any value >= 10 would fail
```

This separation preserves the distinction between reasoning about *policies* (abstract rules) and reasoning about *evaluation results* (witnessed facts).

### Operators

Policies are built from operators. Polix provides built-in operators for common comparisons and boolean logic:

- Equality: `:=`, `:!=`
- Comparison: `:>`, `:<`, `:>=`, `:<=`
- Set membership: `:in`, `:not-in`
- Pattern matching: `:matches`, `:not-matches`
- Boolean connectives: `:and`, `:or`, `:not`
- Ground terms: `:conflict`, `:complex`

The `:conflict` operator is special — it only appears in residuals, never in source policies. It wraps another constraint to indicate evaluation failure: `[:conflict [:< 10] 11]` means "the constraint `[:< 10]` was evaluated against `11` and failed." Conflicts are fixed points under negation (negating a conflict returns the same conflict).

The `:complex` operator marks constraints that cannot be inverted or simplified, such as hash comparisons. Like conflicts, complex markers appear in residuals to indicate constraints that exist but cannot be reasoned about symbolically.

Operators are extensible. Applications register domain-specific operators that integrate with the evaluation and constraint-solving machinery. Each operator defines how to evaluate it forward, how to negate it for inverse evaluation, and optionally how to simplify combinations of constraints using that operator.

### Literal Values

By default, polix interprets namespaced keywords according to their namespace:

- `:doc/key` — document accessor
- `:self/key` — self-reference accessor
- `:param/key` — parameter accessor
- `:event/key` — event accessor
- Other namespaced keywords — binding accessors (for quantifier bindings)

When you need to use a namespaced keyword as a literal value (not an accessor), wrap it with `:literal`:

```clojure
;; Without wrapper: :phase/ACTIONS interpreted as binding accessor
[:= :doc/phase :phase/ACTIONS]  ; WRONG - looks for binding :phase

;; With wrapper: :phase/ACTIONS used as literal keyword value
[:= :doc/phase [:literal :phase/ACTIONS]]  ; CORRECT
```

The `:literal` wrapper accepts any Clojure value:

```clojure
[:literal :phase/ACTIONS]      ; namespaced keyword
[:literal "string"]            ; string (same as bare string)
[:literal {:key "value"}]      ; map
[:literal #{:a :b :c}]         ; set
```

Non-namespaced keywords and other values don't require wrapping — they're already treated as literals. The wrapper is only necessary for namespaced keywords that would otherwise be interpreted as accessors.

### Quantifiers

Policies can reason about collections using quantifiers. The universal quantifier (`forall`) requires all elements to satisfy a condition. The existential quantifier (`exists`) requires at least one element to satisfy it.

Quantifiers introduce bindings that scope over their body. Within the body, accessors can reference the bound element using a namespace prefix matching the binding name.

```clojure
[:forall [:u :doc/users]
 [:= :u/status "active"]]
```

This policy requires all users to have active status. The binding `:u` iterates over `:doc/users`, and `:u/status` accesses the status of each user in turn.

Quantifiers produce residuals like any other construct. If the collection is missing, the quantifier produces a residual constraining that collection. If the collection is present but some elements produce residuals, the quantifier's result depends on whether those residuals could affect the outcome — a `forall` with one conflicting element produces a conflict; an `exists` with one satisfying element returns `{}`.

### Abstractions via Let Bindings

Policies support `:let` bindings that introduce named abstractions. These abstractions become keys in the equivalent document representation, enabling reuse and composition.

```clojure
[:let [:high-level-writers [:u :doc/users :where [:and [:= :u/role "writer"]
                                                       [:> :u/account-level 1000]]]]
 [:and
  [:> [:fn/count :self/high-level-writers] 10]
  [:exists [:w :self/high-level-writers]
   [:> :w/account-level 10000]]]]
```

The binding `:high-level-writers` defines a filtered view of `:users`. Within the body, `:self/high-level-writers` references this derived collection. The policy requires at least 10 high-level writers and at least one with an account level above 10,000.

The equivalent document representation:

```clojure
{:users [:exists [:u :where [:and [:= :u/role "writer"]
                                  [:> :u/account-level 1000]]]
         [:> :u/account-level 1000]]
 :high-level-writers [:and
                      [:> [:fn/count] 10]
                      [:exists [:w] [:> :w/account-level 10000]]]}
```

Let bindings serve multiple purposes:

**Abstraction** — Named bindings make complex policies readable by giving meaningful names to intermediate concepts.

**Reuse** — The same binding can be referenced multiple times without repeating the filter logic.

**Composition** — Bindings from outer scopes are visible in inner scopes, enabling layered abstractions.

**Document mapping** — Each binding becomes a key in the equivalent document, making the policy's structure explicit.

The binding syntax mirrors quantifier bindings: a keyword name followed by a collection expression (potentially with `:where` filters). The scope of the binding extends through the body of the `:let`.

---

## Composition and Modularity

### The Registry

All name resolution in polix flows through a registry — a data structure mapping namespace prefixes to their meanings. The registry contains both built-in resolvers and user-defined modules. There is no distinction in mechanism; built-ins are simply pre-populated entries.

```clojure
{:doc {:type :document-accessor}
 :self {:type :self-accessor}
 :fn {:type :builtins
      :entries {:count {:type :aggregate}
                :sum {:type :aggregate}}}
 :param {:type :param-accessor}
 :event {:type :event-accessor}

 ;; User modules
 :auth {:type :module
        :policies {:admin [:= :doc/role "admin"]
                   :moderator [:in :doc/role #{"admin" "moderator"}]}}
 :content {:type :module
           :imports [:auth]
           :policies {:can-edit [:and [:auth/moderator]
                                      [:= :doc/author :doc/user-id]]}}}
```

When the evaluator encounters `:auth/admin`, it looks up `:auth` in the registry, finds a module, and resolves `admin` within that module's policies. When it encounters `:doc/role`, it looks up `:doc`, finds a document accessor, and retrieves the `role` key from the current document.

### Resolution Rules

Resolution follows a precedence order, itself configurable as data:

```clojure
{:resolution-order [:alias :module :builtin]}
```

When a prefix matches multiple registry entries of different types, the order determines which wins. By default, aliases take precedence over modules, which take precedence over built-ins.

User modules can shadow built-in prefixes if desired — though this is rarely advisable. For cases where shadowing has occurred, the `polix.*` namespace provides an escape hatch:

```clojure
;; User has defined their own :fn module
{:fn {:type :module :policies {:count [:= :doc/type "counter"]}}}

;; :fn/count now refers to user's policy
;; To access the built-in count function:
[:polix.fn/count :doc/users]
```

The `polix.*` namespace is the only truly reserved prefix — it always resolves to built-in functionality regardless of user definitions.

### Policy References

Policies reference other policies using namespaced keywords:

```clojure
;; Reference a policy from a module
[:auth/admin]

;; Reference with parameters
[:auth/has-role {:role "editor"}]
```

Policy references are transparent during evaluation — they expand to their definitions and unify with the document like any other constraint. The residual contains document paths, not policy names.

### Parameterized Policies

Policies can accept parameters via the `:param/` accessor:

```clojure
{:policies
 {:has-role [:= :doc/role :param/role]
  :min-level [:> :doc/level :param/min]
  :role-with-level [:and [:./has-role] [:./min-level]]}}
```

When a parameterized policy is referenced with arguments, those arguments bind to `:param/` accessors:

```clojure
;; Applies has-role with role="admin"
[:auth/has-role {:role "admin"}]

;; Equivalent after expansion
[:= :doc/role "admin"]
```

Parameters flow through nested policy references. A policy referencing another parameterized policy passes its parameters along, with explicit arguments taking precedence.

### Modules as Data

A module is a data structure defining a namespace and its policies:

```clojure
;; auth.edn
{:namespace :auth
 :policies
 {:admin [:= :doc/role "admin"]
  :moderator [:in :doc/role #{"admin" "moderator"}]
  :authenticated [:not [:nil? :doc/user-id]]
  :has-role [:= :doc/role :param/role]}}
```

Modules can import other modules:

```clojure
;; content.edn
{:namespace :content
 :imports [:auth]
 :policies
 {:can-edit [:and [:auth/moderator]
                  [:or [:= :doc/author :doc/user-id]
                       [:auth/admin]]]
  :can-view [:or [:= :doc/visibility "public"]
                 [:auth/authenticated]]}}
```

Imports bring another module's policies into scope for reference. The imported module must be present in the registry.

### Aliasing

Any registry entry can alias another:

```clojure
{:auth {:type :module :policies {...}}
 :a {:type :alias :target :auth}
 :d {:type :alias :target :doc}}
```

With these aliases, `:a/admin` resolves to `:auth/admin` and `:d/role` resolves to `:doc/role`. Aliasing enables shorthand references and allows renaming imports to avoid collisions.

### Loading and Assembly

The registry is assembled from data files. A loader function reads EDN files, resolves imports, and produces a flat registry. The loader is a pure function from files to data — no global state, no side effects. The registry is baked into the compiled policy:

```clojure
(def policy (compile [:auth/admin] {:registry loaded-registry}))
(policy document)  ; → residual
```

Different compilations can use different registries. Testing can use mock registries. The registry is just another piece of data flowing through compilation.

---

## Compilation

### Tiered Compilation

Polix supports multiple compilation tiers, selecting the appropriate level based on policy analysis:

**Tier 0: Interpreted** — Full feature support including residuals, protocol-based dispatch, dynamic resolution. Used for complex policies, ClojureScript, and as fallback.

**Tier 1: Guarded** — Generated code with version guards that fall back to Tier 0 when assumptions are violated. Used for policies with custom operators or parameterized references.

**Tier 2: Fully Inlined** — All operators and references inlined as direct code. Boolean-only (no residuals). Used for simple policies with only built-in operators and fully resolved references.

### Policy Reference Classification

During compilation, each policy reference is classified:

| Status | Meaning | Strategy |
|--------|---------|----------|
| `:inlined` | Fully resolved and expanded | Tier 2: embed definition directly |
| `:guarded` | Resolved but may change | Tier 1: inline with version guard |
| `:late-bound` | Explicitly marked dynamic | Tier 0: resolve at evaluation time |
| `:unresolved` | Missing from registry | Compile error or Tier 0 with runtime check |

### Version Guards

The registry maintains a version counter. Compiled policies snapshot this version. At evaluation time, guarded policies check whether the registry has changed:

```
if registry.version != compiled_version:
    return fallback_to_tier0(policy, document)
// else continue with inlined fast path
```

This single integer comparison adds negligible overhead while enabling safe evolution of the policy set. When policies are added or modified, dependent compiled policies automatically fall back to interpretation until recompiled.

### Compilation Transparency

Compilation produces not just an evaluation function but metadata about what happened:

```clojure
{:compiled-fn <fn>
 :tier :t1
 :registry-version 42
 :analysis
 {:refs
  {:auth/admin {:status :inlined}
   :auth/has-role {:status :guarded :reason :parameterized}
   :vendor/policy {:status :unresolved :reason :missing-module}}
  :operators
  {:builtin [:= :> :in]
   :custom [:domain/special]}
  :residual-capable? true}}
```

This transparency ensures no silent failures. You know at compile time which references were inlined, which are guarded, and which will resolve dynamically.

### Explicit Control

Users can control inlining behavior at the reference level:

```clojure
;; Force late binding (never inline, always resolve from registry)
[:dynamic [:auth/admin]]

;; Force inline (compile error if unresolved)
[:inline [:auth/admin]]

;; Default behavior
[:auth/admin]
```

Or at the compilation level:

```clojure
(compile policy {:inline-refs :all})      ;; Inline everything possible
(compile policy {:inline-refs :none})     ;; Late-bind all references
(compile policy {:inline-refs :guarded})  ;; Inline with guards (default)
```

### Residual Analysis

Policy references affect tier selection through residual analysis. A policy can produce residuals when document fields may be missing, quantifiers iterate unbounded collections, or OR branches have different residual paths.

Referenced policies are analyzed recursively. If a reference points to a policy that can produce residuals, the referencing policy inherits that capability. Only policies where all paths lead to definite results (no missing fields possible) qualify for Tier 2 boolean-only compilation.

---

## Triggers and Effects

### Motivation

Many applications need reactive rules: when some event occurs, if certain conditions are met, perform some action. Game engines, workflow systems, and real-time applications all share this pattern. Polix integrates triggers and effects into the policy model rather than treating them as separate concerns.

### Trigger Policies

A trigger policy extends the constraint model with event bindings and effect specifications. Where a pure policy evaluates to a boolean, a trigger policy evaluates to determine whether to fire and what effect to produce.

A trigger policy includes:

- **Event types** — which events activate this trigger
- **Timing** — when relative to the event (before, instead, after)
- **Condition** — a policy expression evaluated against the event context
- **Effect** — what to do when the condition is satisfied
- **Priority** — ordering among multiple triggers

The condition uses the same policy DSL as authorization policies. Document accessors reference values from the event, the trigger's bound context (self, owner, source), and optionally the current application state.

### Effects as Constraint Transformers

Effects are modeled as **constraint transformers** — data structures that describe how to transform one constraint state into another. This model separates the abstract transformation (what changes) from concrete execution (how it's applied).

An effect specifies:

- **Footprint** — what document paths the effect touches
- **Transform** — how those paths change
- **Requirements** — postconditions the result must satisfy

```clojure
{:effect/type :modify
 :target [:doc/entity :event/target-id]        ; Footprint
 :transform {:health [:fn/sub :event/damage]}  ; Transformation
 :requires {:health [[:>= 0]]}}                ; Postcondition
```

The footprint declares which paths the effect reads and writes. This enables static analysis — two effects with disjoint footprints can be reordered or parallelized. Effects with overlapping footprints must be sequenced.

The transform describes mutations as data. Rather than calling functions directly, transforms reference operations (`:fn/sub`, `:fn/add`) and values (`:event/damage`). These references are resolved at application time against the effect context.

Requirements express postconditions as constraints. After applying the transform, the result must satisfy these constraints. If the transformation would violate a requirement (e.g., health going negative), the effect fails or produces a residual indicating the constraint cannot be met.

### Residuals in Effects

Effects produce residuals when references cannot be resolved. If `:event/damage` is unknown, the effect cannot compute the new health value. Instead of failing, it produces a residual:

```clojure
;; Effect with unresolved reference
{:effect/type :modify
 :target [:doc/entity "entity-1"]
 :transform {:health [:fn/sub :event/damage]}}

;; Residual: what would this effect need?
{:event/damage :required
 :result {:health [[:fn/sub :event/damage]]}}
```

This residual-aware model enables "what if" queries — computing the outcome of an effect without having all inputs, and seeing what inputs would be needed.

### Precondition Inference

The constraint transformer model enables precondition inference. Given a postcondition and a transform, the system can compute what precondition would be required:

```clojure
;; Postcondition: health >= 0
;; Transform: health = health - 5
;; Inferred precondition: health >= 5
```

This is the effect-level equivalent of unifying with an empty document. It answers "what state would allow this effect to succeed?"

### Effect Composition

Effects compose through sequencing and branching:

```clojure
{:effect/type :sequence
 :effects [{:effect/type :modify
            :target [:doc/entity :event/target-id]
            :transform {:health [:fn/sub :event/damage]}}
           {:effect/type :modify
            :target [:doc/entity :event/source-id]
            :transform {:energy [:fn/sub :event/cost]}}]}
```

The constraint transformer model analyzes composed effects:

- **Independent effects** (disjoint footprints) can be reordered or batched
- **Dependent effects** must preserve ordering
- **Conflicting effects** (incompatible transforms to same path) produce contradictions

Built-in effect types handle common patterns: setting values, updating values, adding to collections, removing from collections, sequencing multiple effects, and conditional branching. Applications register domain-specific effect types for their particular needs.

### Effect Backends

Because effects describe intent rather than implementation, the same effect can execute against different storage systems. An effect like "set the value at path `[:user :name]`" is independent of whether the underlying storage is a Clojure map, a SQL database, or a Redis instance. The effect is the specification; the execution strategy is separate.

The layered model:

```
┌─────────────────────────────────────────────┐
│ Effect Definition (constraint transformer)  │  Abstract: footprint, transform, requirements
├─────────────────────────────────────────────┤
│ Effect Resolution (polix)                   │  Resolve references, compute residuals
├─────────────────────────────────────────────┤
│ Effect Backend                              │  Concrete: storage-specific execution
└─────────────────────────────────────────────┘
```

Effect execution is mediated by a backend protocol:

```clojure
(defprotocol EffectBackend
  (apply-transform [this target transform context]
    "Apply a transformation to a target path. Returns updated state.")
  (read-path [this path]
    "Read a value at path. Used for reference resolution.")
  (supports-residual? [this]
    "Whether this backend can handle effects with residuals.")
  (compile-effect [this effect-spec]
    "Optional: pre-compile effect for repeated execution."))
```

Each backend implements these primitives for its storage model:

- A **Clojure backend** implements primitives via `assoc-in`, `update-in`, and related functions — the default for in-memory state.
- A **SQL backend** accumulates UPDATE and INSERT statements, translating logical paths to tables and columns via a schema mapping.
- A **Redis backend** accumulates HSET, HDEL, and RPUSH commands, translating paths to key patterns.
- An **event sourcing backend** emits domain events rather than mutating state directly.
- A **CRDT backend** produces merge-compatible operations for distributed state.

The same effect produces different outputs depending on backend:

```clojure
;; Effect definition
{:effect/type :modify
 :target [:doc/entity :event/target-id]
 :transform {:health [:fn/sub :event/damage]}}

;; Clojure backend → new map
{:entity-1 {:health 95}}

;; SQL backend → statement
"UPDATE entities SET health = health - ? WHERE id = ?"

;; Event sourcing backend → event
{:event/type :health-modified :entity-id "entity-1" :delta -5}
```

Effect handlers delegate to the backend rather than calling storage operations directly. The same effect definition works across storage systems; only the backend differs.

This abstraction extends to reference resolution. When effects reference state paths, the resolver delegates reads to the backend. For SQL or Redis backends, this requires either cached reads or deferred resolution. The backend protocol includes read operations alongside writes, enabling resolvers to work uniformly across storage models.

The two-phase model — compile effects to primitive operations, then execute via backend — enables additional capabilities: inspecting planned operations before execution, optimizing or batching operations, validating effects without execution, and targeting the same compiled operations to different backends.

### Effect Compilation

Effects follow the same tiered compilation model as policies:

**Tier 0: Interpreted** — Full residual support, dynamic handler lookup, protocol-based dispatch. Used for debugging, tracing, and effects with complex or unknown handlers.

**Tier 1: Guarded** — Compiled with version guards that fall back to Tier 0 when handlers change. Used for effects with custom handlers that may be updated.

**Tier 2: Fully Inlined** — All handlers inlined, effect fusion applied, redundancy eliminated. Used for effects with known handlers and statically-resolvable targets.

### Effect Fusion

The compiler automatically fuses compatible effects when possible. Fusion combines multiple effects into fewer operations:

```clojure
;; Before fusion
[{:effect/type :modify :target [:entity-1] :transform {:health [:fn/add 10]}}
 {:effect/type :modify :target [:entity-1] :transform {:health [:fn/sub 3]}}
 {:effect/type :modify :target [:entity-1] :transform {:energy [:fn/sub 5]}}]

;; After fusion
[{:effect/type :modify :target [:entity-1] :transform {:health [:fn/add 7] :energy [:fn/sub 5]}}]
```

Fusion also eliminates redundant effects:

```clojure
;; Before: set then overwrite
[{:effect/type :set :target [:entity-1 :status] :value "active"}
 {:effect/type :set :target [:entity-1 :status] :value "inactive"}]

;; After: only final value matters
[{:effect/type :set :target [:entity-1 :status] :value "inactive"}]
```

### Fusion Protocol

Custom effect handlers participate in fusion by implementing additional protocol methods:

```clojure
(defprotocol EffectHandler
  ;; Core execution
  (apply-effect [this state effect context]
    "Apply the effect to state. Returns {:state new-state :applied [...]}.")

  ;; Fusion support (optional)
  (fuse-effects [this effect1 effect2]
    "Attempt to fuse two effects of this type. Returns fused effect or nil if incompatible.")
  (effect-commutes? [this effect1 effect2]
    "Whether two effects can be reordered without changing semantics.")
  (effect-identity [this]
    "The identity effect for this type (no-op). Used for cancellation detection."))
```

When `fuse-effects` returns nil, the effects remain separate. When it returns a fused effect, the compiler substitutes the single effect for the pair. The `effect-commutes?` method enables reordering for optimization. The `effect-identity` method enables detecting effects that cancel out (e.g., +5 then -5).

Handlers that don't implement fusion methods are treated conservatively — their effects are never fused or reordered.

### Compilation Transparency

Effect compilation produces metadata alongside the compiled function:

```clojure
{:compiled-fn <fn>
 :tier :t2
 :analysis
 {:effects-before 5
  :effects-after 2
  :fusions [{:type :arithmetic-combine :count 3}]
  :eliminations [{:type :redundant-set :count 1}]
  :handlers {:builtin [:modify :set :sequence]
             :custom [:game/damage]}}}
```

This transparency shows what optimizations were applied and why certain effects couldn't be fused.

### Conditional Effects and Uncertainty

Effects can include conditions that determine whether or how they apply. When a condition evaluates to an open residual (constraints remain but no conflicts), the effect must handle this uncertainty.

```clojure
{:effect/type :conditional
 :condition [:> :doc/health 0]
 :then {:effect/type :modify :target [:doc/entity] :transform {:status "alive"}}
 :else {:effect/type :modify :target [:doc/entity] :transform {:status "dead"}}
 :on-residual :block}
```

The `:on-residual` key specifies how to handle uncertain conditions:

| Strategy | Behavior |
|----------|----------|
| **:block** | Treat residual as failure; effect does not apply. **(Default)** |
| **:defer** | Queue the effect; re-evaluate when more information is available. |
| **:proceed** | Apply the effect; carry the residual through to the result. |
| **:speculate** | Apply optimistically within a transaction; rollback if condition later fails. |

The default is `:block` — if the condition cannot be fully evaluated, the effect does not fire. This is the safe, strict behavior appropriate for authorization and state integrity.

**Defer** is useful for eventual consistency scenarios where effects can wait for missing information. The deferred effect is queued with its residual, and the system re-evaluates when relevant data changes.

**Proceed** is useful when the effect should apply regardless, but downstream consumers need to know the condition wasn't fully checked. The residual propagates to the effect result.

**Speculate** requires a transaction context. The effect applies optimistically, but if the condition later resolves to a conflict, the transaction rolls back:

```clojure
{:effect/type :transaction
 :effects [{:effect/type :conditional
            :condition [:> :doc/balance :event/amount]
            :then {:effect/type :modify :target [:doc/balance] :transform [:fn/sub :event/amount]}
            :on-residual :speculate}
           {:effect/type :external
            :action :send-payment
            :target :event/recipient}]
 :on-failure :rollback}
```

Within a transaction, speculative effects are tracked. If any speculative condition resolves to contradiction before the transaction commits, all effects roll back. This keeps `:speculate` scoped to explicit transaction boundaries rather than requiring global rollback infrastructure.

### Timing

Triggers fire at different moments relative to the event they respond to:

**Before** triggers fire before the event's primary action occurs. They can inspect the event, modify state, or prevent the action entirely by setting a prevention flag.

**Instead** triggers replace the event's action. When an instead trigger fires, its effect runs in place of the normal event handling. Only one instead trigger fires per event; subsequent ones are skipped.

**After** triggers fire after the event has been handled. They react to completed actions and cannot prevent or modify the original action.

**At** triggers fire at specific moments like phase boundaries or turn transitions, independent of other actions.

### Processing Flow

When an event fires, the trigger system:

1. Collects all triggers registered for that event type
2. Partitions them by timing
3. Evaluates before triggers in priority order, applying effects and checking for prevention
4. If not prevented, evaluates the first matching instead trigger (if any)
5. Evaluates after triggers in priority order

Each trigger's condition is evaluated as a policy against a document constructed from the event data and trigger context. If the condition returns `{}` (satisfied), the effect is applied. If the condition returns an open residual, the trigger is skipped — the event lacks sufficient information to determine if it should fire. If the condition returns a conflict, the trigger does not fire.

### Unified Model

The unification of policies, triggers, and effects means:

- The same constraint language describes authorization rules and game mechanics
- Residual-driven UI works for both "what actions are available" and "what targets are valid"
- Effects can contain conditional logic expressed as policies
- Triggers can be analyzed, combined, and transformed like any other policy

A policy that specifies event bindings and effects is a trigger. A policy without them is a pure constraint. The evaluation machinery handles both uniformly.

---

## Evaluation Context

### Context Structure

Policy evaluation occurs within a context that provides:

- The document being evaluated against
- Operator definitions (built-in and custom)
- Bindings from enclosing scopes (quantifiers, triggers)
- Application state (for trigger evaluation)
- Options controlling evaluation behavior

The context is threaded through evaluation, allowing nested constructs to access enclosing bindings and enabling applications to customize behavior.

### Triggers in Context

When triggers are bound into the evaluation context, the system can:

- Match events to registered triggers
- Build trigger documents from event data and bindings
- Evaluate trigger conditions against those documents
- Apply triggered effects to state

Triggers become part of the constraint system rather than a separate layer. The same evaluation machinery handles both pure policies and triggered rules.

### Effect Context

Effects resolve references against a context containing:

- Current state (for reading values)
- Bindings (self, target, owner, source)
- Event data (for event-triggered effects)
- Effect parameters

Reference resolution happens at effect application time, not definition time. This allows effects to be defined abstractly and applied in varying contexts.

---

## Compilation and Optimization

### Policy Compilation

For repeated evaluation, policies can be compiled to optimized representations. Compilation:

- Parses the policy DSL to an AST
- Normalizes constraints (extracting constraint sets from AST)
- Simplifies redundant or contradictory constraints
- Produces an evaluation function

Compiled policies detect contradictions at compile time (e.g., requiring a value to be both "admin" and "guest") and simplify redundant constraints (e.g., merging overlapping range checks).

### Constraint Merging

Multiple policies can be merged before compilation. The compiler extracts constraints from each policy, combines them by document key, simplifies, and detects conflicts. This enables policy composition without runtime overhead.

### Three-Valued Optimization

The compiler preserves three-valued semantics through optimization. Residuals flow correctly through simplified constraint sets. The optimized policy returns the same results (true, false, or residual) as the original, just faster.

---

## Extension Points

### Custom Operators

Applications register operators for domain-specific comparisons:

- `eval` — evaluate the operator given actual values
- `negate` — produce the negated operator (for inverse evaluation)
- `simplify` — simplify a set of constraints using this operator
- `subsumes?` — check if one constraint implies another

Custom operators participate fully in compilation, simplification, and bidirectional evaluation.

### Custom Effects

Applications register effect types for domain-specific mutations:

- The handler receives state, effect, context, and options
- It returns the new state plus metadata about what was applied

Custom effects can delegate to other effects, enabling composition. They can produce pending results when external input is needed (e.g., player choices).

### Custom Resolvers

Reference resolution is customizable. Applications provide resolvers for domain-specific reference patterns beyond the built-in `:state` and `:ctx` prefixes.

---

## Correctness Properties

### Unification Laws

Polix's constraint unification satisfies algebraic laws:

**Identity**: Unifying with an empty document produces the policy's full constraint set (the policy in document form).

**Idempotence**: Unifying twice with the same document produces the same result as unifying once.

**Monotonicity**: Adding keys to a document can only *resolve* constraints, never add new ones. Resolution takes two forms:

- An open constraint becomes satisfied (removed from residual)
- An open constraint becomes a conflict (evaluated and failed)

More precisely, if `D ⊆ D'` (D' contains all keys of D plus possibly more), then `residual(P, D')` is "more resolved" than `residual(P, D)`:

```clojure
;; Policy
[:and [:< :doc/x 10] [:> :doc/y 5]]

;; Progressive resolution
(unify policy {})           ; → {:x [[:< 10]] :y [[:> 5]]}  (two open)
(unify policy {:x 7})       ; → {:y [[:> 5]]}               (one open)
(unify policy {:x 7 :y 6})  ; → {}                          (satisfied)

;; Conflict is also resolution
(unify policy {:x 15})      ; → {:x [[:conflict [:< 10] 15]] :y [[:> 5]]}
(unify policy {:x 15 :y 6}) ; → {:x [[:conflict [:< 10] 15]]}  (y resolved)
```

The ordering is by *uncertainty*: open constraints have unknown satisfiability; satisfied constraints and conflicts are both fully resolved. Both `{}` and `{:x [[:conflict ...]]}` represent terminal states — we know the answer. Adding information moves toward resolution, never backward.

**Conflict Permanence**: Once a conflict exists for a key, adding more keys cannot remove it. You cannot "un-witness" a contradiction by providing unrelated information.

**Negation Duality**: Unifying a negated policy with a document that satisfies the original policy produces a conflict (not satisfaction), and vice versa.

These laws are tested via property-based testing rather than proven formally, but they guide the design and catch regressions.

### Determinism

Unification is deterministic. The same policy unified with the same document produces the same result. Effects are applied in deterministic order. Trigger priority ordering is stable.

---

## Limitations

### Operator Invertibility

Not all operators can be inverted. Hash functions, encryption, and other one-way transformations cannot produce meaningful constraint descriptions. Such constraints are marked with a `:complex` indicator within the residual:

```clojure
{:password [[:complex {:op :hash-equals :value "abc123..."}]]}
```

The constraint exists in the residual but cannot be simplified or used for query generation. Applications handle complex constraints according to their semantics.

### Computational Complexity

General constraint satisfaction is NP-complete. Polix targets the common case where policies are relatively shallow and constraints are tractable. Deep nesting, large disjunctions, and complex cross-key constraints may require exponential time.

### Effect Reversibility

While effects are data, they are not automatically reversible. The effect `{:type :polix/update-in :path [:x] :f + :args [1]}` cannot be undone without additional information. Applications requiring undo must track effect history or design effects to be reversible.

---

## Summary

Polix is a constraint-based policy language built on three principles:

1. **Everything is data** — policies, documents, triggers, effects
2. **Policies and documents are equivalent** — both are constraint systems, unification merges them
3. **Residuals are the universal result** — `{}` satisfied, open constraints partial, conflicts violated

The unified model provides a single operation: unify a policy with a document to produce a residual. Different "queries" emerge from varying the document — complete documents yield definite answers, partial documents reveal remaining constraints, empty documents extract the full policy requirements. The `negate` utility enables reasoning about contradictions.

Residuals distinguish between *open* constraints (awaiting evaluation) and *conflicts* (evaluated and failed). A conflict `[:conflict C w]` preserves both the violated constraint and the witness value, enabling applications to diagnose failures and guide remediation. Conflicts are ground terms — fixed points under negation — representing witnessed facts rather than open requirements.

This transforms policy evaluation from a boolean function into a constraint algebra, enabling query-driven interfaces, requirement derivation, failure diagnosis, and bidirectional reasoning through one simple operation.

The language is implemented in Clojure and ClojureScript, designed for extension, and focused on enabling applications to reason about policies rather than merely execute them.
