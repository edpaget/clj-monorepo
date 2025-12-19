(ns polix.document
  "Document abstraction for policy evaluation.

  Provides a protocol-based interface for accessing key-value data during
  policy evaluation. Documents can be backed by static data structures like
  maps, or by dynamic sources like databases.")

(defprotocol Document
  "Protocol for key-value document interface.

  A Document provides access to key-value data and can be backed by
  static data structures or dynamic sources like databases."

  (doc-get [this key]
    "Returns the value associated with `key`, or `nil` if not found.

    Note: `nil` is a valid value. Use [[doc-contains?]] to distinguish
    key absence from a `nil` value.")

  (doc-keys [this]
    "Returns a collection of all keys available in this document.")

  (doc-project [this ks]
    "Returns a new document containing only the specified keys from `ks`.")

  (doc-merge [this other]
    "Merges this document with `other`, with `other`'s values taking precedence.

    Returns a new merged document."))

(defrecord MapDocument [data]
  Document

  (doc-get [_ key]
    (get data key))

  (doc-keys [_]
    (keys data))

  (doc-project [_ ks]
    (->MapDocument (select-keys data ks)))

  (doc-merge [_ other]
    (->MapDocument (merge data (:data other)))))

(defn map-document
  "Creates a [[Document]] from a map `m`.

  Returns a `MapDocument` wrapping the provided map."
  [m]
  (->MapDocument m))

(defmulti doc-contains?
  "Returns `true` if `key` exists in `document`, `false` otherwise.

  Uses [[doc-keys]] by default to determine presence. Custom Document
  implementations may provide optimized implementations by adding methods
  for their specific type.

  Example:

      (let [doc (map-document {:role \"admin\" :name nil})]
        (doc-contains? doc :role)   ;=> true
        (doc-contains? doc :name)   ;=> true (key exists with nil value)
        (doc-contains? doc :missing) ;=> false)"
  (fn [document _key] (type document)))

(defmethod doc-contains? :default
  [document key]
  (contains? (set (doc-keys document)) key))

(defmethod doc-contains? MapDocument
  [document key]
  (contains? (:data document) key))
