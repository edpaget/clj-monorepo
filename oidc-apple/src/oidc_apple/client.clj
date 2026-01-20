(ns oidc-apple.client
  "Apple Sign-In web client flow implementation.

   Provides functions for the Apple web OAuth flow:
   - Client secret generation (JWT signed with ES256)
   - Authorization URL construction
   - Authorization code exchange

   ## Apple Web Flow Requirements

   Unlike most OAuth providers, Apple requires:
   1. A JWT-based client secret signed with your private key
   2. POST callback with `form_post` response mode
   3. User data (email, name) only on first authorization

   ## Configuration

   Required config keys:
   - `:client-id` - Your Apple Services ID (for web)
   - `:team-id` - Your Apple Developer Team ID
   - `:key-id` - The Key ID from your Sign in with Apple key
   - `:private-key` - The private key contents from the .p8 file
   - `:redirect-uri` - Your callback URL

   ## Example

       (require '[oidc-apple.client :as client])

       (def config {:client-id   \"com.example.service\"
                    :team-id     \"ABCD1234\"
                    :key-id      \"KEY123\"
                    :private-key \"-----BEGIN PRIVATE KEY-----...\"
                    :redirect-uri \"https://example.com/auth/apple/callback\"})

       ;; Generate authorization URL
       (client/authorization-url config \"random-state-string\")

       ;; Exchange code for tokens
       (client/exchange-code config \"auth-code-from-callback\")"
  (:require
   [buddy.core.keys :as keys]
   [buddy.sign.jwt :as jwt]
   [oidc.authorization :as auth]))

(set! *warn-on-reflection* true)

(def apple-issuer
  "Apple's OIDC issuer URL."
  "https://appleid.apple.com")

(def apple-auth-endpoint
  "Apple's authorization endpoint."
  "https://appleid.apple.com/auth/authorize")

(def apple-token-endpoint
  "Apple's token endpoint."
  "https://appleid.apple.com/auth/token")

(defn generate-client-secret
  "Generates a JWT client secret for Apple Sign-In web flow.

   Apple requires a JWT signed with ES256 containing:
   - iss: team-id
   - iat: current timestamp
   - exp: expiration (max 6 months)
   - aud: https://appleid.apple.com
   - sub: client-id (Services ID for web)

   Arguments:
   - config: Map with :team-id, :key-id, :client-id, :private-key
   - opts: Optional map with :expires-in-seconds (default 86400 = 24 hours)

   Returns a JWT string valid for the specified duration."
  [{:keys [team-id key-id client-id private-key]} & [{:keys [expires-in-seconds]
                                                       :or   {expires-in-seconds 86400}}]]
  (let [now    (quot (System/currentTimeMillis) 1000)
        claims {:iss team-id
                :iat now
                :exp (+ now expires-in-seconds)
                :aud apple-issuer
                :sub client-id}
        ec-key (keys/str->private-key private-key)]
    (jwt/sign claims ec-key {:alg :es256 :header {:kid key-id}})))

(defn authorization-url
  "Generates Apple Sign-In authorization URL.

   Arguments:
   - config: Map with :client-id and :redirect-uri
   - state: Random state string for CSRF protection
   - opts: Optional map with:
     - :response-mode (default \"form_post\" - Apple recommends this)
     - :scope (default \"openid email name\")

   Returns the authorization URL string."
  [{:keys [client-id redirect-uri]} state & [{:keys [response-mode scope]
                                               :or   {response-mode "form_post"
                                                      scope         "openid email name"}}]]
  (auth/authorization-url
   apple-auth-endpoint
   client-id
   redirect-uri
   {:scope         scope
    :state         state
    :response-mode response-mode}))

(defn exchange-code
  "Exchanges authorization code for tokens.

   Arguments:
   - config: Map with :client-id, :team-id, :key-id, :private-key, :redirect-uri
   - code: Authorization code from Apple callback

   Returns token response map with:
   - :access_token
   - :id_token (JWT with user claims)
   - :token_type
   - :expires_in
   - :refresh_token (optional)"
  [{:keys [client-id redirect-uri] :as config} code]
  (let [client-secret (generate-client-secret config)]
    (auth/exchange-code
     apple-token-endpoint
     code
     client-id
     client-secret
     redirect-uri
     {})))
