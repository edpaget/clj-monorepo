(ns oidc-provider.authorization
  "Authorization endpoint implementation for OAuth2/OIDC."
  (:require
   [clojure.string :as str]
   [malli.core :as m]
   [oidc-provider.protocol :as proto]
   [oidc-provider.token :as token])
  (:import
   [java.net URLDecoder]))

(set! *warn-on-reflection* true)

(def AuthorizationRequest
  "Malli schema for authorization request parameters."
  [:map
   [:response_type :string]
   [:client_id :string]
   [:redirect_uri :string]
   [:scope {:optional true} :string]
   [:state {:optional true} :string]
   [:nonce {:optional true} :string]
   [:prompt {:optional true} :string]
   [:max_age {:optional true} [:or :string pos-int?]]
   [:ui_locales {:optional true} :string]])

(def AuthorizationResponse
  "Malli schema for authorization response."
  [:map
   [:redirect-uri :string]
   [:params :map]])

(defn- parse-query-string
  [query-string]
  (when query-string
    (into {}
          (map (fn [param]
                 (let [[k v]       (str/split param #"=" 2)
                       ^String val (if v v "")]
                   [(keyword k) (URLDecoder/decode val "UTF-8")])))
          (str/split query-string #"&"))))

(defn- validate-redirect-uri
  [client redirect-uri]
  (when-not (some #{redirect-uri} (:redirect-uris client))
    (throw (ex-info "Invalid redirect_uri"
                    {:redirect-uri redirect-uri
                     :allowed (:redirect-uris client)}))))

(defn- validate-response-type
  [client response-type]
  (when-not (some #{response-type} (:response-types client))
    (throw (ex-info "Unsupported response_type"
                    {:response-type response-type
                     :supported (:response-types client)}))))

(defn- validate-scope
  [client scope-str]
  (let [requested-scopes (when scope-str (str/split scope-str #" "))
        client-scopes    (:scopes client)]
    (when (some (fn [scope] (not (some #{scope} client-scopes))) requested-scopes)
      (throw (ex-info "Invalid scope"
                      {:requested requested-scopes
                       :allowed client-scopes})))))

(defn parse-authorization-request
  "Parses and validates an authorization request.

  Args:
    query-string: URL query string from authorization request
    client-store: ClientStore implementation

  Returns:
    Validated request map

  Throws:
    ex-info on validation errors"
  [query-string client-store]
  (let [params (parse-query-string query-string)]
    (when-not (m/validate AuthorizationRequest params)
      (throw (ex-info "Invalid authorization request"
                      {:errors (m/explain AuthorizationRequest params)})))
    (let [client-id (:client_id params)
          client    (proto/get-client client-store client-id)]
      (when-not client
        (throw (ex-info "Unknown client" {:client-id client-id})))
      (validate-redirect-uri client (:redirect_uri params))
      (validate-response-type client (:response_type params))
      (when (:scope params)
        (validate-scope client (:scope params)))
      params)))

(defn handle-authorization-approval
  "Handles user approval of authorization request.

  Args:
    request: Parsed authorization request from parse-authorization-request
    user-id: User identifier who approved the request
    provider-config: Provider configuration map
    code-store: AuthorizationCodeStore implementation

  Returns:
    Authorization response map with :redirect-uri and :params"
  [{:keys [response_type client_id redirect_uri scope state nonce]}
   user-id
   provider-config
   code-store]
  (cond
    (= response_type "code")
    (let [code   (token/generate-authorization-code)
          expiry (+ (System/currentTimeMillis)
                    (* 1000 (or (:authorization-code-ttl-seconds provider-config) 600)))
          scopes (when scope (vec (str/split scope #" ")))]
      (proto/save-authorization-code code-store code user-id client_id
                                     redirect_uri scopes nonce expiry)
      {:redirect-uri redirect_uri
       :params (cond-> {:code code}
                 state (assoc :state state))})

    :else
    (throw (ex-info "Unsupported response_type"
                    {:response-type response_type}))))

(defn handle-authorization-denial
  "Handles user denial of authorization request.

  Args:
    request: Parsed authorization request
    error-code: OAuth2 error code (default \"access_denied\")
    error-description: Human-readable error description

  Returns:
    Authorization response map with error parameters"
  [{:keys [redirect_uri state]} error-code error-description]
  {:redirect-uri redirect_uri
   :params (cond-> {:error (or error-code "access_denied")}
             error-description (assoc :error_description error-description)
             state (assoc :state state))})

(defn build-redirect-url
  "Builds the redirect URL with query parameters.

  Args:
    response: Authorization response from handle-authorization-approval or handle-authorization-denial

  Returns:
    Complete redirect URL string"
  [{:keys [redirect-uri params]}]
  (let [query-string (->> params
                          (map (fn [[k v]] (str (name k) "=" (java.net.URLEncoder/encode (str v) "UTF-8"))))
                          (str/join "&"))]
    (str redirect-uri
         (if (str/includes? redirect-uri "?") "&" "?")
         query-string)))
