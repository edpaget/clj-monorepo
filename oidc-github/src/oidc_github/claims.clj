(ns oidc-github.claims
  "GitHub API interaction and OIDC claim mapping.

  Provides functions to fetch user data from GitHub's REST API and transform it
  into standard OIDC claims. Includes caching support to respect GitHub's rate limits."
  (:require
   [clj-http.client :as http]
   [clojure.core.cache.wrapped :as cache]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(def ^:private default-base-url "https://api.github.com")

(defn- github-api-url
  "Constructs a GitHub API URL for the given path.

  Uses enterprise-url if provided, otherwise uses public GitHub API."
  ([path]
   (github-api-url nil path))
  ([enterprise-url path]
   (let [base (or enterprise-url default-base-url)
         base (if (str/ends-with? base "/") (subs base 0 (dec (count base))) base)
         path (if (str/starts-with? path "/") path (str "/" path))]
     (str base path))))

(defn- http-get*
  "Makes an authenticated GET request to GitHub API.

  Returns the full response map including `:body` and `:headers`."
  [url access-token]
  (try
    (http/get url
              {:headers {"Authorization" (str "Bearer " access-token)
                         "Accept" "application/vnd.github+json"
                         "X-GitHub-Api-Version" "2022-11-28"}
               :as :json
               :throw-exceptions true})
    (catch Exception e
      (log/error e "GitHub API request failed" {:url url})
      (throw (ex-info "GitHub API request failed"
                      {:url url
                       :error (.getMessage e)}
                      e)))))

(defn- http-get
  "Makes an authenticated GET request to GitHub API.

  Returns the parsed JSON response body or throws on error."
  [url access-token]
  (:body (http-get* url access-token)))

(defn parse-oauth-scopes
  "Parses the X-OAuth-Scopes header into a set of scope strings.

  GitHub returns granted scopes as a comma-separated string in the
  `X-OAuth-Scopes` response header. Returns nil if header is not present."
  [headers]
  (when-let [scopes-header (get headers "X-OAuth-Scopes")]
    (->> (str/split scopes-header #",")
         (map str/trim)
         (remove str/blank?)
         set)))

(defn has-email-scope?
  "Returns true if the scopes include access to user emails.

  Email access is granted by either `user` (full user scope) or
  `user:email` (email-only scope)."
  [scopes]
  (boolean (and scopes (some scopes ["user" "user:email"]))))

(defn has-org-scope?
  "Returns true if the scopes include access to user organizations.

  Org access is granted by either `read:org` (read-only) or
  `admin:org` (full admin access)."
  [scopes]
  (boolean (and scopes (some scopes ["read:org" "admin:org"]))))

(defn fetch-user-profile
  "Fetches user profile data from GitHub API.

  Returns a map containing user information including login, name, email, avatar URL, etc.
  Optionally accepts an enterprise-url for GitHub Enterprise Server instances."
  ([access-token]
   (fetch-user-profile access-token nil))
  ([access-token enterprise-url]
   (http-get (github-api-url enterprise-url "/user") access-token)))

(defn fetch-user-emails
  "Fetches user email addresses from GitHub API.

  Returns a vector of email maps, each containing `:email`, `:verified`, and `:primary` keys."
  ([access-token]
   (fetch-user-emails access-token nil))
  ([access-token enterprise-url]
   (http-get (github-api-url enterprise-url "/user/emails") access-token)))

(defn fetch-user-orgs
  "Fetches user organization memberships from GitHub API.

  Returns a vector of organization maps, each containing `:login` and other org metadata."
  ([access-token]
   (fetch-user-orgs access-token nil))
  ([access-token enterprise-url]
   (http-get (github-api-url enterprise-url "/user/orgs") access-token)))

(defn fetch-all-user-data
  "Fetches user data from GitHub API based on granted token scopes.

  Returns a map with `:profile` (always fetched), `:emails` (if `user` or `user:email`
  scope), and `:orgs` (if `read:org` or `admin:org` scope) keys. Checks the
  `X-OAuth-Scopes` header from the initial profile request to determine which
  additional endpoints to call."
  ([access-token]
   (fetch-all-user-data access-token nil))
  ([access-token enterprise-url]
   (let [profile-response (http-get* (github-api-url enterprise-url "/user") access-token)
         scopes           (parse-oauth-scopes (:headers profile-response))]
     (cond-> {:profile (:body profile-response)}
       (has-email-scope? scopes)
       (assoc :emails (fetch-user-emails access-token enterprise-url))

       (has-org-scope? scopes)
       (assoc :orgs (fetch-user-orgs access-token enterprise-url))))))

(defn primary-verified-email
  "Extracts the primary verified email from a list of email maps.

  Returns the email string if found, nil otherwise."
  [emails]
  (->> emails
       (filter #(and (:primary %) (:verified %)))
       first
       :email))

(defn github->oidc-claims
  "Transforms GitHub user data into standard OIDC claims.

  Takes a map with `:profile`, `:emails`, and `:orgs` keys (as returned by
  [[fetch-all-user-data]]) and returns a map of OIDC standard claims.

  Standard claims returned:
  - `sub` - GitHub user ID (as string)
  - `preferred_username` - GitHub login
  - `name` - User's full name
  - `email` - Primary verified email address
  - `email_verified` - Always true if email is present
  - `profile` - GitHub profile URL
  - `picture` - Avatar URL

  Custom GitHub claims:
  - `github_login` - GitHub username
  - `github_orgs` - Vector of organization logins
  - `github_company` - Company name from profile"
  [{:keys [profile emails orgs]}]
  (let [primary-email (primary-verified-email emails)]
    (cond-> {:sub (str (:id profile))
             :preferred_username (:login profile)
             :github_login (:login profile)
             :profile (:html_url profile)
             :picture (:avatar_url profile)}

      (:name profile)
      (assoc :name (:name profile))

      primary-email
      (assoc :email primary-email
             :email_verified true)

      (:company profile)
      (assoc :github_company (:company profile))

      (seq orgs)
      (assoc :github_orgs (mapv :login orgs)))))

(defn filter-by-scope
  "Filters OIDC claims based on requested scopes.

  The `profile` scope includes: name, preferred_username, profile, picture
  The `email` scope includes: email, email_verified

  GitHub-specific claims (github_*) are always included regardless of scope."
  [claims scope]
  (let [scope-set      (set scope)
        profile-claims [:name :preferred_username :profile :picture]
        email-claims   [:email :email_verified]
        github-claims  [:github_login :github_orgs :github_company]
        base-claims    [:sub]]
    (select-keys claims
                 (concat base-claims
                         github-claims
                         (when (scope-set "profile") profile-claims)
                         (when (scope-set "email") email-claims)))))

(defn create-cache
  "Creates a cache for GitHub user data with the specified TTL in milliseconds.

  Uses an LRU cache with a maximum of 1000 entries and a TTL for each entry."
  [ttl-ms]
  (cache/ttl-cache-factory {} :ttl ttl-ms))

(defn cached-fetch-user-data
  "Fetches user data from GitHub API with caching.

  Uses the provided cache to store results keyed by access token. The cache
  should be created with [[create-cache]].

  Note: Caching by access token means that if a user's data changes on GitHub,
  the changes won't be reflected until the cache expires. This is generally
  acceptable for the TTL values used (typically 5 minutes)."
  [user-cache access-token enterprise-url]
  (cache/lookup-or-miss user-cache
                        [access-token enterprise-url]
                        (fn [_] (fetch-all-user-data access-token enterprise-url))))

(defn user-in-org?
  "Checks if a user is a member of the specified GitHub organization.

  Takes user data (as returned by [[fetch-all-user-data]]) and an organization
  login string. Returns true if the user is a member of that org, false otherwise."
  [user-data org-login]
  (boolean (some #(= (:login %) org-login) (:orgs user-data))))
