(ns oidc.core
  "Core OIDC client functionality for authentication flows and token management."
  (:require
   [malli.core :as m]))

#?(:clj (set! *warn-on-reflection* true))

(def Config
  "Malli schema for OIDC client configuration."
  [:map
   [:issuer :string]
   [:client-id :string]
   [:client-secret {:optional true} :string]
   [:redirect-uri :string]
   [:scopes {:optional true} [:vector :string]]])

(defn create-client
  "Creates an OIDC client configuration.

  Args:
    config: Map containing OIDC configuration
      - :issuer - The OIDC issuer URL (e.g., https://accounts.google.com)
      - :client-id - OAuth2 client ID
      - :client-secret - OAuth2 client secret (optional for public clients)
      - :redirect-uri - Redirect URI registered with the provider
      - :scopes - Vector of OAuth2 scopes (defaults to [\"openid\"])

  Returns:
    Validated client configuration map"
  [{:keys [scopes] :as config}]
  {:pre [(m/validate Config config)]}
  (assoc config :scopes (or scopes ["openid"])))
