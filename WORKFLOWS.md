# Common Monorepo Workflows

## Daily Development

### Starting Your Day

```bash
# From the monorepo root
cd /Users/edward/Projects/clj-monorepo

# Start REPL with all projects
clojure -M:dev-all:repl

# Or start with specific projects you're working on
clojure -M:db:polix:repl
```

### Working on Multiple Projects

```clojure
;; In your REPL
(require '[db.core :as db] :reload)
(require '[exclusive-initializer.core :as ei] :reload)

;; Make changes to code, then reload
(require '[db.core :as db] :reload)

;; Run a specific namespace's tests
(require '[db.core-test] :reload)
(clojure.test/run-tests 'db.core-test)
```

## Testing Workflows

### Test Everything

```bash
# Run all tests across all projects
clojure -X:test-all
```

### Test Single Project

```bash
# Test just the db project
clojure -X:db-test

# Test just exclusive-initializer
clojure -X:exclusive-initializer-test

# Test just polix
clojure -X:polix-test
```

### Test from Build Script

```bash
# All tests
clojure -X:build test-all

# Specific project
clojure -X:build test-project :project '"db"'
```

## Code Quality

### Linting

```bash
# Lint entire monorepo
clojure -M:lint --lint .

# Lint specific project
clojure -M:lint --lint db/src

# Lint and show config
clojure -M:lint --lint . --config .clj-kondo/config.edn
```

### Formatting

```bash
# Format all code
clojure -X:format

# Check formatting without fixing
clojure -M:format check .
```

### Dependency Management

```bash
# Check for outdated dependencies across all projects
clojure -M:outdated

# Download all dependencies (useful for CI)
clojure -P -M:dev-all:test-all
```

## Working with Individual Projects

Even though we use the monorepo pattern, each project's `deps.edn` is still valid and can be used independently:

```bash
# Work on db project in isolation
cd db
clojure -M:repl

# Run db tests from within the project
cd db
clojure -X:test
```

However, **prefer running from the root** to ensure consistency and take advantage of the aggregated aliases.

## Adding Inter-Project Dependencies

When one project needs to depend on another:

```clojure
;; In the dependent project's deps.edn
{:deps {net.carcdr/exclusive-initializer {:local/root "../exclusive-initializer/"}
        ...}}
```

Benefits:
- Changes are immediately visible
- No build/install step needed
- REPL reloads pick up changes
- Maintains project boundaries

## CI/CD Workflows

### GitHub Actions Example

```yaml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: DeLaGuardo/setup-clojure@master
        with:
          cli: latest

      # Test all projects
      - name: Run tests
        run: clojure -X:test-all

      # Lint all code
      - name: Lint
        run: clojure -M:lint --lint .
```

### Test Individual Projects in CI

```yaml
strategy:
  matrix:
    project: [exclusive-initializer, db, polix]

steps:
  - name: Test ${{ matrix.project }}
    run: clojure -X:${{ matrix.project }}-test
```

## Editor Integration

### VSCode with Calva

1. Open monorepo root in VSCode
2. Jack-in with custom command: `clojure -M:dev-all:repl`
3. All projects available in same REPL session

### Emacs with Cider

```elisp
;; In your project .dir-locals.el
((clojure-mode . ((cider-clojure-cli-aliases . "dev-all:repl"))))
```

### IntelliJ with Cursive

1. Import as deps.edn project
2. Mark all `src` and `test` directories appropriately
3. Use aliases: `dev-all:repl` for REPL

## Troubleshooting

### Classpath Issues

```bash
# Verify what's on the classpath
clojure -Spath -M:dev-all

# Check for conflicts
clojure -Stree -M:dev-all
```

### Dependency Conflicts

```bash
# See full dependency tree
clojure -X:deps tree

# Find where a dependency comes from
clojure -X:deps find-versions :lib some/library
```

### Cache Issues

```bash
# Clear CLI cache
rm -rf .cpcache

# Force dependency re-download
clojure -P -M:dev-all:test-all
```

## Migration Tips

If you're used to working in individual project directories:

**Old way:**
```bash
cd db
clojure -M:repl
```

**New way:**
```bash
# From root
clojure -M:db:repl
```

**Old way:**
```bash
cd db
clojure -X:test
```

**New way:**
```bash
# From root
clojure -X:db-test
```

The benefit: You can now easily combine multiple projects and see changes across project boundaries immediately.
