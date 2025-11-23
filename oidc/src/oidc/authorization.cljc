(ns oidc.authorization
  "OIDC Authorization Code Flow implementation."
  (:require
   #?(:clj [cheshire.core :as json]
      :cljs [cljs.core.async :refer [<!]])
   #?(:clj [clj-http.client :as http]
      :cljs [cljs-http.client :as http])
   [clojure.string :as str]
   [malli.core :as m])
  #?(:clj (:import
           [java.net URLEncoder]
           [java.security SecureRandom]
           [java.util Base64]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

#?(:clj (set! *warn-on-reflection* true))

(def TokenResponse
  "Malli schema for OAuth2 token response."
  [:map
   [:access_token :string]
   [:token_type :string]
   [:id_token {:optional true} :string]
   [:refresh_token {:optional true} :string]
   [:expires_in {:optional true} pos-int?]
   [:scope {:optional true} :string]])

(defn- url-encode
  [s]
  #?(:clj (URLEncoder/encode ^String s "UTF-8")
     :cljs (js/encodeURIComponent s)))

(defn- generate-random-string
  [length]
  #?(:clj
     (let [random (SecureRandom.)
           bytes  (byte-array length)]
       (.nextBytes random bytes)
       (.encodeToString (Base64/getUrlEncoder) bytes))
     :cljs
     (let [array (js/Uint8Array. length)]
       (.getRandomValues js/crypto array)
       (-> array
           (.reduce (fn [acc byte]
                      (str acc (.toString byte 16)))
                    "")
           (.substring 0 (* length 2))))))

(defn generate-state
  "Generates a random state parameter for CSRF protection.

   Returns a cryptographically random string suitable for use as an OAuth2 state
   parameter to prevent cross-site request forgery attacks."
  []
  (generate-random-string 32))

(defn generate-nonce
  "Generates a random nonce parameter for replay protection.

   Returns a cryptographically random string suitable for use as an OIDC nonce
   parameter to prevent token replay attacks."
  []
  (generate-random-string 32))

(defn authorization-url
  "Constructs the authorization URL for initiating the OIDC flow.

   Takes an authorization endpoint URL (from discovery), a client ID, a redirect URI,
   and an options map. The options support `:scope` (defaults to \"openid\"), `:state`
   (for CSRF protection), `:nonce` (for replay protection), `:response-type` (defaults
   to \"code\"), `:response-mode` (e.g., \"query\" or \"fragment\"), `:prompt` (e.g.,
   \"none\", \"login\", \"consent\"), `:max-age` (maximum authentication age in seconds),
   `:ui-locales` (preferred locales for UI), and `:additional-params` (map of additional
   query parameters).

   Constructs the full authorization URL with all parameters properly URL-encoded.
   Returns the authorization URL string."
  [authorization-endpoint client-id redirect-uri {:keys [scope state nonce response-type
                                                         response-mode prompt max-age
                                                         ui-locales additional-params]
                                                  :or {scope "openid"
                                                       response-type "code"}}]
  (let [params       (cond-> {"response_type" response-type
                              "client_id" client-id
                              "redirect_uri" redirect-uri
                              "scope" scope}
                       state (assoc "state" state)
                       nonce (assoc "nonce" nonce)
                       response-mode (assoc "response_mode" response-mode)
                       prompt (assoc "prompt" prompt)
                       max-age (assoc "max_age" (str max-age))
                       ui-locales (assoc "ui_locales" ui-locales)
                       additional-params (merge additional-params))
        query-string (->> params
                          (map (fn [[k v]] (str (url-encode k) "=" (url-encode v))))
                          (str/join "&"))]
    (str authorization-endpoint "?" query-string)))

(defn exchange-code
  "Exchanges an authorization code for tokens.

   Takes a token endpoint URL (from discovery), the authorization code received from
   the callback, a client ID, an optional client secret (for confidential clients),
   the same redirect URI used in the authorization request, and an options map that
   can include `:code-verifier` for PKCE flows.

   Makes an HTTP POST request to the token endpoint with grant_type=authorization_code.
   Validates the response against the TokenResponse schema. In Clojure, returns the
   token response map containing access_token, id_token, etc. In ClojureScript, returns
   a promise. Throws ExceptionInfo on HTTP or validation errors."
  [token-endpoint code client-id client-secret redirect-uri {:keys [code-verifier]}]
  (let [params (cond-> {"grant_type" "authorization_code"
                        "code" code
                        "client_id" client-id
                        "redirect_uri" redirect-uri}
                 client-secret (assoc "client_secret" client-secret)
                 code-verifier (assoc "code_verifier" code-verifier))]
    #?(:clj
       (let [response (http/post token-endpoint
                                 {:form-params params
                                  :content-type :x-www-form-urlencoded
                                  :accept :json
                                  :throw-exceptions true})
             body     (json/parse-string (:body response) true)]
         (when-not (m/validate TokenResponse body)
           (throw (ex-info "Invalid token response"
                           {:errors (m/explain TokenResponse body)})))
         body)
       :cljs
       (go
         (let [response (<! (http/post token-endpoint
                                       {:form-params params
                                        :with-credentials? false}))
               body     (:body response)]
           (when-not (m/validate TokenResponse body)
             (throw (ex-info "Invalid token response"
                             {:errors (m/explain TokenResponse body)})))
           body)))))

(defn refresh-token
  "Refreshes an access token using a refresh token.

   Takes a token endpoint URL (from discovery), the refresh token, a client ID, an
   optional client secret (for confidential clients), and an options map that can
   include `:scope` to request specific scopes.

   Makes an HTTP POST request to the token endpoint with grant_type=refresh_token.
   Validates the response against the TokenResponse schema. In Clojure, returns the
   token response map with the new access_token. In ClojureScript, returns a promise.
   Throws ExceptionInfo on HTTP or validation errors."
  [token-endpoint refresh-token-val client-id client-secret {:keys [scope]}]
  (let [params (cond-> {"grant_type" "refresh_token"
                        "refresh_token" refresh-token-val
                        "client_id" client-id}
                 client-secret (assoc "client_secret" client-secret)
                 scope (assoc "scope" scope))]
    #?(:clj
       (let [response (http/post token-endpoint
                                 {:form-params params
                                  :content-type :x-www-form-urlencoded
                                  :accept :json
                                  :throw-exceptions true})
             body     (json/parse-string (:body response) true)]
         (when-not (m/validate TokenResponse body)
           (throw (ex-info "Invalid token response"
                           {:errors (m/explain TokenResponse body)})))
         body)
       :cljs
       (go
         (let [response (<! (http/post token-endpoint
                                       {:form-params params
                                        :with-credentials? false}))
               body     (:body response)]
           (when-not (m/validate TokenResponse body)
             (throw (ex-info "Invalid token response"
                             {:errors (m/explain TokenResponse body)})))
           body)))))
