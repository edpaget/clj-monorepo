(ns bashketball-editor-ui.config
  "Application configuration.

  Provides environment-specific settings for API endpoints and other
  configuration values.")

(def api-base-url
  "Base URL for the bashketball-editor-api.

  When UI and API are served from the same origin (production), this should be
  empty to use relative URLs. For local development, set API_BASE_URL or use
  the default localhost:3000."
  (if (exists? js/goog.global.API_BASE_URL)
    js/goog.global.API_BASE_URL
    "http://localhost:3000"))

(def api-url
  "Base URL for the bashketball-editor-api GraphQL endpoint."
  (str api-base-url "/graphql"))

(def github-login-url
  "URL for GitHub OAuth login."
  (str api-base-url "/auth/github/login"))

(def app-name
  "Application display name."
  "Bashketball Card Editor")
