(ns oidc.discovery
  "OIDC Discovery document fetching and parsing."
  (:require
   #?@(:clj [[oidc.discovery.jvm :as jvm]]
       :cljs [[oidc.discovery.cljs-impl :as cljs-impl]])
   [clojure.string :as str]
   [oidc.discovery.protocol :as proto]))

#?(:clj (set! *warn-on-reflection* true))

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

(defn well-known-url
  "Constructs the .well-known/openid-configuration URL from an issuer.

  Args:
    issuer: The OIDC issuer URL

  Returns:
    Full URL to the discovery document"
  [issuer]
  (str (if (str/ends-with? issuer "/")
         (subs issuer 0 (dec (count issuer)))
         issuer)
       "/.well-known/openid-configuration"))

(defn create-client
  "Creates a platform-specific discovery client.

  Returns:
    IDiscoveryClient implementation (JVM with caching, ClojureScript without)"
  []
  #?(:clj (jvm/create-client)
     :cljs (cljs-impl/create-client)))

(defn fetch-discovery-document
  "Fetches and parses the OIDC discovery document from the issuer.

  Args:
    issuer: The OIDC issuer URL

  Returns:
    Parsed discovery document as a map (ClojureScript returns a promise)

  Throws:
    clojure.lang.ExceptionInfo on HTTP errors or invalid document"
  [issuer]
  (proto/fetch-discovery-document (create-client) issuer))
