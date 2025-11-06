# Clojure Monorepo

Extracts utilities I've built on while working on personal clojure projects into libraries that I can resue across projects while not needing to build any release tooling or manage updating dependencies across projects. 

Also hosts the apps themselves. 

## Structure

Each utility and app has it's own folder with a deps.edn file. 
Build scripts live in the `/build` directory.
Shell scripts for common tasks live in the `/scripts` directory.

## Usage

The top-level `deps.edn` provides aliases for each project that can be composed together for development and testing.

### Available Aliases

**Project Aliases** - Include individual projects:
- `:exclusive-initializer` - Exclusive initializer library
- `:db` - Database utilities
- `:polix` - Polix library

**Test Aliases** - Run tests for specific projects:
- `:exclusive-initializer-test` - Test exclusive-initializer
- `:db-test` - Test db
- `:polix-test` - Test polix

**Aggregated Aliases** - Work with all projects:
- `:dev-all` - All projects loaded for development
- `:test-all` - Run all tests across the monorepo

### Working from the Root Directory

All commands should be run from the monorepo root directory using the top-level aliases:

```bash
# Start a REPL with all projects loaded
clojure -M:dev-all:repl

# Run all tests
clojure -X:test-all

# Run tests for a specific project
clojure -X:db-test

# Work with a single project in the REPL
clojure -M:db:repl

# Combine multiple projects
clojure -M:db:polix:repl
```

### Creating New Projects

```bash
./scripts/new-project my-new-lib
```

Creates a new project with:
- Standard directory structure (`src/`, `test/`)
- deps.edn with common aliases
- Initial namespace files
- Symlinks to shared configuration files

### Running Tests

```bash
# Test all projects
./scripts/test all

# Test specific project
./scripts/test my-new-lib
```

### Linting

```bash
# Lint all projects
./scripts/lint all

# Lint specific project
./scripts/lint my-new-lib
```

### Code Formatting

```bash
# Format all projects
./scripts/format all

# Format specific project
./scripts/format my-new-lib

# Check formatting without fixing
./scripts/format check all
./scripts/format check my-new-lib
```

### Documentation

Generate API documentation for all packages:

```bash
# Generate documentation locally
./scripts/generate-docs.sh

# View locally (requires Python 3)
python3 -m http.server 8000 -d docs-output
```

Documentation is automatically published to GitHub Pages when changes are pushed to `main`. The published docs will be available at your GitHub Pages URL.

### REPL

```bash
# Start monorepo-wide REPL
./scripts/repl

# Start project-specific REPL
./scripts/repl my-new-lib
```

### Build Functions

The build utilities in `build/build.clj` provide programmatic access to monorepo operations:

```clojure
;; From the root directory REPL
(require '[build :refer [test-all test-project lint-all clean-all deps-all outdated]])

(test-all)              ; Run tests for all projects using :test-all alias
(test-project "db")     ; Run tests for a specific project using :{project}-test alias
(lint-all)              ; Lint all projects
(clean-all)             ; Clean build artifacts
(deps-all)              ; Download dependencies for all projects
(outdated)              ; Check for outdated dependencies
```

Or use the CLI directly:

```bash
# Run all tests
clojure -X:build test-all

# Start REPL with all projects
clojure -X:build repl
```

## Configuration

- **`.clj-kondo/config.edn`** - Shared linting configuration
- **`.cljfmt.edn`** - Shared formatting rules
- Projects automatically inherit these via symlinks

## License

All code is distributed under the terms of the Affero General Public License (see LICENSE).
