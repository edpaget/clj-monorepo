(ns oidc.id-token
  "Generic ID token validation for any OIDC provider.

   Provides a high-level API for validating ID tokens from any OIDC-compliant
   provider (Google, Apple, Microsoft, etc.). Handles discovery document fetching,
   JWKS retrieval, signature validation, and claims verification.

   Usage:
     ;; JVM (synchronous)
     (let [validator (create-validator)]
       (validate validator {:id-token \"eyJhbG...\"
                            :issuer \"https://accounts.google.com\"
                            :audience \"your-client-id\"}))
     ;; => {:valid? true :claims {:sub \"...\" :email \"...\" ...}}

     ;; ClojureScript (returns Promise)
     (-> (validate validator opts)
         (.then #(println \"Claims:\" (:claims %))))"
  (:require
   [oidc.discovery :as discovery]
   [oidc.discovery.protocol :as discovery-proto]
   #?@(:clj [[oidc.jwt.jvm :as jwt-impl]]
       :cljs [[oidc.jwt.cljs-impl :as jwt-impl]])
   [oidc.jwt.protocol :as jwt-proto]))

#?(:clj (set! *warn-on-reflection* true))

(defn create-validator
  "Creates an ID token validator.

   The validator caches discovery documents and JWKS to minimize network
   requests. Cache TTL is 1 hour by default.

   Returns:
     A validator map containing the discovery client and JWT validator."
  []
  {:discovery-client (discovery/create-client)
   :jwt-validator (jwt-impl/create-validator)})

#?(:clj
   (defn validate
     "Validates an ID token from any OIDC provider (JVM, synchronous).

      Arguments:
        validator - Created by create-validator
        opts - Map with:
          :id-token - The JWT ID token string
          :issuer - Expected issuer URL (e.g., 'https://accounts.google.com')
          :audience - Expected audience (your client ID)
          :nonce (optional) - Expected nonce value for replay protection

      Returns:
        {:valid? true :claims {...}} on success
        {:valid? false :error \"description\"} on failure"
     [{:keys [discovery-client jwt-validator]} {:keys [id-token issuer audience nonce]}]
     (try
       ;; 1. Fetch discovery document (cached by discovery client)
       (let [discovery-doc (discovery-proto/fetch-discovery-document discovery-client issuer)
             jwks-uri (:jwks_uri discovery-doc)]

         (when-not jwks-uri
           (throw (ex-info "No jwks_uri in discovery document" {:issuer issuer})))

         ;; 2. Fetch JWKS (cached by JWT validator)
         (let [jwks (jwt-proto/fetch-jwks jwt-validator jwks-uri)]

           ;; 3. Validate token signature and claims
           (let [claims (jwt-proto/validate-id-token
                         jwt-validator
                         id-token
                         jwks
                         issuer
                         audience
                         {:nonce nonce})]
             {:valid? true :claims claims})))

       (catch Exception e
         {:valid? false
          :error (or (ex-message e) "Token validation failed")
          :error-data (ex-data e)}))))

#?(:cljs
   (defn validate
     "Validates an ID token from any OIDC provider (ClojureScript, async).

      Arguments:
        validator - Created by create-validator
        opts - Map with:
          :id-token - The JWT ID token string
          :issuer - Expected issuer URL (e.g., 'https://accounts.google.com')
          :audience - Expected audience (your client ID)
          :nonce (optional) - Expected nonce value for replay protection

      Returns:
        Promise resolving to:
        {:valid? true :claims {...}} on success
        {:valid? false :error \"description\"} on failure"
     [{:keys [discovery-client jwt-validator]} {:keys [id-token issuer audience nonce]}]
     (-> (discovery-proto/fetch-discovery-document discovery-client issuer)
         (.then (fn [discovery-doc]
                  (let [jwks-uri (:jwks_uri discovery-doc)]
                    (when-not jwks-uri
                      (throw (ex-info "No jwks_uri in discovery document" {:issuer issuer})))
                    ;; In CLJS, fetch-jwks returns a JWKS getter function
                    (let [jwks (jwt-proto/fetch-jwks jwt-validator jwks-uri)]
                      (jwt-proto/validate-id-token
                       jwt-validator
                       id-token
                       jwks
                       issuer
                       audience
                       {:nonce nonce})))))
         (.then (fn [claims]
                  {:valid? true :claims claims}))
         (.catch (fn [error]
                   {:valid? false
                    :error (or (.-message error) (ex-message error) "Token validation failed")
                    :error-data (ex-data error)})))))

(defn extract-subject
  "Extracts the subject (user ID) from validated claims.

   The subject is the unique identifier for the user within the provider's
   system. This value is stable across logins for the same user."
  [claims]
  (:sub claims))

(defn extract-email
  "Extracts the email from validated claims.

   Returns the email only if email_verified is true (or not present,
   as some providers don't include it when email is always verified)."
  [claims]
  (when (not= false (:email_verified claims))
    (:email claims)))

(defn token-expired?
  "Checks if a token's expiration time has passed.

   Takes the claims from a validated token and returns true if the
   token has expired. Returns false if no exp claim is present.
   Useful for checking cached tokens."
  [claims]
  (if-let [exp (:exp claims)]
    (let [now #?(:clj (quot (System/currentTimeMillis) 1000)
                 :cljs (js/Math.floor (/ (.getTime (js/Date.)) 1000)))]
      (> now exp))
    false))
