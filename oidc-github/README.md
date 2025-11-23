# oidc-github

GitHub OAuth and OIDC integration for Clojure. Provides both provider-side authentication (for use with [oidc-provider](../oidc-provider)) and client-side OAuth helpers with GitHub-specific defaults.

## Features

- **Provider-side**: Use GitHub as an authenticator for your OIDC provider
- **Client-side**: Simplified GitHub OAuth flow with sensible defaults
- **Organization validation**: Optionally restrict access to members of specific GitHub organizations
- **Enterprise support**: Works with GitHub Enterprise Server
- **Caching**: Built-in response caching to respect GitHub API rate limits
- **Standard claims**: Maps GitHub user data to standard OIDC claims

## Installation

Add to your `deps.edn`:

```clojure
{:deps {local/oidc-github {:local/root "oidc-github"}}}
```

Or use the alias defined in the root `deps.edn`:

```bash
clojure -X:oidc-github
```

## Usage

### Provider-Side (Authenticator)

Use GitHub as an authenticator for your OIDC provider:

```clojure
(require '[oidc-github.core :as github]
         '[oidc-provider.core :as provider])

;; Create GitHub authenticator
(def config
  {:client-id "your-github-oauth-app-id"
   :client-secret "your-github-oauth-app-secret"
   :required-org "your-company"        ;; Optional: restrict to org members
   :validate-org? true})               ;; Optional: enable org validation

(def authenticator (github/create-github-authenticator config))

;; Use with oidc-provider
(def oidc-provider
  (provider/create-provider
   {:issuer "https://your-app.com"
    :authorization-endpoint "https://your-app.com/authorize"
    :token-endpoint "https://your-app.com/token"
    :jwks-uri "https://your-app.com/jwks"
    :credential-validator (:credential-validator authenticator)
    :claims-provider (:claims-provider authenticator)}))
```

The authenticator accepts credentials in two formats:

```clojure
;; Direct access token
{:access-token "ghp_xxxxxxxxxxxx"}

;; Authorization code (will be exchanged for token)
{:code "authorization-code-from-callback"}
```

### Client-Side (OAuth Flow)

Use GitHub as an OAuth provider:

```clojure
(require '[oidc-github.core :as github])

(def config
  {:client-id "your-github-oauth-app-id"
   :client-secret "your-github-oauth-app-secret"
   :redirect-uri "https://your-app.com/oauth/callback"
   :scopes ["user:email" "read:user" "read:org"]})

;; Step 1: Generate authorization URL
(def auth-url (github/authorization-url config "random-state-value"))
;; => "https://github.com/login/oauth/authorize?client_id=...&state=..."

;; Redirect user to auth-url

;; Step 2: Handle callback and exchange code for token
(def token-response (github/exchange-code config "code-from-callback"))
;; => {:access_token "ghp_...", :token_type "bearer", :scope "..."}

;; Step 3: Fetch user information
(def user-data (github/fetch-user (:access_token token-response)))
;; => {:profile {...}, :emails [...], :orgs [...]}
```

### GitHub Enterprise

Both provider and client support GitHub Enterprise Server:

```clojure
(def enterprise-config
  {:client-id "your-client-id"
   :client-secret "your-client-secret"
   :enterprise-url "https://github.your-company.com"})

;; Works with both authenticator and client functions
(def authenticator (github/create-github-authenticator enterprise-config))
(def auth-url (github/authorization-url enterprise-config "state"))
```

## Configuration

### Configuration Options

| Key | Type | Required | Default | Description |
|-----|------|----------|---------|-------------|
| `:client-id` | String | Yes | - | GitHub OAuth App client ID |
| `:client-secret` | String | Yes | - | GitHub OAuth App client secret |
| `:redirect-uri` | String | No | - | OAuth callback URL |
| `:scopes` | Vector | No | `["user:email" "read:user" "read:org"]` | OAuth scopes to request |
| `:enterprise-url` | String | No | - | Base URL for GitHub Enterprise Server |
| `:required-org` | String | No | - | GitHub org users must belong to |
| `:validate-org?` | Boolean | No | `false` | Whether to validate org membership |
| `:cache-ttl-ms` | Integer | No | `300000` (5 min) | Cache TTL for GitHub API responses |

### OAuth Scopes

The default scopes provide access to:

- `user:email` - Read user email addresses
- `read:user` - Read user profile information
- `read:org` - Read organization membership

