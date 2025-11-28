(ns oidc-google.provider
  "Provider-side Google authentication for oidc-provider.

  Implements [[authn.protocol/CredentialValidator]] and
  [[authn.protocol/ClaimsProvider]] protocols to enable using Google
  as an authenticator in your OIDC provider."
  (:require
   [authn.protocol :as proto]
   [clojure.tools.logging :as log]
   [oidc-google.claims :as claims]
   [oidc-google.client :as client]))

(defn- validate-with-id-token
  "Validates credentials using an ID token.

  Validates the token signature and claims, returns the subject on success."
  [id-token client-id]
  (try
    (let [token-claims (client/validate-id-token id-token client-id)]
      (:sub token-claims))
    (catch Exception e
      (log/debug e "ID token validation failed")
      nil)))

(defn- validate-with-access-token
  "Validates credentials using an access token.

  Fetches userinfo to confirm token validity, returns the subject on success."
  [access-token user-cache]
  (try
    (let [userinfo (claims/cached-fetch-userinfo user-cache access-token)]
      (:sub userinfo))
    (catch Exception e
      (log/debug e "Access token validation failed")
      nil)))

(defn- validate-with-code
  "Validates credentials using an authorization code.

  Exchanges the code for tokens, validates the ID token if present,
  returns the subject on success."
  [code config user-cache]
  (try
    (let [tokens (client/exchange-code config code)]
      (if-let [id-token (:id_token tokens)]
        (validate-with-id-token id-token (:client-id config))
        (validate-with-access-token (:access_token tokens) user-cache)))
    (catch Exception e
      (log/debug e "Code exchange failed")
      nil)))

(defrecord GoogleCredentialValidator [config cache-ttl-ms]
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (let [user-cache (claims/create-cache cache-ttl-ms)]
      (cond
        (:id-token credentials)
        (validate-with-id-token (:id-token credentials) (:client-id config))

        (:access-token credentials)
        (validate-with-access-token (:access-token credentials) user-cache)

        (:code credentials)
        (validate-with-code (:code credentials) config user-cache)

        :else
        nil))))

(defrecord GoogleClaimsProvider [cache-ttl-ms]
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    (let [user-cache   (claims/create-cache cache-ttl-ms)
          access-token user-id
          userinfo     (claims/cached-fetch-userinfo user-cache access-token)
          all-claims   (claims/google->oidc-claims userinfo)]
      (claims/filter-by-scope all-claims scope))))

(defn create-credential-validator
  "Creates a GoogleCredentialValidator for use with oidc-provider."
  [config cache-ttl-ms]
  (->GoogleCredentialValidator config cache-ttl-ms))

(defn create-claims-provider
  "Creates a GoogleClaimsProvider for use with oidc-provider."
  [cache-ttl-ms]
  (->GoogleClaimsProvider cache-ttl-ms))
