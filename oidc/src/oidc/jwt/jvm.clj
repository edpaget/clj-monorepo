(ns oidc.jwt.jvm
  "JVM implementation of JWT operations using buddy-sign."
  (:require
   [buddy.core.keys :as keys]
   [buddy.sign.jwt :as jwt]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.core.cache.wrapped :as cache]
   [clojure.string :as str]
   [malli.core :as m]
   [oidc.jwt.protocol :as proto]))

(set! *warn-on-reflection* true)

(def ^:private jwks-cache
  (cache/ttl-cache-factory {} :ttl (* 60 60 1000)))

(def IDTokenClaims
  "Malli schema for OIDC ID token claims."
  [:map
   [:iss :string]
   [:sub :string]
   [:aud [:or :string [:vector :string]]]
   [:exp pos-int?]
   [:iat pos-int?]
   [:nonce {:optional true} :string]
   [:at_hash {:optional true} :string]
   [:c_hash {:optional true} :string]])

(defn- parse-jwk
  [jwk]
  (keys/jwk->public-key jwk))

(defn- normalize-audience
  [aud]
  (if (string? aud) [aud] aud))

(defn- fetch-jwks-uncached
  [jwks-uri]
  (let [response  (http/get jwks-uri {:accept :json})
        jwks      (json/parse-string (:body response) true)
        keys-list (:keys jwks)]
    (into {}
          (map (fn [jwk]
                 [(:kid jwk) (parse-jwk jwk)]))
          keys-list)))

(defrecord JVMValidator []
  proto/IJWTValidator
  (fetch-jwks [_ jwks-uri]
    (cache/lookup-or-miss jwks-cache jwks-uri fetch-jwks-uncached))

  (validate-id-token [_ token jwks expected-issuer expected-audience opts]
    (let [{:keys [now leeway nonce]} opts
          header                     (-> token
                                         (str/split #"\.")
                                         first
                                         (jwt/decode-header))
          kid                        (:kid header)
          alg                        (:alg header)
          public-key                 (get jwks kid)]
      (when-not public-key
        (throw (ex-info "Unknown key ID in token"
                        {:kid kid
                         :available-keys (keys jwks)})))
      (let [claims (jwt/unsign token public-key
                               (cond-> {:alg (keyword alg)}
                                 now (assoc :now now)
                                 leeway (assoc :leeway leeway)))]
        (when-not (m/validate IDTokenClaims claims)
          (throw (ex-info "Invalid ID token claims"
                          {:errors (m/explain IDTokenClaims claims)})))
        (when (not= (:iss claims) expected-issuer)
          (throw (ex-info "Issuer mismatch"
                          {:expected expected-issuer
                           :actual (:iss claims)})))
        (let [audiences (normalize-audience (:aud claims))]
          (when-not (some #{expected-audience} audiences)
            (throw (ex-info "Audience mismatch"
                            {:expected expected-audience
                             :actual audiences}))))
        (when (and nonce (not= (:nonce claims) nonce))
          (throw (ex-info "Nonce mismatch"
                          {:expected nonce
                           :actual (:nonce claims)})))
        claims)))

  proto/IJWTParser
  (decode-header [_ token]
    (-> token
        (str/split #"\.")
        first
        jwt/decode-header)))

(defn create-validator
  "Creates a JVM-based JWT validator.

  Returns:
    IJWTValidator implementation using buddy-sign"
  []
  (->JVMValidator))
