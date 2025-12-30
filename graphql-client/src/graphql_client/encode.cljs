(ns graphql-client.encode
  "Variable and option encoding utilities for Apollo GraphQL hooks.

  Provides functions for encoding Clojure data structures to JavaScript
  format expected by Apollo Client, including converting kebab-case keys
  to camelCase for GraphQL compatibility."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.walk :as walk]))

(defn encode-variable-keys
  "Converts kebab-case keys to camelCase in a variables map.

  Recursively transforms all map keys for GraphQL compatibility.
  Returns nil if input is nil."
  [variables]
  (when variables
    (walk/postwalk
     (fn [x]
       (if (map? x)
         (into {} (map (fn [[k v]] [(csk/->camelCase k) v])) x)
         x))
     variables)))

(defn encode-options
  "Encodes hook options for Apollo, converting all keys to camelCase.

  Converts option keys (like :refetch-queries -> refetchQueries) and
  variable keys for GraphQL compatibility.
  Always returns a JS object (never null) for Apollo Client 4 compatibility."
  [options]
  (clj->js (if options
             (-> options
                 (update :variables encode-variable-keys)
                 (->> (into {} (map (fn [[k v]] [(csk/->camelCase k) v])))))
             {})))
