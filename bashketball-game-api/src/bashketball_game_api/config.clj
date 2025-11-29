(ns bashketball-game-api.config
  "Application configuration loading and validation.

  Loads configuration from `resources/config.edn` using Aero and validates
  it against a Malli schema. Supports environment-specific profiles and
  environment variable substitution."
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [malli.core :as m]))

(def Config
  "Malli schema for application configuration."
  [:map
   [:server [:map
             [:port pos-int?]
             [:host :string]]]
   [:database [:map
               [:database-url :string]
               [:c3p0-opts {:optional true} :map]]]
   [:google [:map
             [:client-id :string]
             [:client-secret :string]
             [:redirect-uri :string]
             [:success-redirect-uri :string]
             [:scopes [:vector :string]]]]
   [:session [:map
              [:ttl-ms pos-int?]
              [:cookie-name :string]
              [:cookie-secret :string]
              [:cookie-secure? :boolean]
              [:cookie-http-only? :boolean]
              [:cookie-same-site [:enum :strict :lax :none]]]]
   [:cors [:map
           [:allowed-origins [:or [:vector :string] :string]]]]])

(defn load-config
  "Loads and validates application configuration.

  Reads configuration from `resources/config.edn` using the specified profile
  (defaults to `:dev`). Validates the configuration against the Config schema
  and throws an exception if validation fails."
  ([]
   (load-config :dev))
  ([profile]
   (let [config (aero/read-config (io/resource "config.edn") {:profile profile})]
     (if (m/validate Config config)
       config
       (throw (ex-info "Invalid configuration"
                       {:errors (m/explain Config config)}))))))
