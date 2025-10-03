(ns polix.core
  "Core functionality for polix - a DSL for writing declarative policies")

(defprotocol Document
  "Protocol for key-value document interface.
  
  A Document provides access to key-value data and can be backed by
  static data structures or dynamic sources like databases."

  (doc-get [this key]
    "Get the value associated with key.
    
    Args:
      this: The document
      key: The key to look up
      
    Returns:
      The value associated with key, or nil if not found")

  (doc-keys [this]
    "Get all available keys in this document.
    
    Args:
      this: The document
      
    Returns:
      A collection of all keys available in the document")

  (doc-project [this ks]
    "Project the document to only include specified keys.
    
    Args:
      this: The document
      ks: Collection of keys to project
      
    Returns:
      A new document containing only the specified keys")

  (doc-merge [this other]
    "Merge this document with another, left-to-right.
    
    Args:
      this: The first document (left)
      other: The second document (right)
      
    Returns:
      A new merged document where other's values override this's values"))

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
  "Create a Document from a map.
  
  Args:
    m: A map of key-value pairs
    
  Returns:
    A MapDocument wrapping the provided map"
  [m]
  (->MapDocument m))
