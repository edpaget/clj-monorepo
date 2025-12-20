# Polix

Polix is a Clojure/ClojureScript DSL for writing declarative **policies** - constraint expressions that evaluate against **documents** (key-value data structures). It features three-valued evaluation semantics that distinguish between satisfied, contradicted, and partially-evaluated constraints.

## Quick Start

```clojure
(require '[polix.core :as p])

;; Define a policy
(p/defpolicy AdminOnly
  "Only admins can access"
  [:= :doc/role "admin"])

;; Evaluate against a document
(p/evaluate (:ast AdminOnly) {:role "admin"})
;; => true

(p/evaluate (:ast AdminOnly) {:role "guest"})
;; => false

(p/evaluate (:ast AdminOnly) {})
;; => {:residual {:role [[:= "admin"]]}}
```

## Three-Valued Evaluation

Polix uses three-valued logic for evaluation:

| Result | Meaning |
|--------|---------|
| `true` | All constraints satisfied |
| `false` | At least one constraint contradicted |
| `{:residual {...}}` | Partial match - some values missing |

This enables partial evaluation - you can evaluate a policy against an incomplete document and get back the remaining constraints that need to be satisfied.

## Core Concepts

### Policies

A **Policy** is a constraint expression written in a vector DSL (similar to HoneySQL or Malli). The `:doc/` prefix indicates document key lookup.

```clojure
(p/defpolicy AccessPolicy
  "Checks access requirements"
  [:and
   [:= :doc/role "admin"]
   [:> :doc/level 5]
   [:in :doc/status #{"active" "pending"}]])
```

This creates a Policy with:
- Automatically extracted schema: `#{:role :level :status}`
- Parsed AST for evaluation
- Compile-time validation

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
- **Boolean**: `:and`, `:or`, `:not`

## Direct Evaluation

Use `evaluate` for one-off policy evaluation:

```clojure
;; Evaluate a policy expression directly
(p/evaluate [:= :doc/role "admin"] {:role "admin"})
;; => true

;; Evaluate parsed AST
(let [ast (p/unwrap (p/parse-policy [:> :doc/level 5]))]
  (p/evaluate ast {:level 10}))
;; => true

;; Missing keys return residuals
(p/evaluate [:= :doc/role "admin"] {})
;; => {:residual {:role [[:= "admin"]]}}
```

## Compiled Policies

For repeated evaluation, compile policies for better performance:

```clojure
;; Compile multiple policies (ANDed together)
(def checker (p/compile-policies
               [[:= :doc/role "admin"]
                [:> :doc/level 5]
                [:in :doc/status #{"active" "pending"}]]))

;; Full satisfaction
(checker {:role "admin" :level 10 :status "active"})
;; => true

;; Contradiction
(checker {:role "guest" :level 10 :status "active"})
;; => false

;; Partial - returns residual constraints
(checker {:role "admin"})
;; => {:residual {:level [[:> 5]], :status [[:in #{"active" "pending"}]]}}
```

### Constraint Simplification

The compiler performs constraint solving at compile time:

```clojure
;; Range constraints are simplified to tightest bounds
(def range-check (p/compile-policies
                   [[:> :doc/x 3]
                    [:> :doc/x 5]   ; subsumes [:> :doc/x 3]
                    [:< :doc/x 10]]))
;; Internally simplified to: [:> :doc/x 5], [:< :doc/x 10]

;; Contradictions detected at compile time
(def impossible (p/compile-policies
                  [[:= :doc/role "admin"]
                   [:= :doc/role "guest"]]))
;; Returns (constantly false) - always returns false
```

### Compilation Options

```clojure
(def checker (p/compile-policies
               [[:= :doc/role "admin"]]
               {:operators {...}  ; custom operator overrides
                :fallback fn      ; (fn [op-key]) for unknown operators
                :strict? true     ; throw on unknown operators
                :trace? true}))   ; record evaluation trace
```

### Working with Residuals

Convert residual constraints back to policy expressions:

```clojure
(let [result (checker {:role "admin"})]
  (when (p/residual? result)
    (p/residual->constraints (:residual result))))
;; => [[:> :doc/level 5] [:in :doc/status #{"active" "pending"}]]

;; Or get a simplified policy expression
(p/result->policy result)
;; => [:and [:> :doc/level 5] [:in :doc/status #{"active" "pending"}]]
```

## Custom Operators

Define custom operators using `register-operator!` or the `defoperator` macro:

```clojure
(require '[polix.operators :as op])

;; Register a custom operator
(op/register-operator! :starts-with
  {:eval (fn [value expected] (str/starts-with? (str value) expected))
   :negate :not-starts-with})

(op/register-operator! :not-starts-with
  {:eval (fn [value expected] (not (str/starts-with? (str value) expected)))
   :negate :starts-with})

;; Or use the macro
(op/defoperator :contains-substr
  :eval (fn [value expected] (str/includes? (str value) expected))
  :negate :not-contains-substr)

;; Use in policies
(def checker (p/compile-policies [[:starts-with :doc/email "admin@"]]))
(checker {:email "admin@example.com"}) ;; => true
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
  Engine (three-valued evaluation)
        ^
        |
    Compiler (normalization, simplification, merging)
```

### Namespaces

| Namespace | Purpose |
|-----------|---------|
| `polix.core` | Main API - evaluate, compile-policies, defpolicy |
| `polix.engine` | Unified evaluation engine with three-valued logic |
| `polix.compiler` | Constraint normalization, merging, simplification |
| `polix.parser` | Policy DSL parsing to AST |
| `polix.operators` | Operator registry and custom operator support |
| `polix.ast` | AST data structures |
| `polix.policy` | Policy definition macro |

## Development

```bash
# Run tests
clojure -X:test

# Lint
clj-kondo --lint src test

# Format
clojure -X:format :paths '["src" "test"]'
```
