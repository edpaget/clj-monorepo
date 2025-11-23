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

   Takes an OIDC issuer URL and appends `/.well-known/openid-configuration` to it,
   removing any trailing slash from the issuer if present. Returns the full URL to
   the discovery document."
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

   Takes an OIDC issuer URL, creates a platform-specific discovery client, and
   fetches the discovery document from the well-known endpoint. In Clojure, returns
   the parsed discovery document as a map. In ClojureScript, returns a promise that
   resolves to the document. Throws ExceptionInfo on HTTP errors or when the document
   is invalid."
  [issuer]
  (proto/fetch-discovery-document (create-client) issuer))
