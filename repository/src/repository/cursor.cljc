(ns repository.cursor
  "Cursor encoding and decoding for pagination.

  Cursors are opaque strings that encode the position in a result set,
  enabling efficient keyset pagination. This is more efficient than
  offset-based pagination for large datasets.

  ## Cursor Format

  Cursors encode a map of field values representing the last seen record.
  The format is Base64-encoded EDN for cross-platform compatibility.

  ## Example Usage

      (require '[repository.cursor :as cursor])

      ;; Encode cursor from last record
      (cursor/encode {:created-at #inst \"2024-01-15T10:00:00Z\" :id \"abc\"})
      ;; => \"ezpjcmVhdGVkLWF0ICMuLi4sIDppZCAiYWJjIn0=\"

      ;; Decode cursor
      (cursor/decode \"ezpjcmVhdGVkLWF0ICMuLi4sIDppZCAiYWJjIn0=\")
      ;; => {:created-at #inst \"2024-01-15T10:00:00Z\" :id \"abc\"}

      ;; Create cursor from entity and order-by spec
      (cursor/from-entity {:id \"abc\" :name \"Alice\" :created-at #inst \"...\"}
                          [[:created-at :desc] [:id :asc]])
      ;; => \"...\""
  #?(:clj (:import [java.util Base64])
     :cljs (:require [cljs.reader])))

#?(:clj
   (defn- base64-encode
     "Encodes a string to Base64."
     [^String s]
     (.encodeToString (Base64/getEncoder) (.getBytes s "UTF-8")))

   :cljs
   (defn- base64-encode
     "Encodes a string to Base64."
     [s]
     (js/btoa (js/unescape (js/encodeURIComponent s)))))

#?(:clj
   (defn- base64-decode
     "Decodes a Base64 string."
     [^String s]
     (String. (.decode (Base64/getDecoder) s) "UTF-8"))

   :cljs
   (defn- base64-decode
     "Decodes a Base64 string."
     [s]
     (js/decodeURIComponent (js/escape (js/atob s)))))

(defn encode
  "Encodes cursor data to an opaque string.

  The cursor data should be a map of field names to values representing
  the position in the result set. Typically these are the values of the
  order-by fields from the last record."
  [cursor-data]
  (when (and cursor-data (seq cursor-data))
    (base64-encode (pr-str cursor-data))))

(defn decode
  "Decodes an opaque cursor string back to cursor data.

  Returns nil if the cursor is nil, empty, or invalid."
  [cursor-str]
  (when (and cursor-str (seq cursor-str))
    (try
      #?(:clj (read-string (base64-decode cursor-str))
         :cljs (cljs.reader/read-string (base64-decode cursor-str)))
      (catch #?(:clj Exception :cljs js/Error) _
        nil))))

(defn from-entity
  "Creates cursor data from an entity and order-by specification.

  Extracts the values of the order-by fields from the entity to create
  cursor data that can be used to fetch the next page.

  The order-by spec is a vector of `[field direction]` pairs, e.g.:
  `[[:created-at :desc] [:id :asc]]`"
  [entity order-by]
  (when (and entity (seq order-by))
    (reduce (fn [cursor [field _direction]]
              (assoc cursor field (get entity field)))
            {}
            order-by)))

(defn encode-from-entity
  "Creates and encodes a cursor from an entity and order-by specification.

  Convenience function combining [[from-entity]] and [[encode]]."
  [entity order-by]
  (encode (from-entity entity order-by)))

(defn page-info
  "Creates page-info for a result set.

  Takes the result data, order-by specification, and limit to determine
  if there are more pages and create the end cursor."
  [data order-by limit]
  (let [has-more    (and limit (= (count data) limit))
        last-entity (last data)]
    {:has-next-page has-more
     :end-cursor (when (and has-more last-entity)
                   (encode-from-entity last-entity order-by))}))
