(ns oidc-google.claims
  "Google userinfo API interaction and OIDC claim mapping.

  Provides functions to fetch user data from Google's userinfo endpoint and
  transform it into standard OIDC claims. Includes caching support."
  (:require
   [clj-http.client :as http]
   [clojure.core.cache.wrapped :as cache]
   [clojure.tools.logging :as log]))

(def ^:private userinfo-endpoint "https://openidconnect.googleapis.com/v1/userinfo")

(defn fetch-userinfo
  "Fetches user info from Google's userinfo endpoint.

  Takes an access token and returns a map containing Google user data including
  `sub`, `name`, `email`, `picture`, and other OIDC standard claims."
  [access-token]
  (try
    (let [response (http/get userinfo-endpoint
                             {:headers {"Authorization" (str "Bearer " access-token)}
                              :as :json
                              :throw-exceptions true})]
      (:body response))
    (catch Exception e
      (log/error e "Google userinfo request failed")
      (throw (ex-info "Google userinfo request failed"
                      {:error (.getMessage e)}
                      e)))))

(defn google->oidc-claims
  "Transforms Google userinfo data into standard OIDC claims.

  Google's userinfo endpoint returns data in OIDC-compatible format, so this
  function primarily passes through standard claims and adds any Google-specific
  claims under the `google_` prefix.

  Standard claims returned:
  - `sub` - Google user ID
  - `name` - User's full name
  - `given_name` - First name
  - `family_name` - Last name
  - `email` - Email address
  - `email_verified` - Whether email is verified
  - `picture` - Profile picture URL
  - `locale` - User's locale

  Custom Google claims:
  - `google_hd` - Hosted domain (Google Workspace)"
  [userinfo]
  (cond-> {:sub (:sub userinfo)}
    (:name userinfo)
    (assoc :name (:name userinfo))

    (:given_name userinfo)
    (assoc :given_name (:given_name userinfo))

    (:family_name userinfo)
    (assoc :family_name (:family_name userinfo))

    (:email userinfo)
    (assoc :email (:email userinfo))

    (contains? userinfo :email_verified)
    (assoc :email_verified (:email_verified userinfo))

    (:picture userinfo)
    (assoc :picture (:picture userinfo))

    (:locale userinfo)
    (assoc :locale (:locale userinfo))

    (:hd userinfo)
    (assoc :google_hd (:hd userinfo))))

(defn filter-by-scope
  "Filters OIDC claims based on requested scopes.

  The `profile` scope includes: name, given_name, family_name, picture, locale
  The `email` scope includes: email, email_verified

  Google-specific claims (google_*) are always included regardless of scope."
  [claims scope]
  (let [scope-set      (set scope)
        profile-claims [:name :given_name :family_name :picture :locale]
        email-claims   [:email :email_verified]
        google-claims  [:google_hd]
        base-claims    [:sub]]
    (select-keys claims
                 (concat base-claims
                         google-claims
                         (when (scope-set "profile") profile-claims)
                         (when (scope-set "email") email-claims)))))

(defn create-cache
  "Creates a cache for Google user data with the specified TTL in milliseconds."
  [ttl-ms]
  (cache/ttl-cache-factory {} :ttl ttl-ms))

(defn cached-fetch-userinfo
  "Fetches user info from Google with caching.

  Uses the provided cache to store results keyed by access token."
  [user-cache access-token]
  (cache/lookup-or-miss user-cache
                        access-token
                        (fn [_] (fetch-userinfo access-token))))
