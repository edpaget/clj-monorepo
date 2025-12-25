# Polix

Polix is a constraint-based policy language for Clojure and ClojureScript. At its core, polix treats policies and documents as a unified system of constraints expressed as plain data. Evaluation is unification: unify a policy with a document to produce a residual.

## Quick Start

```clojure
(require '[polix.core :as p])

;; Define a policy
(p/defpolicy AdminOnly
  "Only admins can access"
  [:= :doc/role "admin"])

;; Evaluate against a document - returns a residual
(p/evaluate (:ast AdminOnly) {:role "admin"})
;; => {}  (empty residual = satisfied)

(p/evaluate (:ast AdminOnly) {:role "guest"})
;; => {:role [[:conflict [:= "admin"] "guest"]]}  (conflict = violated)

(p/evaluate (:ast AdminOnly) {})
;; => {:role [[:= "admin"]]}  (open constraints = partial)
```

## Residuals as the Universal Result

Policy evaluation always produces a **residual** — a map of remaining constraints. There are no special boolean types; meaning derives from residual structure:

| Result | Meaning |
|--------|---------|
| `{}` | Empty residual — all constraints satisfied |
| `{:x [[:< 10]]}` | Open constraints — awaiting evaluation |
| `{:x [[:conflict [:< 10] 11]]}` | Conflict — constraint evaluated and failed |

A conflict `[:conflict C w]` preserves both the violated constraint and the witness value that failed it, enabling diagnosis and remediation guidance.

```clojure
;; All the same operation: constraint unification
(policy {:role "admin" :level 10})  ; => {} (satisfied)
(policy {:role "admin"})            ; => {:level [[:> 5]]} (open)
(policy {})                         ; => {:role [[:= "admin"]] :level [[:> 5]]} (open)
(policy {:role "guest"})            ; => {:role [[:conflict [:= "admin"] "guest"]]} (conflict)
```

## Design Philosophy

### Policies and Documents are Equivalent

A policy is a document with constraints. A document is a policy with concrete values. The document `{:role "admin"}` is equivalent to the policy `[:= :doc/role "admin"]`. Both express the same constraint.

Evaluating a policy against a document unifies two constraint systems. Compatible constraints merge. Contradictory constraints produce conflicts. Unresolved constraints become residuals.

### Unified Evaluation Model

Traditional policy engines answer "is this allowed?" Polix also answers "what would make this allowed?" — and these are the same operation:

- **"Is this allowed?"** — Unify with complete document → `{}` or conflict
- **"What would satisfy this?"** — Unify with empty document → full residual
- **"What else is needed?"** — Unify with partial document → remaining residual
- **"What would contradict this?"** — Negate policy, then unify with empty document

## Core Concepts

### Policies

A policy is a constraint expression using a vector-based DSL where the first element is an operator:

```clojure
(p/defpolicy AccessPolicy
  "Checks access requirements"
  [:and
   [:= :doc/role "admin"]
   [:> :doc/level 5]
   [:in :doc/status #{"active" "pending"}]])
```

The `:doc/` prefix indicates a document accessor — a reference to a value looked up at evaluation time.

### Documents

Documents are plain Clojure maps. Keys are accessed without the `:doc/` prefix:

```clojure
{:role "admin"
 :level 10
 :status "active"}
```

### Operators

Built-in operators:

- **Equality**: `:=`, `:!=`
- **Comparison**: `:>`, `:<`, `:>=`, `:<=`
- **Set membership**: `:in`, `:not-in`
- **Pattern matching**: `:matches`, `:not-matches`
- **Boolean connectives**: `:and`, `:or`, `:not`
- **Ground terms**: `:conflict`, `:complex`

The `:conflict` operator only appears in residuals, never in source policies. The `:complex` operator marks constraints that cannot be inverted (e.g., hash comparisons).

### Policy Negation

The `negate` function inverts a policy's constraints:

```clojure
;; Original policy
[:and [:= :doc/role "admin"] [:> :doc/level 5]]

;; Negated (De Morgan's law applied)
[:or [:!= :doc/role "admin"] [:<= :doc/level 5]]
```

Conflicts are fixed points under negation — negating a witnessed fact returns the same conflict:

```clojure
(negate [:< 10])                     ; => [:>= 10]
(negate [:conflict [:< 10] 11])      ; => [:conflict [:< 10] 11]
```

### Quantifiers

Policies can reason about collections:

```clojure
[:forall [:u :doc/users]
 [:= :u/status "active"]]

[:exists [:u :doc/users :where [:= :u/role "writer"]]
 [:> :u/account-level 1000]]
```

## Registry and Modules

All name resolution flows through a registry mapping namespace prefixes to their meanings:

```clojure
{:doc {:type :document-accessor}
 :self {:type :self-accessor}
 :fn {:type :builtins
      :entries {:count {:type :aggregate}
                :sum {:type :aggregate}}}
 :param {:type :param-accessor}

 ;; User modules
 :auth {:type :module
        :policies {:admin [:= :doc/role "admin"]
                   :moderator [:in :doc/role #{"admin" "moderator"}]}}}
```

### Policy References

```clojure
;; Reference a policy from a module
[:auth/admin]

;; Reference with parameters
[:auth/has-role {:role "editor"}]
```

### Parameterized Policies

