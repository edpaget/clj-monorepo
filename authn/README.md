# authn

First-party authentication with cookie-based sessions for Clojure web applications.

## Features

- **Session-based authentication**: Cookie-based sessions for first-party applications
- **Protocol-driven**: Pluggable credential validation and claims provisioning
- **Ring integration**: Middleware for easy integration with Ring applications
- **Session management**: Create, validate, refresh, and destroy sessions
- **Storage abstraction**: Pluggable session storage (in-memory provided, implement your own for production)
- **Ready-to-use handlers**: Login, logout, and whoami endpoints
- **Shared protocols**: CredentialValidator and ClaimsProvider protocols used by oidc-provider and oidc-github

## When to Use

Use `authn` when:
- Building a **first-party application** where you control both frontend and backend
- Need **cookie-based sessions** instead of JWT tokens
- Want **straightforward authentication** without OAuth2/OIDC complexity
- Have a **traditional web app** with login forms

Don't use `authn` if:
- Building an identity provider for third-party apps → use [oidc-provider](../oidc-provider)
- Need OAuth2/OIDC flows with authorization codes → use [oidc-provider](../oidc-provider)
- Building a mobile app or SPA that needs JWT tokens → use oidc-provider + oidc client

## Installation

Add to your `deps.edn`:

```clojure
{:deps {local/authn {:local/root "authn"}}}
```

Or use the alias:

```bash
clojure -X:authn
```

## Quick Start

### 1. Implement Credential Validator

```clojure
(require '[authn.protocol :as proto])

(defrecord DatabaseValidator [db]
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (when-let [user (db/find-user (:username credentials))]
      (when (password/check (:password credentials) (:password-hash user))
        (:id user)))))
```

### 2. Implement Claims Provider

```clojure
(defrecord DatabaseClaimsProvider [db]
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    (let [user (db/get-user user-id)]
      (cond-> {:sub user-id}
        (some #{"profile"} scope)
        (assoc :name (:name user)
               :picture (:avatar user))

        (some #{"email"} scope)
        (assoc :email (:email user)
               :email_verified (:email-verified user))))))
```

### 3. Create Authenticator

```clojure
(require '[authn.core :as authn]
         '[authn.store :as store])

(def authenticator
  (authn/create-authenticator
    {:credential-validator (->DatabaseValidator db)
     :claims-provider (->DatabaseClaimsProvider db)
     :session-store (store/create-session-store)
     :session-ttl-ms (* 24 60 60 1000)}))  ; 24 hours
```

### 4. Add Middleware

```clojure
(require '[authn.middleware :as mw]
         '[ring.middleware.session :as session])

(def app
  (-> handler
      (mw/wrap-authentication authenticator)
      (session/wrap-session {:cookie-name "session-id"
                            :cookie-attrs {:http-only true
                                          :secure true
                                          :same-site :lax}})))
```

### 5. Add Login/Logout Endpoints

```clojure
(require '[authn.handler :as handler])

(defn routes []
  [["POST" "/login" (handler/login-handler authenticator)]
   ["POST" "/logout" (handler/logout-handler authenticator)]
   ["GET" "/whoami" (handler/whoami-handler)]])
```

## Usage

### Authentication Flow

1. **Login**: User submits credentials to `/login`
2. **Validation**: CredentialValidator checks credentials
3. **Session Creation**: Session created with user claims
4. **Cookie Set**: Session ID stored in secure HTTP-only cookie
5. **Subsequent Requests**: Middleware validates session cookie
6. **Logout**: `/logout` destroys session and clears cookie

### Accessing User Information

After authentication middleware, request contains:

```clojure
{:authn/authenticated? true
 :authn/user-id "user-123"
 :authn/claims {:sub "user-123"
                :name "John Doe"
                :email "john@example.com"}}
```

### Example Handler

```clojure
(defn protected-handler [request]
  (if (:authn/authenticated? request)
    {:status 200
     :body {:message (str "Hello " (get-in request [:authn/claims :name]))}}
    {:status 401
     :body {:error "Unauthorized"}}))
```

### Requiring Authentication

```clojure
(def protected-app
  (-> handler
      mw/wrap-require-authentication  ; Returns 401 if not authenticated
      (mw/wrap-authentication authenticator)
      (session/wrap-session)))
```

### Session Refresh

Keep active sessions alive:

```clojure
(def app
  (-> handler
      (mw/wrap-session-refresh authenticator)  ; Extends session on each request
      (mw/wrap-authentication authenticator)
      (session/wrap-session)))
```

