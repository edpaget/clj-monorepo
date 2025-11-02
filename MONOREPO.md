# Monorepo Workflow Guide

## Philosophy

- All `clojure` commands run from the **repository root**
- Projects are composed via top-level aliases in `deps.edn`
- Each project maintains its own `deps.edn` for dependencies
- Local dependencies use `:local/root` (e.g., `db` depends on `exclusive-initializer`)

## Quick Reference

### Start a REPL

```bash
# All projects loaded
clojure -M:dev-all:repl

# Single project
clojure -M:db:repl

# Multiple specific projects
clojure -M:db:polix:repl
```

### Run Tests

```bash
# All projects
clojure -X:test-all

# Single project
clojure -X:db-test
clojure -X:exclusive-initializer-test
clojure -X:polix-test

# Via build script
clojure -X:build test-all
clojure -X:build test-project :project '"db"'
```

### Development Tools

```bash
# Lint all code
clojure -M:lint --lint .

# Format code
clojure -X:format

# Check for outdated deps
clojure -M:outdated

# Download all dependencies
clojure -P -M:dev-all:test-all
```

## Alias Categories

### Project Aliases
Load individual projects into the classpath:
- `:exclusive-initializer`
- `:db`
- `:polix`

### Test Aliases
Load projects with their test paths and dependencies:
- `:exclusive-initializer-test`
- `:db-test`
- `:polix-test`

### Aggregated Aliases
Work with multiple projects at once:
- `:dev-all` - All projects for development
- `:test-all` - All projects with tests

### Tool Aliases
Development tools available globally:
- `:repl` - nREPL server with Cider middleware
- `:lint` - clj-kondo
- `:format` - cljfmt
- `:outdated` - antq for dependency checking
- `:build` - tools.build utilities

## Adding a New Project

1. Create project directory with standard structure:
```bash
./scripts/new-project my-new-lib
```

2. Add aliases to root `deps.edn`:
```clojure
:my-new-lib
{:extra-deps {local/my-new-lib {:local/root "my-new-lib"}}}

:my-new-lib-test
{:extra-paths ["my-new-lib/test"]
 :jvm-opts    ["-Duser.timezone=UTC"]
 :extra-deps  {local/my-new-lib {:local/root "my-new-lib"}
               io.github.cognitect-labs/test-runner {:git/tag "v0.5.1" :git/sha "dfb30dd"}}}
```

3. Update `:dev-all` and `:test-all` aliases to include the new project

## Inter-Project Dependencies

Projects can depend on each other using `:local/root`:

```clojure
;; In db/deps.edn
{:deps {net.carcdr/exclusive-initializer {:local/root "../exclusive-initializer/"}
        ...}}
```

This allows you to:
- Work on multiple related projects simultaneously
- See changes immediately without rebuilding
- Maintain separate project boundaries

## REPL Workflow

```clojure
;; Start from root with all projects
clojure -M:dev-all:repl

;; In the REPL
(require '[db.core :as db] :reload)
(require '[exclusive-initializer.core :as ei] :reload)
(require '[polix.core :as polix] :reload)

;; Run tests for specific namespace
(require '[cognitect.test-runner.api :as test-api])
(test-api/test {:dirs ["db/test"]})
```

## Why This Pattern?

**Advantages:**
- Simple and transparent - just deps.edn features
- No additional tools required beyond Clojure CLI
- Easy to understand and debug
- Works well with all editors and tools
- Each project remains independently valid
- Flexible composition of subsets of projects

**Trade-offs:**
- Some duplication of alias definitions
- Manual maintenance of aggregated aliases
- Less automated than Polylith
- No built-in incremental testing

## References

- [Sean Corfield's Blog Series](https://corfield.org/blog/2021/02/23/deps-edn-monorepo/)
- [Clojure CLI Guide](https://clojure.org/guides/deps_and_cli)
