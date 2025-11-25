(ns basketball-editor-ui.config
  "Application configuration.

  Provides environment-specific settings for API endpoints and other
  configuration values.")

(def api-url
  "Base URL for the bashketball-editor-api GraphQL endpoint."
  (or js/goog.global.API_URL "http://localhost:8080/graphql"))

(def app-name
  "Application display name."
  "Bashketball Card Editor")