## API Reference

### Core Functions

#### `create-authenticator`

```clojure
(create-authenticator config)
```

Creates an authenticator instance. Config map:

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `:credential-validator` | Protocol | Yes | Validates user credentials |
| `:claims-provider` | Protocol | Yes | Provides user claims |
| `:session-store` | Protocol | No | Session storage (default: in-memory) |
| `:session-ttl-ms` | Integer | No | Session TTL in ms (default: 24h) |
| `:session-cookie-name` | String | No | Cookie name (default: "session-id") |
| `:session-cookie-secure?` | Boolean | No | Require HTTPS (default: true) |
| `:session-cookie-http-only?` | Boolean | No | HTTP only (default: true) |
| `:session-cookie-same-site` | Keyword | No | SameSite (default: :lax) |

#### `authenticate`

```clojure
(authenticate authenticator credentials)
(authenticate authenticator credentials scope)
```

Authenticates credentials and creates a session. Returns session ID on success, nil on failure.

#### `get-session`

```clojure
(get-session authenticator session-id)
```

Retrieves session data by session ID.

#### `logout`

```clojure
(logout authenticator session-id)
```

Destroys a session.

#### `refresh-session`

```clojure
(refresh-session authenticator session-id)
```

Extends session expiration.

### Middleware

#### `wrap-authentication`

```clojure
(wrap-authentication handler authenticator)
```

Authenticates requests using session cookies. Adds `:authn/authenticated?`, `:authn/user-id`, and `:authn/claims` to request.

#### `wrap-require-authentication`

```clojure
(wrap-require-authentication handler)
```

Requires authentication for all requests. Returns 401 for unauthenticated requests.

#### `wrap-session-refresh`

```clojure
(wrap-session-refresh handler authenticator)
```

Refreshes session expiration on each authenticated request.

### Handlers

#### `login-handler`

```clojure
(login-handler authenticator)
```

POST handler for login. Accepts JSON credentials, returns session info and sets cookie.

Request:
```json
{
  "username": "user",
  "password": "pass"
}
```

Response (success):
```json
{
  "success": true,
  "user-id": "user-123",
  "claims": {...}
}
```

#### `logout-handler`

```clojure
(logout-handler authenticator)
```

POST handler for logout. Destroys session and clears cookie.

#### `whoami-handler`

```clojure
(whoami-handler)
```

GET handler that returns current user info. Requires authentication middleware.

Response:
```json
{
  "authenticated": true,
  "user-id": "user-123",
  "claims": {...}
}
```

## Protocols

### CredentialValidator

```clojure
(defprotocol CredentialValidator
  (validate-credentials [this credential-hash client-id]))
```

Validates credentials and returns user ID. The `client-id` parameter can be nil for first-party apps.

### ClaimsProvider

```clojure
(defprotocol ClaimsProvider
  (get-claims [this user-id scope]))
```

Returns user claims based on scope. Scope is a vector of strings like `["profile" "email"]`.

### SessionStore

```clojure
(defprotocol SessionStore
  (create-session [this user-id claims])
  (get-session [this session-id])
  (update-session [this session-id session-data])
  (delete-session [this session-id])
  (cleanup-expired [this]))
```

Manages session storage. Implement for production persistence.

## Session Storage

### In-Memory Store

Provided for development:

```clojure
(require '[authn.store :as store])

(def session-store
  (store/create-session-store (* 24 60 60 1000)))  ; 24 hour TTL
```

### Production Storage

Implement `SessionStore` protocol for production:

```clojure
(defrecord RedisSessionStore [redis-conn ttl-ms]
  proto/SessionStore
  (create-session [_this user-id claims]
    (let [session-id (generate-id)
          session-data {:user-id user-id
                       :claims claims
                       :created-at (System/currentTimeMillis)
                       :expires-at (+ (System/currentTimeMillis) ttl-ms)}]
      (redis/setex redis-conn session-id (/ ttl-ms 1000) session-data)
      session-id))

  (get-session [_this session-id]
    (redis/get redis-conn session-id))

  (delete-session [_this session-id]
    (redis/del redis-conn session-id)
    true)

  (update-session [_this session-id session-data]
    (when-let [existing (redis/get redis-conn session-id)]
      (redis/set redis-conn session-id (merge existing session-data))
      true))

  (cleanup-expired [_this]
    ;; Redis handles expiration automatically
    0))
```

