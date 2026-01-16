(ns clj-jobrunr.serialization
  "EDN serialization for job payloads.

  Provides configurable serialization with support for custom tagged literals.
  Users can provide custom readers for types like java.time, or supply
  completely custom read/write functions."
  (:require [clojure.edn :as edn]
            [clojure.instant])
  (:import [java.time Instant Duration LocalDate]))

(def ^:private default-readers
  "Default EDN readers for standard tagged literals."
  {'inst #(java.util.Date. (inst-ms (clojure.instant/read-instant-date %)))
   'uuid #(java.util.UUID/fromString %)})

(defn make-serializer
  "Creates a serializer with the given options.

  Options:
    :readers  - Map of tag symbols to reader functions, merged with defaults
    :read-fn  - Custom read function (takes precedence over :readers)
    :write-fn - Custom write function (defaults to pr-str)"
  [{:keys [readers read-fn write-fn]}]
  (let [merged-readers  (merge default-readers readers)
        actual-read-fn  (or read-fn
                            (fn [s] (edn/read-string {:readers merged-readers} s)))
        actual-write-fn (or write-fn pr-str)]
    {:read-fn actual-read-fn
     :write-fn actual-write-fn}))

(defn default-serializer
  "Returns a serializer with default EDN readers for #inst and #uuid."
  []
  (make-serializer {}))

(defn serialize
  "Serializes data to an EDN string using the serializer's write function."
  [serializer data]
  ((:write-fn serializer) data))

(defn deserialize
  "Deserializes an EDN string to data using the serializer's read function."
  [serializer s]
  ((:read-fn serializer) s))

(defn install-time-print-methods!
  "Installs print-method implementations for common java.time types.

  After calling this, java.time objects will serialize as tagged literals:
    Instant   -> #time/instant \"2024-01-15T10:30:00Z\"
    Duration  -> #time/duration \"PT2H\"
    LocalDate -> #time/local-date \"2024-01-15\""
  []
  (defmethod print-method Instant [^Instant v ^java.io.Writer w]
    (.write w (str "#time/instant \"" (.toString v) "\"")))

  (defmethod print-method Duration [^Duration v ^java.io.Writer w]
    (.write w (str "#time/duration \"" (.toString v) "\"")))

  (defmethod print-method LocalDate [^LocalDate v ^java.io.Writer w]
    (.write w (str "#time/local-date \"" (.toString v) "\""))))
