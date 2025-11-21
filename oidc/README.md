# OIDC Client

A Clojure/ClojureScript (CLJC) implementation of an OpenID Connect (OIDC) client library.

## Overview

This library provides a client implementation for OpenID Connect authentication flows, including:

- Discovery document fetching and caching
- Authorization Code Flow
- JWT token validation (ID tokens)
- Token introspection and refresh
- JWKS (JSON Web Key Set) handling

## Features

- **Cross-Platform**: Works in both Clojure (JVM) and ClojureScript (JS) environments
- **Standards Compliant**: Implements OIDC Core 1.0 specification
- **JWT Validation**: Full support for validating ID tokens with signature verification
- **Discovery**: Automatic configuration via OIDC Discovery endpoints
- **Flexible**: Works with any OIDC-compliant identity provider

## Platform-Specific Dependencies

### Clojure (JVM)
- `buddy-sign` and `buddy-core` for JWT/JWS/JWE cryptographic operations
- `clj-http` for HTTP requests to OIDC endpoints
- `cheshire` for JSON parsing
- `malli` for schema validation

### ClojureScript (JS)
- `panva/jose` (npm) for JWT/JWS/JWE cryptographic operations
- `cljs-http` for HTTP requests to OIDC endpoints
- `malli` for schema validation

## Architecture

The library uses a protocol-based abstraction layer to provide platform-specific implementations:
- **JVM**: Uses buddy-sign with Java cryptography (BouncyCastle)
- **JS**: Uses panva/jose with Web Crypto API

ClojureScript functions that perform async operations (HTTP requests, JWT validation) return promises/channels, while Clojure versions return values directly.

## Documentation

Full API documentation is available at [carcdr.net/clj-monorepo/oidc/](https://carcdr.net/clj-monorepo/oidc/).

## Development

### Clojure (JVM)

Run tests:
```bash
clojure -X:test
```

Start a REPL:
```bash
clojure -M:repl
```

### ClojureScript (JS)

Install npm dependencies:
```bash
npm install
```

Compile and run tests:
```bash
npm run compile
```

Watch mode for development:
```bash
npm run watch
```

## License

See LICENSE file in repository root.
