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
