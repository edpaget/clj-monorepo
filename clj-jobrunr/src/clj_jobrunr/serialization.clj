(ns clj-jobrunr.serialization
  "EDN serialization for job payloads.

  Provides configurable serialization with built-in support for java.time types.
  By default, Instant, Duration, and LocalDate serialize as tagged literals
  (`#time/instant`, `#time/duration`, `#time/local-date`) without modifying
  global state.

  Users can exclude default handlers or add custom ones that compose with them."
  (:require
   [clojure.edn :as edn]
   [clojure.instant]
   [clojure.walk :as walk])
  (:import
   [java.time Instant Duration LocalDate]))

(def ^:dynamic *serializer*
  "Dynamic var holding the active serializer for job execution.
  Set by the Integrant server component at startup."
  nil)

(def default-readers
  "Default EDN readers for standard and java.time tagged literals."
  {'inst            #(java.util.Date. (inst-ms (clojure.instant/read-instant-date %)))
   'uuid            #(java.util.UUID/fromString %)
   'time/instant    #(Instant/parse %)
   'time/duration   #(Duration/parse %)
   'time/local-date #(LocalDate/parse %)})

(def default-writers
  "Default writers that transform java.time values to tagged literals.
  Maps Class to a function that returns a tagged-literal."
  {Instant   (fn [^Instant v] (tagged-literal 'time/instant (.toString v)))
   Duration  (fn [^Duration v] (tagged-literal 'time/duration (.toString v)))
   LocalDate (fn [^LocalDate v] (tagged-literal 'time/local-date (.toString v)))})

(defn- transform-for-edn
  "Walks data, replacing values whose types are in writers with tagged-literals."
  [writers data]
  (walk/postwalk
   (fn [v]
     (if-let [writer (get writers (type v))]
       (writer v)
       v))
   data))

(defn- make-write-fn
  "Creates a write function using the given writers map."
  [writers]
  (fn [data]
    (pr-str (transform-for-edn writers data))))

(defn make-serializer
  "Creates a serializer with the given options.

  Options:
    :readers         - Map of tag symbols to reader fns, merged with defaults
    :writers         - Map of Class to writer fns, merged with defaults
    :exclude-readers - Set of reader tags to exclude from defaults
    :exclude-writers - Set of Classes to exclude from default writers
    :read-fn         - Custom read function (takes precedence over :readers)
    :write-fn        - Custom write function (takes precedence over :writers)"
  [{:keys [readers writers exclude-readers exclude-writers read-fn write-fn]}]
  (let [base-readers    (apply dissoc default-readers exclude-readers)
        merged-readers  (merge base-readers readers)
        base-writers    (apply dissoc default-writers exclude-writers)
        merged-writers  (merge base-writers writers)
        actual-read-fn  (or read-fn
                            (fn [s] (edn/read-string {:readers merged-readers} s)))
        actual-write-fn (or write-fn
                            (make-write-fn merged-writers))]
    {:read-fn actual-read-fn
     :write-fn actual-write-fn}))

(defn default-serializer
  "Returns a serializer with default readers and writers for java.time types."
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
