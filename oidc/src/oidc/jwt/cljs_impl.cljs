(ns oidc.jwt.cljs-impl
  "ClojureScript implementation of JWT operations using panva/jose."
  (:require
   ["jose" :as jose]
   [oidc.jwt.protocol :as proto]))

(defn- normalize-audience
  [aud]
  (if (string? aud) [aud] (into [] aud)))

(defn js->clj-deep
  "Recursively converts JavaScript object to Clojure with keywordized keys."
  [x]
  (js->clj x :keywordize-keys true))

(defn- validate-claims
  [claims expected-issuer expected-audience nonce]
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
  claims)

#_{:clj-kondo/ignore [:missing-docstring]}
(defrecord JSValidator []
  proto/IJWTValidator
  (fetch-jwks [_ jwks-uri]
    (jose/createRemoteJWKSet (js/URL. jwks-uri)))

  (validate-id-token [_ token jwks expected-issuer expected-audience opts]
    (let [{:keys [nonce]} opts]
      (-> (jose/jwtVerify token jwks
                          (clj->js {:issuer expected-issuer
                                    :audience expected-audience}))
          (.then (fn [^js result]
                   (let [claims (js->clj-deep (.-payload result))]
                     (validate-claims claims expected-issuer expected-audience nonce)
                     claims)))
          (.catch (fn [error]
                    (throw (ex-info "JWT validation failed"
                                    {:message (.-message error)
                                     :cause error})))))))

  proto/IJWTParser
  (decode-header [_ token]
    (-> (jose/decodeProtectedHeader token)
        js->clj-deep)))

(defn create-validator
  "Creates a ClojureScript-based JWT validator.

  Returns:
    IJWTValidator implementation using panva/jose"
  []
  (->JSValidator))
