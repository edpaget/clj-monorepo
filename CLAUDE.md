# Monorepo Projects

This monorepo contains the following projects:

## Libraries

| Project | Description |
|---------|-------------|
| **exclusive-initializer** | Test fixture coordination ensuring initialization runs once across parallel tests |
| **db** | Database utilities with next.jdbc wrappers, HoneySQL, connection pooling, migrations, and PostgreSQL extensions |
| **polix** | Declarative policy DSL for constraint-based policies evaluated against documents |
| **graphql-server** | GraphQL schema definition using Malli with Lacinia/Ring, macro-based resolvers |
| **authn** | Session-based cookie authentication with pluggable validators and Ring middleware |
| **oidc** | Cross-platform (Clj/ClojureScript) OIDC client with discovery, JWT validation, and Ring middleware |
| **oidc-provider** | OpenID Connect Provider supporting authorization code, refresh token, and client credentials grants |
| **oidc-github** | GitHub OAuth/OIDC integration for both provider and client-side authentication |
| **oidc-google** | Google OIDC integration with ID token validation, refresh tokens, and provider-side authentication |
| **cljs-tlr** | ClojureScript wrapper for @testing-library/react with JSDom support |
| **bashketball-schemas** | Shared Malli schemas for Bashketball ecosystem (cards, enums, user) |
| **bashketball-game** | CLJC game state engine for Bashketball with data-driven actions and Malli validation |

## Applications

| Project | Description |
|---------|-------------|
| **bashketball-editor-api** | GraphQL API for trading card game editor with GitHub OAuth, Git-based card storage |
| **bashketball-editor-ui** | React/ClojureScript frontend for trading card game editor with Apollo GraphQL client |

## Project Details

### exclusive-initializer
Test fixture library using thread-safe locking to ensure initialization code runs only once.
- **Namespaces**: `exclusive-initializer.core`

### db
Database abstraction layer for Integrant-driven projects.
- **Namespaces**: `db.core`, `db.connection-pool`, `db.jdbc-ext`, `db.migrate`, `db.test-utils`
- **Features**: HoneySQL query building, Ragtime migrations, C3P0 pooling, PostgreSQL enums/JSON

### polix
Policy evaluation engine using a unified constraint model.
- **Namespaces**: `polix.core`, `polix.parser`, `polix.document`, `polix.evaluator`, `polix.policy`, `polix.ast`
- **Features**: Either monad error handling, Malli schemas

### graphql-server
Lacinia wrapper with Malli schema support.
- **Namespaces**: `graphql-server.core`, `graphql-server.schema`, `graphql-server.ring`
- **Features**: `defresolver` macro, automatic argument coercion

### authn
First-party authentication for web applications.
- **Namespaces**: `authn.core`, `authn.middleware`, `authn.store`, `authn.protocol`, `authn.handler`
- **Features**: Pluggable credential validators, claims providers

### oidc
OpenID Connect client (works in both Clojure and ClojureScript).
- **Namespaces**: `oidc.core`, `oidc.authorization`, `oidc.discovery`, `oidc.jwt`, `oidc.ring`
- **Features**: Discovery document fetching, JWT validation (buddy-sign on JVM, panva/jose on JS)

### oidc-provider
Full OIDC identity provider implementation.
- **Namespaces**: `oidc-provider.core`, `oidc-provider.authorization`, `oidc-provider.token-endpoint`, `oidc-provider.discovery`
- **Features**: Authorization code flow, refresh tokens, client credentials, pluggable storage

### oidc-github
GitHub-specific OIDC/OAuth integration.
- **Namespaces**: `oidc-github.provider`, `oidc-github.client`, `oidc-github.claims`
- **Features**: GitHub Enterprise support, organization validation with caching

### oidc-google
Google OIDC integration using the base oidc library.
- **Namespaces**: `oidc-google.core`, `oidc-google.client`, `oidc-google.claims`, `oidc-google.provider`
- **Features**: ID token validation via JWKS, refresh token support, userinfo endpoint integration

### bashketball-editor-api
Trading card game editor backend.
- **Namespaces**: `bashketball-editor-api.system`, `bashketball-editor-api.handler`, `bashketball-editor-api.graphql.schema`
- **Features**: GitHub OAuth, Git-based card/set storage (JGit), PostgreSQL for users/sessions

### bashketball-editor-ui
Trading card game editor frontend built with ClojureScript and React.
- **Namespaces**: `bashketball-editor-ui.core`, `bashketball-editor-ui.views`, `bashketball-editor-ui.graphql`
- **Features**: UIx (React wrapper), Apollo GraphQL client, Tailwind CSS, Radix UI components

### cljs-tlr
React Testing Library wrapper for ClojureScript.
- **Namespaces**: `cljs-tlr.core`, `cljs-tlr.render`, `cljs-tlr.screen`, `cljs-tlr.user-event`, `cljs-tlr.async`, `cljs-tlr.fixtures`
- **Features**: JSDom setup via shadow-cljs `:prepend-js`, UIx support

