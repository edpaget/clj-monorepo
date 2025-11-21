# Testing

The OIDC library includes comprehensive tests for both Clojure (JVM) and ClojureScript (JS) platforms.

## Test Structure

All test files use the `.cljc` extension and reader conditionals to support both platforms:

- `oidc.core-test` - Client configuration tests
- `oidc.discovery-test` - Discovery document URL construction tests
- `oidc.authorization-test` - Authorization flow tests (state/nonce generation, URL construction)
- `oidc.jwt-test` - JWT protocol and validator tests

## Running Tests

### Clojure (JVM)

```bash
clojure -X:test
```

Expected output:
```
Running tests in #{"test"}
Testing oidc.authorization-test
Testing oidc.core-test
Testing oidc.discovery-test
Testing oidc.jwt-test

Ran 7 tests containing 27 assertions.
0 failures, 0 errors.
```

### ClojureScript (JS/Node.js)

First, install npm dependencies:
```bash
npm install
```

Then compile and run tests:
```bash
npx shadow-cljs compile test
```

Or use the npm script:
```bash
npm run compile
```

Expected output:
```
[:test] Compiling ...
========= Running Tests =======================
Testing oidc.authorization-test
Testing oidc.core-test
Testing oidc.discovery-test
Testing oidc.jwt-test

Ran 7 tests containing 27 assertions.
0 failures, 0 errors.
```

## Test Coverage

### Platform-Agnostic Tests
- Client configuration with default and custom scopes
- Discovery URL construction with/without trailing slashes
- Random state/nonce generation
- Authorization URL construction with various parameters
- JWT header decoding

### Platform-Specific Tests
- **JVM**: Validator creation with buddy-sign
- **ClojureScript**: Validator creation with panva/jose

## Platform Differences

### URL Encoding
- **JVM**: Spaces encoded as `+` (e.g., `scope=openid+profile+email`)
- **ClojureScript**: Spaces encoded as `%20` (e.g., `scope=openid%20profile%20email`)

Tests account for these differences using reader conditionals.

### JWT Header Format
- **JVM**: Algorithm returned as keyword (`:rs256`)
- **ClojureScript**: Algorithm returned as string (`"RS256"`)

### Async Operations
ClojureScript implementations return promises/channels for HTTP and JWT operations, while JVM implementations return values directly. Tests are structured to handle both approaches.

## Continuous Testing

For development, use watch mode with shadow-cljs:
```bash
npm run watch
```

This will automatically recompile and rerun tests when files change.
