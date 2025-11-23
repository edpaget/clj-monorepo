(ns oidc.jwt.protocol
  "Protocol abstraction for JWT operations across Clojure and ClojureScript.")

(defprotocol IJWTValidator
  "Protocol for JWT token validation operations."
  (fetch-jwks [this jwks-uri]
    "Fetches JWKS from the given URI.

    Takes a URL to the JWKS endpoint and retrieves the JSON Web Key Set. Returns
    a platform-specific JWKS representation that can be used for token signature
    validation.")

  (validate-id-token [this token jwks expected-issuer expected-audience opts]
    "Validates an OIDC ID token.

    Takes a JWT ID token string, JWKS data in platform-specific format, the expected
    issuer claim value, the expected audience claim value, and an options map. The
    options can include `:now` (current time in seconds since epoch for testing),
    `:leeway` (clock skew leeway in seconds, defaults to 0), and `:nonce` (expected
    nonce value if using nonce parameter).

    Validates the token signature using the JWKS, checks expiration and claims, and
    returns the validated and decoded token claims. Throws a platform-specific
    exception on validation failure."))

(defprotocol IJWTParser
  "Protocol for JWT parsing operations."
  (decode-header [this token]
    "Decodes the JWT header without validation.

    Takes a JWT token string and decodes just the header portion without validating
    the signature or claims. Returns a map containing header claims like `alg`,
    `kid`, etc. Useful for inspecting token metadata before full validation."))