### bashketball-schemas
Shared Malli schemas for the Bashketball trading card game ecosystem.
- **Namespaces**: `bashketball-schemas.core`, `bashketball-schemas.enums`, `bashketball-schemas.card`, `bashketball-schemas.user`
- **Features**: CLJC (works in Clojure and ClojureScript), card type schemas, shared enums (CardType, Size, GameStatus, Team, GamePhase)

### bashketball-game
Game state engine for Bashketball trading card game.
- **Namespaces**: `bashketball-game.schema`, `bashketball-game.state`, `bashketball-game.board`, `bashketball-game.actions`, `bashketball-game.event-log`
- **Features**: CLJC, data-driven actions with Malli validation, 5x14 hex board, event log for replay

---

# General Guidelines

Be concise, straightforward, and avoid hyperbole. 

# Clojure Style Guidelines

## Conditionals
- Use `if` for single condition checks, not `cond`
- Only use `cond` for multiple condition branches
- Prefer `if-let` and `when-let` for binding and testing a value in one step
- Consider `when` for conditionals with single result and no else branch
- consider `cond->`, and `cond->>`

## Variable Binding
- Minimize code points by avoiding unnecessary `let` bindings
- Only use `let` when a value is used multiple times or when clarity demands it
- Inline values used only once rather than binding them to variables
- Use threading macros (`->`, `->>`) to eliminate intermediate bindings

## Parameters & Destructuring
- Use destructuring in function parameters when accessing multiple keys
- Example: `[{:keys [::zloc ::match-form] :as ctx}]` for namespaced keys instead of separate `let` bindings
- Example: `[{:keys [zloc match-form] :as ctx}]` for regular keywords

## Control Flow
- Track actual values instead of boolean flags where possible
- Use early returns with `when` rather than deeply nested conditionals
- Return `nil` for "not found" conditions rather than objects with boolean flags

## Comments
- Do not include comments in generated code, unless specifically asked to.

## Docstrings

