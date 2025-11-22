(ns oidc.discovery.jvm
  "JVM implementation of OIDC discovery with caching."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.core.cache.wrapped :as cache]
   [clojure.string :as str]
   [malli.core :as m]
   [oidc.discovery.protocol :as proto]))

(set! *warn-on-reflection* true)

(def ^:private discovery-cache
  (cache/ttl-cache-factory {} :ttl (* 60 60 1000)))

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

(defn- fetch-discovery-document-uncached
  [issuer]
  (let [url      (well-known-url issuer)
        response (http/get url {:accept :json})
        body     (json/parse-string (:body response) true)]
    (when-not (m/validate DiscoveryDocument body)
      (throw (ex-info "Invalid discovery document"
                      {:issuer issuer
                       :errors (m/explain DiscoveryDocument body)})))
    body))

(defrecord JVMDiscoveryClient []
  proto/IDiscoveryClient
  (fetch-discovery-document [_ issuer]
    (cache/lookup-or-miss discovery-cache issuer fetch-discovery-document-uncached)))

(defn create-client
  "Creates a JVM-based discovery client with caching.

  Returns:
    IDiscoveryClient implementation with 1-hour TTL cache"
  []
  (->JVMDiscoveryClient))
