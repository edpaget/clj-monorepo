# Polix

Polix is a DSL for writing declarative **policies** - terminating programs that evaluate constraints against **Documents** (key-value data structures). At its core, Polix treats both policies and documents as constraint systems that can be unified and evaluated bidirectionally.

## Core Concepts

### Unified Constraint Model

In Polix, **Documents and Policies are equivalent** - both are constraint systems:

- A **Document** can return concrete values (like `"admin"`) or constraints (like `[:= "admin"]`)
- A **Policy** is a Document containing constraints at specific keys
- Concrete values are syntactic sugar for equality constraints: `{:role "admin"}` ≡ `{:role [:= "admin"]}`
- Evaluation is **constraint unification** - checking if constraints are mutually satisfiable

### Documents

The **Document** protocol provides a key-value interface that can be implemented as static data structures or backed by external stores (SQL databases, etc.). Documents can also be composed. 

```clojure
(defprotocol Document
  (doc-get [this key])
  (doc-keys [this])
  (doc-project [this ks])
  (doc-merge [this other]))
```

Documents describe their schema (available keys). Merging operates left-to-right with right precedence.

### Policies

A **Policy** combines a schema (required document keys) with constraint expressions. Policies are written as a vector DSL similar to HoneySQL or Malli.

The `:doc/` prefix in policy expressions indicates document lookup - it's policy syntax, not part of the actual document keys.

```clojure
(defpolicy MyGreatPolicy
  "Checks if actor is admin or matches URI pattern"
  [:or
   [:= :doc/actor-role "admin"]
   [:uri-match :doc/actioning-uri "myprotocol:" :doc/actor-name "/*"]])
```

This creates a Policy with:
- Automatically extracted schema: `#{:actor-role :actioning-uri :actor-name}`
- Parsed AST for evaluation
- Compile-time validation

## Operations

### Evaluation (Current Implementation)

The `evaluate` function checks constraints against a document:

```clojure
(require '[polix.core :as p])

;; Create a document
(def doc (p/->MapDocument {:actor-role "admin"}))

;; Evaluate policy against document
(p/evaluate (:ast MyGreatPolicy) doc)
;; => Either[error, result]
```

**Note**: Currently requires operators to be passed in context:

```clojure
(p/evaluate (:ast MyGreatPolicy)
            doc
            (p/->DefaultEvaluator)
            {:environment {:= = :or (fn [& args] (some identity args))}})
```

### Compiled Policies with Three-Valued Evaluation

The `compile-policies` function merges multiple policies into an optimized function
that returns one of three values:

- `true` - document fully satisfies all constraints
- `false` - document contradicts at least one constraint
- `{:residual {...}}` - partial match with remaining constraints

```clojure
(require '[polix.core :as p])

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

#### Constraint Simplification

The compiler performs constraint solving at compile time:

```clojure
;; Range constraints are simplified to tightest bounds
(def range-check (p/compile-policies
                   [[:> :doc/x 3]
                    [:> :doc/x 5]   ; subsumes [:> :doc/x 3]
                    [:< :doc/x 10]]))

;; Contradictions are detected at compile time
(def impossible (p/compile-policies
                  [[:= :doc/role "admin"]
                   [:= :doc/role "guest"]]))
;; => (constantly false) - always returns false
```

#### Working with Residuals

Residual constraints can be converted back to policy expressions:

```clojure
(let [result (checker {:role "admin"})]
  (when (map? result)
    (p/residual->constraints (:residual result))))
;; => [[:> :doc/level 5] [:in :doc/status #{"active" "pending"}]]

;; Or get a simplified policy expression
(p/result->policy result)
;; => [:and [:> :doc/level 5] [:in :doc/status #{"active" "pending"}]]
```

### Bidirectional Evaluation (Planned)

**Status**: Partially implemented via `compile-policies`. The residual output provides
the "implied" constraints. Full bidirectional evaluation with constraint unification
is planned. See `CURRENT_STATE.md` for implementation roadmap.

The `implied` function will return constraints that would satisfy a policy given a desired result:

```clojure
;; Planned API
(implied MyGreatPolicy true)
;; Should return: Document with constraints like
;; {:actor-role [:= "admin"]
;;  :actioning-uri [:uri-match "myprotocol:" :doc/actor-name "/*"]}

;; With partial document
(implied MyGreatPolicy
         (->MapDocument {:actor-role "admin" :actor-name "test-actor"})
         true)
;; Should return: {:actioning-uri "myprotocol:test-actor/*"}
```

### Evaluators

An **Evaluator** customizes how policies are processed. Evaluators can:
- Transform AST to different representations (SQL, etc.)
- Provide custom operator implementations
- Optimize evaluation for specific backends

```clojure
(defrecord SQLEvaluator []
  p/Evaluator
  (eval-node [this node document context]
    ;; Transform to SQL WHERE clause
    ...))
```

**Current Status**: Only `DefaultEvaluator` exists. SQL and other evaluators are planned.

## Development

```bash
# Run tests
clojure -X:test

# Start REPL
clojure -M:repl

# Lint code
clojure -M:lint
```
