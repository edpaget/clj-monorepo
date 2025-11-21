(ns oidc.jwt.protocol
  "Protocol abstraction for JWT operations across Clojure and ClojureScript.")

(defprotocol IJWTValidator
  "Protocol for JWT token validation operations."
  (fetch-jwks [this jwks-uri]
    "Fetches JWKS from the given URI.

    Args:
      jwks-uri: URL to the JWKS endpoint

    Returns:
      Platform-specific JWKS representation")

  (validate-id-token [this token jwks expected-issuer expected-audience opts]
    "Validates an OIDC ID token.

    Args:
      token: The JWT ID token string
      jwks: JWKS data (platform-specific format)
      expected-issuer: Expected issuer claim value
      expected-audience: Expected audience claim value
      opts: Optional validation options
        - :now - Current time in seconds since epoch (for testing)
        - :leeway - Clock skew leeway in seconds (default 0)
        - :nonce - Expected nonce value (if using nonce)

    Returns:
      Validated and decoded token claims

    Throws:
      Platform-specific exception on validation failure"))

(defprotocol IJWTParser
  "Protocol for JWT parsing operations."
  (decode-header [this token]
    "Decodes the JWT header without validation.

    Args:
      token: The JWT token string

    Returns:
      Map containing header claims (alg, kid, etc.)"))