Adjust scopes based on your needs. See [GitHub's OAuth scopes documentation](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps) for all available scopes.

## OIDC Claims

GitHub user data is mapped to standard OIDC claims:

### Standard Claims

| Claim | Source | Description |
|-------|--------|-------------|
| `sub` | User ID | GitHub user ID (as string) |
| `preferred_username` | Login | GitHub username |
| `name` | Name | User's full name |
| `email` | Primary email | Primary verified email address |
| `email_verified` | Email verification | Always `true` if email is present |
| `profile` | Profile URL | GitHub profile URL |
| `picture` | Avatar URL | User's avatar URL |

### GitHub-Specific Claims

| Claim | Source | Description |
|-------|--------|-------------|
| `github_login` | Login | GitHub username (duplicate of `preferred_username`) |
| `github_orgs` | Organizations | Vector of organization logins user belongs to |
| `github_company` | Company | Company name from user's profile |

### Scope Filtering

Claims are filtered based on requested scopes:

- `profile` scope: Includes `name`, `preferred_username`, `profile`, `picture`
- `email` scope: Includes `email`, `email_verified`
- GitHub claims (`github_*`) are always included

## Organization Validation

Restrict access to members of specific GitHub organizations:

```clojure
(def config
  {:client-id "your-client-id"
   :client-secret "your-client-secret"
   :required-org "my-company"
   :validate-org? true})

(def authenticator (github/create-github-authenticator config))

;; Users not in "my-company" org will be rejected
```

This is useful for:
- Internal tools restricted to company members
- SaaS apps with organization-based access
- Ensuring users belong to authorized organizations

## Caching

The library includes built-in caching for GitHub API responses:

- Reduces API calls to GitHub
- Respects GitHub's rate limits
- Default TTL: 5 minutes (configurable)
- Cache key: `[access-token enterprise-url]`

```clojure
(def config
  {:client-id "your-client-id"
   :client-secret "your-client-secret"
   :cache-ttl-ms (* 10 60 1000)})  ;; 10 minutes

(def authenticator (github/create-github-authenticator config))
```

**Note**: User data changes on GitHub won't be reflected until the cache expires. This is generally acceptable for authentication use cases.

## Testing

Run tests with:

```bash
# Test only this package
clojure -X:oidc-github-test

# Test all packages
clojure -X:test-all
```

## Creating a GitHub OAuth App

1. Go to **Settings** → **Developer settings** → **OAuth Apps** → **New OAuth App**
2. Fill in:
   - **Application name**: Your app name
   - **Homepage URL**: Your app's URL
   - **Authorization callback URL**: `https://your-app.com/oauth/callback`
3. Click **Register application**
4. Note the **Client ID** and generate a **Client Secret**
5. Use these values in your configuration

For GitHub Enterprise, use your enterprise instance's settings page.

## GitHub Apps vs OAuth Apps

This library supports **GitHub OAuth Apps**, which:

- ✅ Are simpler to set up
- ✅ Work well for user authentication
- ✅ Have straightforward OAuth flows
- ❌ Don't support refresh tokens

If you need refresh tokens or more advanced features, consider using **GitHub Apps** instead, which require different integration patterns not currently supported by this library.

## API Documentation

See the docstrings in the source code for detailed API documentation:

- [`oidc-github.core`](src/oidc_github/core.clj) - Main API
- [`oidc-github.provider`](src/oidc_github/provider.clj) - Provider-side implementation
- [`oidc-github.client`](src/oidc_github/client.clj) - Client-side helpers
- [`oidc-github.claims`](src/oidc_github/claims.clj) - Claims mapping and GitHub API

## Examples

### Complete Provider Example

```clojure
(require '[oidc-github.core :as github]
         '[oidc-provider.core :as provider]
         '[ring.adapter.jetty :as jetty])

(def github-config
  {:client-id (System/getenv "GITHUB_CLIENT_ID")
   :client-secret (System/getenv "GITHUB_CLIENT_SECRET")
   :required-org "my-company"
   :validate-org? true})

(def authenticator (github/create-github-authenticator github-config))

(def oidc-provider
  (provider/create-provider
   {:issuer "https://auth.example.com"
    :authorization-endpoint "https://auth.example.com/authorize"
    :token-endpoint "https://auth.example.com/token"
    :jwks-uri "https://auth.example.com/jwks"
    :credential-validator (:credential-validator authenticator)
    :claims-provider (:claims-provider authenticator)}))

;; Use oidc-provider in your Ring handler
```

### Complete Client Example

```clojure
(require '[oidc-github.core :as github]
         '[ring.util.response :as response])

(def github-config
  {:client-id (System/getenv "GITHUB_CLIENT_ID")
   :client-secret (System/getenv "GITHUB_CLIENT_SECRET")
   :redirect-uri "https://myapp.com/oauth/callback"})

(defn login-handler [request]
  (let [state (str (random-uuid))
        auth-url (github/authorization-url github-config state)]
    (-> (response/redirect auth-url)
        (assoc-in [:session :oauth-state] state))))

(defn callback-handler [request]
  (let [code (get-in request [:params :code])
        state (get-in request [:params :state])
        expected-state (get-in request [:session :oauth-state])]
    (when (= state expected-state)
      (let [token (github/exchange-code github-config code)
            user (github/fetch-user (:access_token token))]
        (-> (response/response "Login successful!")
            (assoc-in [:session :user] user))))))
```

## License

See the root repository for license information.

## Related Packages

- [oidc-provider](../oidc-provider) - OIDC Provider implementation
- [oidc](../oidc) - OIDC Client library
