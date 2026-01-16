(ns oidc-apple.validator
  "CredentialValidator and ClaimsProvider implementations for Apple Sign-In.

   Implements [[authn.protocol/CredentialValidator]] to validate Apple ID tokens
   and [[authn.protocol/ClaimsProvider]] to provide user claims."
  (:require
   [authn.protocol :as proto]
   [clojure.tools.logging :as log]
   [oidc.id-token :as id-token]))

(set! *warn-on-reflection* true)

(def apple-issuer
  "Apple's OIDC issuer URL."
  "https://appleid.apple.com")

(defn- validate-apple-id-token
  "Validates an Apple ID token and returns the subject on success.

   Uses the generic oidc.id-token validator with Apple-specific settings."
  [token-validator client-id id-token-str]
  (try
    (let [result (id-token/validate token-validator
                                    {:id-token id-token-str
                                     :issuer apple-issuer
                                     :audience client-id})]
      (when (:valid? result)
        {:user-id (:sub (:claims result))
         :claims (:claims result)}))
    (catch Exception e
      (log/debug e "Apple ID token validation failed")
      nil)))

(defrecord AppleCredentialValidator [token-validator client-id]
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (let [id-token-str (:id-token credentials)]
      (when-not id-token-str
        (log/debug "No :id-token in credentials")
        nil)
      (when id-token-str
        (when-let [{:keys [user-id]} (validate-apple-id-token
                                      token-validator
                                      client-id
                                      id-token-str)]
          user-id)))))

(defrecord AppleClaimsProvider [token-validator client-id]
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    ;; For Apple, we can't fetch additional claims after the fact since there's
    ;; no userinfo endpoint. The user-id here is the Apple subject.
    ;; In practice, claims should be extracted from the ID token at login time
    ;; and stored in your database.
    ;;
    ;; This implementation returns minimal claims - the actual user data
    ;; should come from your application's database where you stored it
    ;; during the initial sign-in.
    (let [base-claims {:sub user-id}
          scope-set   (set scope)]
      (cond-> base-claims
        ;; We can't provide email without the original token
        ;; Your app should store this during sign-in
        (scope-set "email")
        (merge {})  ;; Placeholder - real implementation would query DB

        true
        (assoc :apple_provider true)))))

(defn create-credential-validator
  "Creates an AppleCredentialValidator for use with oidc-provider.

   Arguments:
   - client-id: Your Apple App ID or Services ID

   Returns:
     CredentialValidator implementation for Apple ID tokens"
  [client-id]
  (->AppleCredentialValidator (id-token/create-validator) client-id))

(defn create-claims-provider
  "Creates an AppleClaimsProvider for use with oidc-provider.

   Note: Apple doesn't have a userinfo endpoint, so this provider can only
   return minimal claims. Your application should store user info from the
   initial sign-in and provide it from your database.

   Arguments:
   - client-id: Your Apple App ID or Services ID

   Returns:
     ClaimsProvider implementation for Apple"
  [client-id]
  (->AppleClaimsProvider (id-token/create-validator) client-id))
