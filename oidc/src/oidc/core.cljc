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

   Takes a configuration map containing `:issuer` (the OIDC issuer URL like
   `https://accounts.google.com`), `:client-id` (OAuth2 client ID), `:redirect-uri`
   (the redirect URI registered with the provider), and optionally `:client-secret`
   (for confidential clients) and `:scopes` (vector of OAuth2 scopes).

   When scopes are not provided, defaults to `[\"openid\"]`. Validates the
   configuration against the Config schema and returns the validated client
   configuration map."
  [{:keys [scopes] :as config}]
  {:pre [(m/validate Config config)]}
  (assoc config :scopes (or scopes ["openid"])))
