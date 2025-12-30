(ns graphql-client.core
  "Core JS to Clojure conversion utilities for GraphQL responses.

  Provides functions for converting JavaScript objects and arrays to Clojure
  data structures while preserving string keys for schema-driven transformation."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [goog.object :as gobj]))

(defn js->clj-preserve-keys
  "Converts a JavaScript value to Clojure, preserving string keys.

  Recursively processes JS objects to maps and JS arrays to vectors,
  but keeps all object keys as strings. This allows Malli's schema-driven
  transformers to handle key conversion appropriately:
  - `:map` keys get converted to kebab-case keywords via key-transformer
  - `:map-of` keys get decoded according to their key schema"
  [x]
  (cond
    (object? x)
    (into {}
          (map (fn [k] [k (js->clj-preserve-keys (gobj/get x k))]))
          (js-keys x))

    (array? x)
    (mapv js->clj-preserve-keys x)

    :else x))

(defn- uppercase-string?
  "Returns true if string is all uppercase (e.g., \"HOME\", \"AWAY\").

  Used to preserve keys that have explicit :graphql/name overrides in the schema."
  [s]
  (and (string? s)
       (not (str/blank? s))
       (= s (str/upper-case s))))

(defn convert-key
  "Converts a string key to keyword, preserving uppercase keys.

  Uppercase keys (HOME, AWAY) become uppercase keywords to match GraphQL
  schema fields with :graphql/name overrides."
  [k]
  (cond
    (keyword? k)          k
    (uppercase-string? k) (keyword k)
    (string? k)           (csk/->kebab-case-keyword k)
    :else                 k))

(defn convert-remaining-string-keys
  "Converts any remaining string keys to kebab-case keywords, preserving uppercase.

  Applied after schema-based decoding. At this point, :map-of keys have
  already been converted by Malli, so any remaining string keys are from
  wrapper maps without __typename.

  Uppercase string keys (HOME, AWAY) are preserved as uppercase keywords
  to match GraphQL schema fields with :graphql/name overrides."
  [x]
  (cond
    (and (map? x) (some string? (keys x)))
    (into {}
          (map (fn [[k v]]
                 [(convert-key k)
                  (convert-remaining-string-keys v)]))
          x)

    (vector? x)
    (mapv convert-remaining-string-keys x)

    :else x))

(defn get-typename
  "Gets __typename from a map, handling both string and keyword keys.

  This supports maps that haven't yet been through key transformation,
  where __typename may still be a string key."
  [m]
  (or (get m :__typename) (get m "__typename")))
