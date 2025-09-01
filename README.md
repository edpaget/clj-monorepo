# Clojure Monorepo

Extracts utilities I've built on while working on personal clojure projects into libraries that I can resue across projects while not needing to build any release tooling or manage updating dependencies across projects. 

Also hosts the apps themselves. 

## Structure

Each utility and app has it's own folder with a deps.edn file. 
Build scripts live in the `/build` directory.
Shell scripts for common tasks live in the `/scripts` directory.

## Usage

The top-level deps.edn file includes all projects in the repo meaning they can all be used from a single nrepl connection.

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
(require '[build :refer [test-all lint-all clean-all deps-all outdated]])

(test-all)     ; Run tests for all projects
(lint-all)     ; Lint all projects  
(clean-all)    ; Clean build artifacts
(deps-all)     ; Download dependencies for all projects
(outdated)     ; Check for outdated dependencies
```

## Configuration

- **`.clj-kondo/config.edn`** - Shared linting configuration
- **`.cljfmt.edn`** - Shared formatting rules
- Projects automatically inherit these via symlinks

## License

All code is distributed under the terms of the Affero General Public License (see LICENSE).
