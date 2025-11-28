(ns bashketball-editor-api.util.edn
  "EDN serialization utilities with proper timestamp support.

  Provides serialization for `java.time.Instant` using the standard `#inst`
  tagged literal format, which can be read back by Clojure's EDN reader.

  Require this namespace to enable proper Instant serialization via `pr-str`."
  (:require
   [clojure.edn :as edn])
  (:import
   [java.time Instant]
   [java.time.format DateTimeFormatter]))

(def ^:private instant-formatter
  "ISO-8601 formatter for Instant serialization."
  DateTimeFormatter/ISO_INSTANT)

(defmethod print-method Instant
  [^Instant instant ^java.io.Writer w]
  (.write w "#inst \"")
  (.write w (.format instant-formatter instant))
  (.write w "\""))

(defmethod print-dup Instant
  [^Instant instant ^java.io.Writer w]
  (print-method instant w))

(def edn-readers
  "EDN readers for custom types.

  The standard `#inst` reader returns `java.util.Date`, but we want `java.time.Instant`.
  Use this with `clojure.edn/read-string` to get Instants back."
  {'inst (fn [s] (Instant/parse s))})

(defn read-edn
  "Reads an EDN string with support for Instant timestamps.

  Unlike `clojure.edn/read-string`, this returns `java.time.Instant` for `#inst`
  tagged literals instead of `java.util.Date`."
  [s]
  (edn/read-string {:readers edn-readers} s))