## Integration Examples

### With Reitit

```clojure
(require '[reitit.ring :as ring]
         '[authn.core :as authn]
         '[authn.middleware :as mw]
         '[authn.handler :as handler])

(def authenticator (authn/create-authenticator {...}))

(def app
  (ring/ring-handler
    (ring/router
      [["/api"
        ["/login" {:post (handler/login-handler authenticator)}]
        ["/logout" {:post (handler/logout-handler authenticator)}]
        ["/protected" {:get protected-handler
                      :middleware [mw/wrap-require-authentication]}]]]
      {:data {:middleware [(fn [handler]
                            (mw/wrap-authentication handler authenticator))]}})))
```

### With Compojure

```clojure
(require '[compojure.core :refer [defroutes POST GET]]
         '[authn.core :as authn]
         '[authn.middleware :as mw]
         '[authn.handler :as handler])

(def authenticator (authn/create-authenticator {...}))

(defroutes app-routes
  (POST "/login" [] (handler/login-handler authenticator))
  (POST "/logout" [] (handler/logout-handler authenticator))
  (GET "/protected" [] (-> protected-handler
                          mw/wrap-require-authentication)))

(def app
  (-> app-routes
      (mw/wrap-authentication authenticator)
      (session/wrap-session)))
```

### With GitHub Authentication

```clojure
(require '[oidc-github.core :as github]
         '[authn.core :as authn])

(def github-auth (github/create-github-authenticator
                  {:client-id "..."
                   :client-secret "..."}))

(def authenticator
  (authn/create-authenticator
    {:credential-validator (:credential-validator github-auth)
     :claims-provider (:claims-provider github-auth)}))

;; Now you have cookie-based sessions with GitHub authentication!
```

## Testing

Run tests:

```bash
# Test only this package
clojure -X:authn-test

# Test all packages
clojure -X:test-all
```

Example test:

```clojure
(deftest authentication-flow-test
  (let [auth (authn/create-authenticator {...})]
    (testing "successful authentication"
      (let [session-id (authn/authenticate auth {:username "user" :password "pass"})]
        (is (string? session-id))
        (let [session (authn/get-session auth session-id)]
          (is (= "user-123" (:user-id session))))))

    (testing "logout"
      (let [session-id (authn/authenticate auth {:username "user" :password "pass"})]
        (authn/logout auth session-id)
        (is (nil? (authn/get-session auth session-id)))))))
```

## Security Considerations

### Cookie Settings

For production, use secure cookie settings:

```clojure
(authn/create-authenticator
  {:credential-validator validator
   :claims-provider provider
   :session-cookie-secure? true      ; HTTPS only
   :session-cookie-http-only? true   ; No JavaScript access
   :session-cookie-same-site :strict ; CSRF protection
   :session-ttl-ms (* 4 60 60 1000)}) ; 4 hour timeout
```

### Session Cleanup

Periodically clean up expired sessions:

```clojure
;; Run every hour
(defn cleanup-task [authenticator]
  (let [count (authn/cleanup-sessions authenticator)]
    (log/info "Cleaned up" count "expired sessions")))
```

### Password Hashing

Never store plaintext passwords. Use a library like `buddy-hashers`:

```clojure
(require '[buddy.hashers :as hashers])

(defrecord PasswordValidator [db]
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (when-let [user (db/find-user (:username credentials))]
      (when (hashers/check (:password credentials) (:password-hash user))
        (:id user)))))
```

### CSRF Protection

Use Ring's CSRF middleware for forms:

```clojure
(require '[ring.middleware.anti-forgery :refer [wrap-anti-forgery]])

(def app
  (-> handler
      wrap-anti-forgery
      (mw/wrap-authentication authenticator)
      (session/wrap-session)))
```

## Comparison with OIDC Provider

| Feature | authn | oidc-provider |
|---------|-------|---------------|
| Use case | First-party apps | Identity provider for third-party apps |
| Flow | Direct login | OAuth2/OIDC authorization code |
| Tokens | Session cookies | JWT access/ID tokens |
| Redirects | No | Yes (to authorization page) |
| Complexity | Low | High |
| Standards | Custom | OAuth2/OIDC compliant |

## Related Packages

- [oidc-provider](../oidc-provider) - OIDC Provider for third-party authentication
- [oidc-github](../oidc-github) - GitHub OAuth integration (provides CredentialValidator)
- [oidc](../oidc) - OIDC Client library

## License

See the root repository for license information.
