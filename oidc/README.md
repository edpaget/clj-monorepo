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
- **Ring Middleware** (JVM only): Ready-to-use Ring middleware for handling OIDC authentication flows

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

## Usage

### Ring Middleware (JVM)

The `oidc.ring` namespace provides ready-to-use Ring middleware that handles OIDC authentication flows automatically, eliminating the need to manually configure authentication routes in your application.

#### Quick Start

```clojure
(require '[oidc.core :as oidc]
         '[oidc.ring :as oidc-ring]
         '[ring.middleware.session :refer [wrap-session]])

;; Create OIDC client configuration
(def client
  (oidc/create-client
    {:issuer "https://accounts.google.com"
     :client-id "your-client-id.apps.googleusercontent.com"
     :client-secret "your-client-secret"
     :redirect-uri "http://localhost:3000/auth/callback"
     :scopes ["openid" "email" "profile"]}))

;; Add middleware to your Ring handler
(def app
  (-> handler
      (oidc-ring/oidc-middleware
        {:client client
         :callback-opts {:success-fn (fn [req tokens]
                                        (response/redirect "/dashboard"))}})
      (oidc-ring/wrap-oidc-tokens)
      (wrap-session {:cookie-attrs {:http-only true
                                     :secure true
                                     :same-site :lax}})))
```

#### Automatic Routes

The middleware automatically handles these routes:

- **`GET /auth/login`** - Initiates OAuth flow, redirects to provider
- **`GET /auth/callback`** - Handles OAuth callback, exchanges code for tokens
- **`GET /POST /auth/logout`** - Clears session and optionally redirects to provider logout

#### Configuration Options

```clojure
(oidc-ring/oidc-middleware handler
  {:client client
   :login-path "/auth/login"          ;; default
   :callback-path "/auth/callback"    ;; default
   :logout-path "/auth/logout"        ;; default

   ;; Login options
   :login-opts {:prompt "consent"     ;; force consent screen
                :max-age 3600         ;; max auth age in seconds
                :ui-locales "en"      ;; preferred UI locale
                :additional-params {}} ;; extra query params

   ;; Callback options
   :callback-opts {:success-fn (fn [req tokens]
                                 ;; Custom success handler
                                 (response/redirect "/dashboard"))
                   :error-fn (fn [req error]
                               ;; Custom error handler
                               (response/response {:error error}))
                   :verify-id-token? true} ;; verify ID token (default true)

   ;; Logout options
   :logout-opts {:post-logout-redirect-uri "http://localhost:3000/"
                 :end-session-redirect? true}}) ;; use OIDC RP-Initiated Logout
```

#### Accessing Tokens

Use `wrap-oidc-tokens` middleware to add tokens to the request:

```clojure
(defn my-handler [request]
  (if-let [access-token (get-in request [:oidc/tokens :access_token])]
    {:status 200 :body (str "Authenticated with token: " access-token)}
    {:status 401 :body "Not authenticated"}))

(def app
  (-> my-handler
      (oidc-ring/wrap-oidc-tokens)
      (wrap-session)))
```

#### Session Storage

The middleware stores authentication state in Ring sessions:

- **State and Nonce**: Stored during login, validated during callback
- **Tokens**: Access token, ID token, refresh token (if provided)

Tokens are stored under `::oidc-ring/tokens` in the session and automatically exposed via `:oidc/tokens` in the request when using `wrap-oidc-tokens`.

### Low-Level API

For more control over the authentication flow, you can use the underlying functions directly:

```clojure
(require '[oidc.authorization :as auth]
         '[oidc.discovery :as discovery])

;; Fetch discovery document
(def discovery-doc (discovery/fetch-discovery-document "https://accounts.google.com"))

;; Generate authorization URL
(def auth-url
  (auth/authorization-url
    (:authorization_endpoint discovery-doc)
    "your-client-id"
    "http://localhost:3000/callback"
    {:scope "openid email profile"
     :state (auth/generate-state)
     :nonce (auth/generate-nonce)}))

;; Exchange authorization code for tokens
(def tokens
  (auth/exchange-code
    (:token_endpoint discovery-doc)
    "authorization-code"
    "your-client-id"
    "your-client-secret"
    "http://localhost:3000/callback"
    {}))
```

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
