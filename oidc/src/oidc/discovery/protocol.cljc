(ns oidc.discovery.protocol
  "Protocol abstraction for OIDC discovery operations across Clojure and ClojureScript.")

(defprotocol IDiscoveryClient
  "Protocol for OIDC discovery document fetching."
  (fetch-discovery-document [this issuer]
    "Fetches and parses the OIDC discovery document from the issuer.

    Args:
      issuer: The OIDC issuer URL

    Returns:
      Parsed discovery document as a map (ClojureScript may return a promise)

    Throws:
      Platform-specific exception on HTTP errors or invalid document"))
