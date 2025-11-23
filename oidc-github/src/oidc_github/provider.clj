(ns oidc-github.provider
  "Provider-side GitHub authentication for oidc-provider.

  Implements [[authn.protocol/CredentialValidator]] and
  [[authn.protocol/ClaimsProvider]] protocols to enable using GitHub
  as an authenticator in your OIDC provider."
  (:require
   [authn.protocol :as proto]
   [clj-http.client :as http]
   [oidc-github.claims :as claims]))

(defn- exchange-code-for-token
  "Exchanges a GitHub OAuth authorization code for an access token.

  Makes a POST request to GitHub's token endpoint with the authorization code.
  Returns the access token string on success, nil on failure."
  [client-id client-secret code enterprise-url]
  (try
    (let [token-url (if enterprise-url
                      (str enterprise-url "/login/oauth/access_token")
                      "https://github.com/login/oauth/access_token")
          response (http/post token-url
                              {:form-params {:client_id client-id
                                            :client_secret client-secret
                                            :code code}
                               :headers {"Accept" "application/json"}
                               :as :json
                               :throw-exceptions false})]
      (when (= 200 (:status response))
        (get-in response [:body :access_token])))
    (catch Exception _e
      nil)))

(defn- validate-token
  "Validates a GitHub access token by making an API request.

  Returns the user's GitHub login if valid, nil otherwise."
  [access-token enterprise-url]
  (try
    (let [user-profile (claims/fetch-user-profile access-token enterprise-url)]
      (:login user-profile))
    (catch Exception _e
      nil)))

(defrecord GitHubCredentialValidator [client-id
                                       client-secret
                                       required-org
                                       validate-org?
                                       enterprise-url
                                       cache-ttl-ms]
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (let [user-cache (claims/create-cache cache-ttl-ms)]
      (cond
        (:access-token credentials)
        (when-let [login (validate-token (:access-token credentials) enterprise-url)]
          (if validate-org?
            (let [user-data (claims/cached-fetch-user-data user-cache
                                                          (:access-token credentials)
                                                          enterprise-url)]
              (when (or (nil? required-org)
                       (claims/user-in-org? user-data required-org))
                login))
            login))

        (:code credentials)
        (when-let [access-token (exchange-code-for-token client-id
                                                        client-secret
                                                        (:code credentials)
                                                        enterprise-url)]
          (when-let [login (validate-token access-token enterprise-url)]
            (if validate-org?
              (let [user-data (claims/cached-fetch-user-data user-cache
                                                            access-token
                                                            enterprise-url)]
                (when (or (nil? required-org)
                         (claims/user-in-org? user-data required-org))
                  login))
              login)))

        :else
        nil))))

(defrecord GitHubClaimsProvider [enterprise-url cache-ttl-ms]
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    (let [user-cache (claims/create-cache cache-ttl-ms)
          access-token user-id
          user-data (claims/cached-fetch-user-data user-cache access-token enterprise-url)
          all-claims (claims/github->oidc-claims user-data)]
      (claims/filter-by-scope all-claims scope))))
