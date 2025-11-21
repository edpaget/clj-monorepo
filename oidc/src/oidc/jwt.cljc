(ns oidc.jwt
  "JWT token validation and parsing for OIDC ID tokens."
  (:require
   #?(:clj [oidc.jwt.jvm :as jvm]
      :cljs [oidc.jwt.cljs-impl :as cljs-impl])
   [oidc.jwt.protocol :as proto]))

(defn fetch-jwks
  "Fetches JWKS (JSON Web Key Set) from the given URI.

  Args:
    jwks-uri: URL to the JWKS endpoint

  Returns:
    Platform-specific JWKS representation"
  [jwks-uri]
  (let [validator #?(:clj (jvm/create-validator)
                     :cljs (cljs-impl/create-validator))]
    (proto/fetch-jwks validator jwks-uri)))

(defn validate-id-token
  "Validates an OIDC ID token.

  Args:
    token: The JWT ID token string
    jwks: JWKS data (from fetch-jwks)
    expected-issuer: Expected issuer claim value
    expected-audience: Expected audience claim value (client ID)
    opts: Optional validation options
      - :now - Current time in seconds since epoch (for testing)
      - :leeway - Clock skew leeway in seconds (default 0)
      - :nonce - Expected nonce value (if using nonce)

  Returns:
    Validated and decoded token claims (may be a promise in ClojureScript)

  Throws:
    Platform-specific exception on validation failure"
  [token jwks expected-issuer expected-audience opts]
  (let [validator #?(:clj (jvm/create-validator)
                     :cljs (cljs-impl/create-validator))]
    (proto/validate-id-token validator token jwks expected-issuer expected-audience opts)))

(defn decode-header
  "Decodes the JWT header without validation.

  Args:
    token: The JWT token string

  Returns:
    Map containing header claims (alg, kid, etc.)"
  [token]
  (let [validator #?(:clj (jvm/create-validator)
                     :cljs (cljs-impl/create-validator))]
    (proto/decode-header validator token)))
