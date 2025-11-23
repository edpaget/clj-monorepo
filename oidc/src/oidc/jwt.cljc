(ns oidc.jwt
  "JWT token validation and parsing for OIDC ID tokens."
  (:require
   #?(:clj [oidc.jwt.jvm :as jvm]
      :cljs [oidc.jwt.cljs-impl :as cljs-impl])
   [oidc.jwt.protocol :as proto]))

(defn fetch-jwks
  "Fetches JWKS (JSON Web Key Set) from the given URI.

   Takes a URL to the JWKS endpoint, creates a platform-specific validator, and
   fetches the JSON Web Key Set. Returns a platform-specific JWKS representation
   that can be used for token validation."
  [jwks-uri]
  (let [validator #?(:clj (jvm/create-validator)
                     :cljs (cljs-impl/create-validator))]
    (proto/fetch-jwks validator jwks-uri)))

(defn validate-id-token
  "Validates an OIDC ID token.

   Takes a JWT ID token string, JWKS data (from [[fetch-jwks]]), the expected issuer
   claim value, the expected audience claim value (client ID), and an options map.
   The options can include `:now` (current time in seconds since epoch for testing),
   `:leeway` (clock skew leeway in seconds, defaults to 0), and `:nonce` (expected
   nonce value if using nonce parameter).

   Creates a platform-specific validator and validates the token signature, expiration,
   and claims. In Clojure, returns the validated and decoded token claims map. In
   ClojureScript, returns a promise that resolves to the claims. Throws a
   platform-specific exception on validation failure."
  [token jwks expected-issuer expected-audience opts]
  (let [validator #?(:clj (jvm/create-validator)
                     :cljs (cljs-impl/create-validator))]
    (proto/validate-id-token validator token jwks expected-issuer expected-audience opts)))

(defn decode-header
  "Decodes the JWT header without validation.

   Takes a JWT token string, creates a platform-specific validator, and decodes just
   the header portion without validating the signature or claims. Returns a map
   containing header claims like `alg`, `kid`, etc. Useful for inspecting token
   metadata before full validation."
  [token]
  (let [validator #?(:clj (jvm/create-validator)
                     :cljs (cljs-impl/create-validator))]
    (proto/decode-header validator token)))
