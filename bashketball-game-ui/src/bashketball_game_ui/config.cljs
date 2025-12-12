(ns bashketball-game-ui.config
  "Application configuration.

  Provides environment-specific settings for API endpoints and other
  configuration values.")

(def api-base-url
  "Base URL for the bashketball-game-api.

  When UI and API are served from the same origin (production), this should be
  empty to use relative URLs. For local development, set API_BASE_URL or use
  the default localhost:4000."
  (if (exists? js/window.API_BASE_URL)
    js/window.API_BASE_URL
    "http://localhost:3002"))

(def api-url
  "Base URL for the bashketball-game-api GraphQL endpoint."
  (str api-base-url "/graphql"))

(def google-login-url
  "URL for Google OAuth login."
  (str api-base-url "/auth/google/login"))

(def app-name
  "Application display name."
  "Bashketball")

(defn resolve-api-url
  "Resolves a URL against the API base URL.

  If the URL is nil or already absolute (starts with http:// or https://),
  returns it unchanged. Otherwise, prepends the api-base-url."
  [url]
  (cond
    (nil? url) nil
    (or (clojure.string/starts-with? url "http://")
        (clojure.string/starts-with? url "https://")) url
    :else (str api-base-url url)))
