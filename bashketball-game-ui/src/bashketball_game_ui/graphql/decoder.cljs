(ns bashketball-game-ui.graphql.decoder
  "Malli decoder for transforming GraphQL responses into Clojure data.

  Performs the inverse of the graphql-server encoder:
  - Converts camelCase keys to kebab-case keywords
  - Converts enum strings to namespaced keywords
  - Converts JS objects/arrays to Clojure maps/vectors"
  (:require
   [camel-snake-kebab.core :as csk]
   [goog.object :as gobj]
   [malli.core :as m]))

(defn- collect-enums
  "Recursively collects all enum schemas from a Malli schema.

  Returns a map of {string-value -> namespaced-keyword} for all enums
  that have namespaced keyword options."
  [schema]
  (let [result (atom {})]
    (m/walk schema
            (fn [s _path _children _options]
              (when (= :enum (m/type s))
                (let [options (m/children s)]
                  (when (and (seq options)
                             (keyword? (first options))
                             (namespace (first options)))
                    (doseq [opt options]
                      (when (keyword? opt)
                        (swap! result assoc (name opt) opt))))))
              s))
    @result))

(defn- transform-value
  "Transforms a value using the enum map if it's a matching string."
  [enum-map value]
  (if (string? value)
    (get enum-map value value)
    value))

(defn- js->clj-decoded
  "Converts a JavaScript value to Clojure with kebab-case keywords and decoded enums.

  Recursively processes JS objects to maps and JS arrays to vectors,
  converting all object keys from camelCase strings to kebab-case keywords,
  and transforming enum string values to namespaced keywords."
  [enum-map x]
  (cond
    (object? x)
    (into {}
          (map (fn [k]
                 [(csk/->kebab-case-keyword k)
                  (js->clj-decoded enum-map (gobj/get x k))]))
          (js-keys x))

    (array? x)
    (mapv #(js->clj-decoded enum-map %) x)

    (string? x)
    (transform-value enum-map x)

    :else x))

(defn- clj->clj-decoded
  "Transforms a Clojure data structure, converting enum strings to keywords."
  [enum-map x]
  (cond
    (map? x)
    (into {}
          (map (fn [[k v]]
                 [k (clj->clj-decoded enum-map v)]))
          x)

    (vector? x)
    (mapv #(clj->clj-decoded enum-map %) x)

    (sequential? x)
    (map #(clj->clj-decoded enum-map %) x)

    (string? x)
    (transform-value enum-map x)

    :else x))

(defn decode
  "Decodes a GraphQL response value using the given Malli schema.

  Extracts enum definitions from the schema, then converts the value:
  1. JS objects/arrays become Clojure maps/vectors
  2. camelCase keys become kebab-case keywords
  3. Enum string values become namespaced keywords

  This approach handles multi-schema dispatch correctly because enum
  values are transformed before Malli sees the data.

  Example:
    (decode card-schema/Card apollo-card)
    ;=> {:slug \"player-1\"
    ;    :name \"Star Player\"
    ;    :card-type :card-type/PLAYER_CARD
    ;    ...}"
  [schema value]
  (let [enum-map (collect-enums schema)]
    (if (object? value)
      (js->clj-decoded enum-map value)
      (clj->clj-decoded enum-map value))))

(defn decode-seq
  "Decodes a sequence of GraphQL response values using the given schema.

  Convenience function for decoding arrays of items like query results."
  [schema values]
  (let [enum-map (collect-enums schema)]
    (if (array? values)
      (mapv #(js->clj-decoded enum-map %) values)
      (mapv #(clj->clj-decoded enum-map %) values))))
