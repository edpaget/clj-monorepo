(ns oidc.discovery.cljs-impl
  "ClojureScript implementation of OIDC discovery without caching."
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [clojure.string :as str]
   [malli.core :as m]
   [oidc.discovery.protocol :as proto])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))

(def DiscoveryDocument
  "Malli schema for OIDC discovery document."
  [:map
   [:issuer :string]
   [:authorization_endpoint :string]
   [:token_endpoint :string]
   [:jwks_uri :string]
   [:userinfo_endpoint {:optional true} :string]
   [:end_session_endpoint {:optional true} :string]
   [:introspection_endpoint {:optional true} :string]
   [:revocation_endpoint {:optional true} :string]
   [:response_types_supported [:vector :string]]
   [:subject_types_supported [:vector :string]]
   [:id_token_signing_alg_values_supported [:vector :string]]])

(defn- well-known-url
  [issuer]
  (str (if (str/ends-with? issuer "/")
         (subs issuer 0 (dec (count issuer)))
         issuer)
       "/.well-known/openid-configuration"))

(defrecord JSDiscoveryClient []
  proto/IDiscoveryClient
  (fetch-discovery-document [_ issuer]
    (let [url (well-known-url issuer)]
      (go
        (let [response (<! (http/get url {:with-credentials? false}))
              body     (:body response)]
          (when-not (m/validate DiscoveryDocument body)
            (throw (ex-info "Invalid discovery document"
                            {:issuer issuer
                             :errors (m/explain DiscoveryDocument body)})))
          body)))))

(defn create-client
  "Creates a ClojureScript-based discovery client without caching.

  Returns:
    IDiscoveryClient implementation relying on browser HTTP cache"
  []
  (->JSDiscoveryClient))
