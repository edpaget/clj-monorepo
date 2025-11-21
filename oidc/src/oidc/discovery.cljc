(ns oidc.discovery
  "OIDC Discovery document fetching and parsing."
  (:require
   #?(:clj [cheshire.core :as json]
      :cljs [cljs.core.async :refer [<!]])
   #?(:clj [clj-http.client :as http]
      :cljs [cljs-http.client :as http])
   [clojure.string :as str]
   [malli.core :as m])
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

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

(defn fetch-discovery-document
  "Fetches and parses the OIDC discovery document from the issuer.

  Args:
    issuer: The OIDC issuer URL

  Returns:
    Parsed discovery document as a map (ClojureScript returns a promise)

  Throws:
    clojure.lang.ExceptionInfo on HTTP errors or invalid document"
  [issuer]
  (let [url (well-known-url issuer)]
    #?(:clj
       (let [response (http/get url {:accept :json})
             body     (json/parse-string (:body response) true)]
         (when-not (m/validate DiscoveryDocument body)
           (throw (ex-info "Invalid discovery document"
                           {:issuer issuer
                            :errors (m/explain DiscoveryDocument body)})))
         body)
       :cljs
       (go
         (let [response (<! (http/get url {:with-credentials? false}))
               body     (:body response)]
           (when-not (m/validate DiscoveryDocument body)
             (throw (ex-info "Invalid discovery document"
                             {:issuer issuer
                              :errors (m/explain DiscoveryDocument body)})))
           body)))))