Policies can accept parameters via the `:param/` accessor:

```clojure
{:policies
 {:has-role [:= :doc/role :param/role]
  :min-level [:> :doc/level :param/min]}}

;; Usage
[:auth/has-role {:role "admin"}]
```

## Compiled Policies

For repeated evaluation, compile policies:

```clojure
(def checker (p/compile-policies
               [[:= :doc/role "admin"]
                [:> :doc/level 5]
                [:in :doc/status #{"active" "pending"}]]))

(checker {:role "admin" :level 10 :status "active"})
;; => {}

(checker {:role "guest" :level 10 :status "active"})
;; => {:role [[:conflict [:= "admin"] "guest"]]}

(checker {:role "admin"})
;; => {:level [[:> 5]] :status [[:in #{"active" "pending"}]]}
```

### Tiered Compilation

Polix supports multiple compilation tiers:

| Tier | Description |
|------|-------------|
| **Tier 0: Interpreted** | Full feature support, residuals, protocol dispatch |
| **Tier 1: Guarded** | Generated code with version guards, fallback to Tier 0 |
| **Tier 2: Fully Inlined** | All operators inlined, boolean-only |

### Constraint Simplification

The compiler simplifies at compile time:

```clojure
;; Range constraints simplified to tightest bounds
(def range-check (p/compile-policies
                   [[:> :doc/x 3]
                    [:> :doc/x 5]   ; subsumes [:> :doc/x 3]
                    [:< :doc/x 10]]))
;; Internally simplified to: [:> :doc/x 5], [:< :doc/x 10]

;; Contradictions detected at compile time
(def impossible (p/compile-policies
                  [[:= :doc/role "admin"]
                   [:= :doc/role "guest"]]))
;; Returns function that always produces conflict
```

## Triggers and Effects

Polix integrates reactive rules into the policy model. A trigger policy extends constraints with event bindings and effect specifications.

### Trigger Policies

```clojure
{:event :damage-dealt
 :timing :after
 :condition [:and [:= :event/source-type "fire"]
                  [:> :event/damage 5]]
 :effect {:effect/type :modify
          :target [:doc/entity :event/target-id]
          :transform {:status "burning"}}
 :priority 10}
```

### Effects as Constraint Transformers

Effects describe transformations as data:

```clojure
{:effect/type :modify
 :target [:doc/entity :event/target-id]
 :transform {:health [:fn/sub :event/damage]}
 :requires {:health [[:>= 0]]}}
```

The footprint declares which paths the effect reads and writes. The transform describes mutations. Requirements express postconditions as constraints.

### Effect Backends

Effects describe intent rather than implementation. The same effect can execute against different storage systems:

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

### Conditional Effects

```clojure
{:effect/type :conditional
 :condition [:> :doc/health 0]
 :then {:effect/type :modify :target [:doc/entity] :transform {:status "alive"}}
 :else {:effect/type :modify :target [:doc/entity] :transform {:status "dead"}}
 :on-residual :block}
```

The `:on-residual` strategy handles uncertain conditions: `:block` (default), `:defer`, `:proceed`, or `:speculate`.

## Custom Operators

Register domain-specific operators:

```clojure
(require '[polix.operators :as op])

(op/register-operator! :starts-with
  {:eval (fn [value expected] (str/starts-with? (str value) expected))
   :negate :not-starts-with})

(def checker (p/compile-policies [[:starts-with :doc/email "admin@"]]))
(checker {:email "admin@example.com"}) ;; => {}
```

Operator spec keys:
- `:eval` (required) - `(fn [value expected] -> boolean?)`
- `:negate` - keyword of the negated operator
- `:simplify` - `(fn [constraints] -> {:simplified [...]} | {:contradicted [...]})`
- `:subsumes?` - `(fn [c1 c2] -> boolean?)` for constraint subsumption

## Architecture

```
Policy Expression (DSL vector)
        |
        v
    Parser (AST)
        |
        v
   Unification Engine (residual-based)
        ^
        |
    Compiler (normalization, simplification, tiered compilation)
        |
        v
    Registry (modules, operators, resolvers)
```

### Namespaces

| Namespace | Purpose |
|-----------|---------|
| `polix.core` | Main API - evaluate, compile-policies, defpolicy |
| `polix.unify` | Constraint unification engine |
| `polix.registry` | Module and operator registry |
| `polix.effects.registry` | Effect handler registry |
| `polix.parser` | Policy DSL parsing to AST |
| `polix.operators` | Operator definitions and custom operator support |
| `polix.ast` | AST data structures |
| `polix.policy` | Policy definition macro |

## Correctness Properties

Polix's constraint unification satisfies algebraic laws:

- **Identity**: Unifying with empty document produces full constraint set
- **Idempotence**: Unifying twice with same document equals unifying once
- **Monotonicity**: Adding keys only resolves constraints, never adds new ones
- **Conflict Permanence**: Once a conflict exists, it cannot be removed by adding keys
- **Negation Duality**: Negated policy produces conflict where original satisfies

## Development

```bash
# Run tests
clojure -X:test

# Lint
clj-kondo --lint src test

# Format
clojure -X:format :paths '["src" "test"]'
```

## Specification

See [SPECIFICATION.md](SPECIFICATION.md) for the complete language specification.
