(ns graphql-client.decoder
  "Automatic GraphQL response decoder using __typename dispatch.

  Provides functions for building decoders that use GraphQL's __typename
  field to dispatch to the appropriate Malli schema for decoding.

  Primary API:
  - [[build-decode-fn]] - Creates a decoder from typename->schema map
  - [[decode-js-response]] - Convenience fn for decoding JS responses"
  (:require
   [clojure.walk :as walk]
   [graphql-client.core :as core]
   [graphql-client.transformer :as transformer]))

(defn build-decode-fn
  "Returns a decoder function using the given typename->schema mappings.

  Uses schema-driven transformation:
  1. Postwalk applies Malli decoding to maps with __typename
  2. Malli's transformers handle :map keys and :map-of keys per schema
  3. Final pass converts remaining string keys (wrapper maps without __typename)

  The returned function takes a Clojure map (with string keys) and returns
  the decoded data with proper Clojure types.

  Example:
    (def registry {\"User\" UserSchema \"Post\" PostSchema})
    (def decode (build-decode-fn registry))
    (decode {\"__typename\" \"User\" \"firstName\" \"Alice\"})"
  ([type-mappings]
   (build-decode-fn type-mappings transformer/decoding-transformer))
  ([type-mappings custom-transformer]
   (letfn [(decode-if-typed [m]
             (if-let [schema (get type-mappings (core/get-typename m))]
               (transformer/decode-with-transformer m schema custom-transformer)
               m))]
     (fn [data]
       (->> data
            (walk/postwalk (fn [x] (cond-> x (map? x) decode-if-typed)))
            core/convert-remaining-string-keys)))))

(defn decode-js-response
  "Decodes a JS GraphQL response using __typename dispatch.

  Convenience function that combines JS->Clojure conversion with schema-driven
  decoding. Takes a typename->schema map and returns a function that decodes
  JS responses.

  Example:
    (def decode (decode-js-response registry))
    (decode js-response)"
  ([type-mappings]
   (decode-js-response type-mappings transformer/decoding-transformer))
  ([type-mappings custom-transformer]
   (let [decode-fn (build-decode-fn type-mappings custom-transformer)]
     (fn [js-value]
       (-> js-value
           core/js->clj-preserve-keys
           decode-fn)))))
