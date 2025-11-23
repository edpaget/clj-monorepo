(ns oidc.discovery.protocol
  "Protocol abstraction for OIDC discovery operations across Clojure and ClojureScript.")

(defprotocol IDiscoveryClient
  "Protocol for OIDC discovery document fetching."
  (fetch-discovery-document [this issuer]
    "Fetches and parses the OIDC discovery document from the issuer.

    Takes an OIDC issuer URL and fetches the discovery document from the
    .well-known/openid-configuration endpoint. In Clojure, returns the parsed
    discovery document as a map. In ClojureScript, may return a promise that
    resolves to the document. Throws a platform-specific exception on HTTP
    errors or when the document is invalid."))
