(ns oidc-provider.discovery
  "OpenID Connect Discovery and metadata endpoints."
  (:require
   [malli.core :as m]
   [oidc-provider.token :as token]))

(set! *warn-on-reflection* true)

(def DiscoveryConfig
  "Malli schema for discovery configuration."
  [:map
   [:issuer :string]
   [:authorization-endpoint :string]
   [:token-endpoint :string]
   [:jwks-uri :string]
   [:userinfo-endpoint {:optional true} :string]
   [:scopes-supported {:optional true} [:vector :string]]
   [:response-types-supported {:optional true} [:vector :string]]
   [:grant-types-supported {:optional true} [:vector :string]]
   [:subject-types-supported {:optional true} [:vector :string]]
   [:id-token-signing-alg-values-supported {:optional true} [:vector :string]]
   [:token-endpoint-auth-methods-supported {:optional true} [:vector :string]]
   [:claims-supported {:optional true} [:vector :string]]])

(defn openid-configuration
  "Generates OpenID Connect Discovery metadata.

  Args:
    config: Discovery configuration map matching DiscoveryConfig schema

  Returns:
    Map containing OpenID Connect Discovery metadata per RFC 8414"
  [{:keys [issuer
           authorization-endpoint
           token-endpoint
           jwks-uri
           userinfo-endpoint
           scopes-supported
           response-types-supported
           grant-types-supported
           subject-types-supported
           id-token-signing-alg-values-supported
           token-endpoint-auth-methods-supported
           claims-supported] :as config}]
  {:pre [(m/validate DiscoveryConfig config)]}
  (cond-> {:issuer issuer
           :authorization_endpoint authorization-endpoint
           :token_endpoint token-endpoint
           :jwks_uri jwks-uri
           :response_types_supported (or response-types-supported ["code"])
           :subject_types_supported (or subject-types-supported ["public"])
           :id_token_signing_alg_values_supported (or id-token-signing-alg-values-supported ["RS256"])
           :scopes_supported (or scopes-supported ["openid" "profile" "email"])
           :grant_types_supported (or grant-types-supported ["authorization_code" "refresh_token"])
           :token_endpoint_auth_methods_supported (or token-endpoint-auth-methods-supported
                                                      ["client_secret_basic" "client_secret_post" "none"])}
    userinfo-endpoint (assoc :userinfo_endpoint userinfo-endpoint)
    claims-supported (assoc :claims_supported claims-supported)))

(defn jwks-endpoint
  "Generates JWKS endpoint response.

  Args:
    provider-config: Provider configuration map with :signing-key

  Returns:
    Map containing JSON Web Key Set"
  [provider-config]
  (token/jwks provider-config))