All docstrings should follow the Clojure community style guide and be written in a narrative form with markdown formatting. They should render well with [cljdoc](https://cljdoc.org/) using `[[namespace/symbol]]` syntax for automatic linking.

### Namespace Docstrings
- Every namespace must have a docstring
- Start with a one-line summary (what the namespace provides)
- Follow with extended description for complex modules
- Include usage examples with code blocks when appropriate
- Use `[[namespace/symbol]]` links to reference related namespaces, functions, or protocols
- Organize with markdown headers (`##`, `###`) for major sections

Example:
```clojure
(ns polix.evaluator
  "Policy evaluation engine.

  Evaluates AST nodes against documents and contexts, transforming parsed
  policies into concrete values. Uses the Either monad for error handling
  and supports pluggable evaluators via the Evaluator protocol.")
```

### Function Docstrings
- All public functions must have docstrings
- Private functions should have docstrings when sufficiently complex
- Write in narrative form, describing what the function does and how to use it
- Avoid structured sections like `Args:`, `Returns:`, `Throws:`, or `Raises:`
- Instead, write flowing prose that naturally describes parameters, return values, and behavior
- Use markdown formatting for emphasis, code snippets, and lists
- Use `[[namespace/symbol]]` to link to related functions, protocols, or types
- Include examples when they aid understanding
- For multi-arity functions, describe the different use cases naturally in the narrative

Example:
```clojure
(defn evaluate
  "Evaluates an AST node with a [[doc/Document]] and optional context.

  Takes an AST node (typically from a [[polix.policy/Policy]]) and evaluates it
  against the provided document. Uses `postwalk` to traverse the AST, converting
  each node's children into lazy thunks that return evaluated values.

  When called with just an `ast` and `document`, uses the [[default-evaluator]]
  with an empty context. You can optionally provide a custom evaluator and context
  map containing `:uri`, `:environment`, and other evaluation-specific data.

  Returns an Either monad - `Right` with the result on success, `Left` with an
  error map on failure. The evaluator does not recurse; recursion is handled by
  the postwalk traversal."
  ([ast document]
   (evaluate ast document default-evaluator {}))
  ([ast document evaluator]
   (evaluate ast document evaluator {}))
  ([ast document evaluator context]
   ...))
```

### Protocol and Multimethod Docstrings
- Protocols and multimethods must have docstrings
- Protocol methods should have individual docstrings
- Write in narrative form, describing the contract and expected behavior
- Avoid structured sections; describe parameters and return values naturally

Example:
```clojure
(defprotocol Evaluator
  "Protocol for evaluating AST nodes.

  Evaluators receive an AST node, a [[doc/Document]], and optional context,
  and return an Either monad containing either an error or the evaluated value."

  (eval-node [this node document context]
    "Evaluates the given AST node with the provided document and context.

    Returns an Either monad - Right with the result on success, Left with an
    error map on failure."))
```

### Macro Docstrings
- Macros must have docstrings
- Write in narrative form describing what the macro does
- Include examples showing usage
- Describe the generated code naturally within the narrative
- Avoid structured sections

Example:
```clojure
(defmacro defpolicy
  "Defines a policy with a name, optional docstring, and policy expression.

  A policy is a declarative rule that evaluates to boolean true/false.
  The macro parses the policy expression into an AST and extracts the
  required document schema, generating a `def` form that creates a
  [[Policy]] record. Throws an exception if the policy expression cannot
  be parsed.

  Examples:

      (defpolicy MyPolicy
        \"Only admins can access\"
        [:= :doc/actor-role \"admin\"])

      (defpolicy AnotherPolicy
        [:or [:= :doc/role \"admin\"]
             [:= :doc/role \"user\"]])"
  [name & args]
  ...)
```

### Schema and Data Structure Docstrings
- Malli schemas and important `def` forms should have docstrings
- Describe what the schema validates or what the data represents

Example:
```clojure
(def Config
  "Malli schema for OIDC client configuration."
  [:map
   [:issuer :string]
   [:client-id :string]
   ...])
```

## Malli
- All public functions should use malli schemas. 

## Nesting
- Minimize nesting levels by using proper control flow constructs
- Use threading macros (`->`, `->>`) for sequential operations

## Function Design
- Functions should generally do one thing
- Pure functions preferred over functions with side effects
- Return useful values that can be used by callers
- smaller functions make edits faster and reduce the number of tokens
- reducing tokens makes me happy

## Library Preferences
- Prefer `clojure.string` functions over Java interop for string operations
  - Use `str/ends-with?` instead of `.endsWith`
  - Use `str/starts-with?` instead of `.startsWith`
  - Use `str/includes?` instead of `.contains`
  - Use `str/blank?` instead of checking `.isEmpty` or `.trim`
- Follow Clojure naming conventions (predicates end with `?`)
- Favor built-in Clojure functions that are more expressive and idiomatic

## ClojureScript JavaScript Interop
- Prefer keyword access over direct property interop for JavaScript objects
- The `bashketball-editor-ui` project extends `ILookup` on JS objects, enabling idiomatic access:
  ```clojure
  ;; Preferred - keyword access
  (:name user)
  (:id card)
  (get response :data)

  ;; Avoid - direct interop
  (.-name user)
  (.-id card)
  ```
- This pattern works because `object` is extended with `ILookup` in `bashketball-editor-ui.core`:
  ```clojure
  (extend-type object
    ILookup
    (-lookup ([o k] (goog.object/get o (name k)))
      ([o k not-found] (goog.object/get o (name k) not-found))))
  ```
- Benefits: more idiomatic, works with destructuring, consistent with Clojure map access
- Use this pattern in all ClojureScript projects that interact with JS objects

## REPL best pratices
- Always reload namespaces with `:reload` flag: `(require '[namespace] :reload)`
- Always change into namespaces that you are working on

## Testing Best Practices
- Always reload namespaces before running tests with `:reload` flag: `(require '[namespace] :reload)`
- Test both normal execution paths and error conditions
- the app.test-utils namespace and sub namespace contain fixtures and macros for helping with tests
- use the app.test-utils/with-inserted-data macro to create models in the database
- prefer using userEvent instead of fireEvent
- use small deftest forms that have 5 or fewer assertions.
- avoid using with-redefs

## Using Shell Commands
- Prefer the idiomatic `clojure.java.shell/sh` for executing shell commands
- Always handle potential errors from shell command execution
- Use explicit working directory for relative paths: `(shell/sh "cmd" :dir "/path")`
- For testing builds and tasks, run `clojure -X:test` instead of running tests piecemeal
- When capturing shell output, remember it may be truncated for very large outputs
- Consider using shell commands for tasks that have mature CLI tools like diffing or git operations

## Formatting and Linting

The monorepo uses cljfmt for formatting and clj-kondo for linting. Both are run from the repository root.

### Formatting
Format requires a `:paths` argument as EDN:
```bash
# Format entire monorepo
clojure -X:format :paths '["."]'

# Format a specific project
clojure -X:format :paths '["bashketball-ui"]'

# Format multiple projects
clojure -X:format :paths '["bashketball-ui" "bashketball-editor-ui"]'
```

### Linting
Lint uses clj-kondo with `--lint` flag:
```bash
# Lint specific directories
clojure -M:lint --lint bashketball-ui/src bashketball-editor-ui/src

# Lint a project's src and test
clojure -M:lint --lint oidc/src oidc/test
```

## Context Maintenance
- Use `clojure_eval` with `:reload` to ensure you're working with the latest code
- always switch into `(in-ns ...)` the namespace that you are working on
- Keep function and namespace references fully qualified when crossing namespace boundaries
