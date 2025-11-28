# oidc-google

Google OIDC integration for Clojure applications, built on top of the [`oidc`](../oidc) library.

## Features

- Full OIDC support (Google is a compliant OIDC provider)
- ID token validation using Google's JWKS
- Refresh token support
- Userinfo endpoint integration
- Provider-side authentication for [`oidc-provider`](../oidc-provider)
- Configurable caching for userinfo responses

## Installation

Add to your `deps.edn`:

```edn
{:deps {local/oidc-google {:local/root "../oidc-google"}}}
```

## Configuration

```clojure
(require '[oidc-google.core :as google])

(def config
  {:client-id     "your-client-id.apps.googleusercontent.com"
   :client-secret "your-client-secret"
   :redirect-uri  "https://your-app.com/auth/callback"
   :scopes        ["openid" "email" "profile"]  ; optional, these are defaults
   :cache-ttl-ms  300000})                      ; optional, default 5 minutes
```

## Client Usage

### Generate Authorization URL

```clojure
(google/authorization-url config "state-123")
;; => "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&scope=openid+email+profile&..."
```

With options:

```clojure
(google/authorization-url config "state-123"
  {:nonce       "nonce-456"           ; For replay protection
   :access-type "offline"             ; Request refresh token
   :prompt      "consent"             ; Force consent screen
   :login-hint  "user@example.com"})  ; Pre-fill email
```

### Exchange Authorization Code

```clojure
(google/exchange-code config "authorization-code-from-callback")
;; => {:access_token "ya29.xxx"
;;     :id_token "eyJhbGci..."
;;     :refresh_token "1//xxx"  ; if access_type=offline
;;     :expires_in 3599
;;     :token_type "Bearer"}
```

### Refresh Access Token

```clojure
(google/refresh-token config "refresh-token")
;; => {:access_token "ya29.new-token"
;;     :expires_in 3599
;;     :token_type "Bearer"}
```

### Validate ID Token

```clojure
(google/validate-id-token "id-token-string" "your-client-id")
;; => {:sub "123456789"
;;     :email "user@example.com"
;;     :email_verified true
;;     :name "John Doe"
;;     ...}

;; With options
(google/validate-id-token "id-token" "client-id"
  {:nonce  "expected-nonce"
   :leeway 60})  ; Clock skew tolerance in seconds
```

### Fetch User Info

```clojure
(google/fetch-userinfo "access-token")
;; => {:sub "123456789"
;;     :name "John Doe"
;;     :given_name "John"
;;     :family_name "Doe"
;;     :email "john@example.com"
;;     :email_verified true
;;     :picture "https://lh3.googleusercontent.com/..."
;;     :locale "en"
;;     :hd "example.com"}  ; Google Workspace domain, if applicable
```

## Provider Integration

Use Google as an authenticator for your OIDC provider:

```clojure
(require '[oidc-google.core :as google]
         '[oidc-provider.core :as provider])

(def google-config
  {:client-id     "your-client-id.apps.googleusercontent.com"
   :client-secret "your-client-secret"})

(def authenticator (google/create-google-authenticator google-config))

(def my-provider
  (provider/create-provider
    {:issuer               "https://your-app.com"
     :credential-validator (:credential-validator authenticator)
     :claims-provider      (:claims-provider authenticator)
     ;; ... other provider config
     }))
```

The credential validator accepts:
- `:id-token` - Validates the Google ID token signature and claims
- `:access-token` - Validates by fetching userinfo
- `:code` - Exchanges for tokens, then validates

## Ring Middleware

Since Google is a proper OIDC provider, you can use `oidc.ring` directly:

```clojure
(require '[oidc.ring :as oidc-ring])

(def oidc-config
  {:client-id     "your-client-id.apps.googleusercontent.com"
   :client-secret "your-client-secret"
   :issuer        "https://accounts.google.com"
   :redirect-uri  "https://your-app.com/auth/callback"
   :scopes        ["openid" "email" "profile"]})

(def app
  (-> handler
      (oidc-ring/oidc-middleware
        {:client           oidc-config
         :verify-id-token? true
         :success-fn       (fn [req tokens]
                             (assoc-in req [:session :user]
                                       (:sub (google/validate-id-token
                                               (:id_token tokens)
                                               (:client-id oidc-config)))))})))
```

## Claims Mapping

Google userinfo is mapped to OIDC standard claims:

| Google Field | OIDC Claim |
|--------------|------------|
| `sub` | `sub` |
| `name` | `name` |
| `given_name` | `given_name` |
| `family_name` | `family_name` |
| `email` | `email` |
| `email_verified` | `email_verified` |
| `picture` | `picture` |
| `locale` | `locale` |
| `hd` | `google_hd` (custom) |

## Domain Validation

This library does not perform domain validation. If you need to restrict access to specific Google Workspace domains, implement this in your application:

```clojure
(let [claims (google/validate-id-token id-token client-id)]
  (when-not (= "your-company.com" (:hd claims))
    (throw (ex-info "Unauthorized domain" {:domain (:hd claims)}))))
```

## Namespaces

| Namespace | Purpose |
|-----------|---------|
| `oidc-google.core` | Public API and configuration |
| `oidc-google.client` | Client-side OIDC flow helpers |
| `oidc-google.claims` | Userinfo fetching and claims transformation |
| `oidc-google.provider` | `authn.protocol` implementations |

## License

Copyright © 2024
