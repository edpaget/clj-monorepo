(ns polix.bytecode.cache
  "LRU cache for compiled policy classes.

  Caches compiled policies by their constraint set hash to avoid
  regenerating bytecode for equivalent policies."
  (:require
   [polix.bytecode.generator :as gen]
   [polix.operators :as op])
  (:import
   [java.util.concurrent ConcurrentHashMap]
   [java.util LinkedHashMap Map Map$Entry]))

(defn- lru-cache
  "Creates an LRU cache with the given max size.

  Returns a synchronized LinkedHashMap that removes oldest entries
  when capacity is exceeded."
  [max-size]
  (let [access-order true
        load-factor 0.75
        initial-capacity (inc max-size)]
    (java.util.Collections/synchronizedMap
     (proxy [LinkedHashMap] [initial-capacity load-factor access-order]
       (removeEldestEntry [^Map$Entry eldest]
         (> (.size ^Map this) max-size))))))

;; Global cache of compiled policies, keyed by constraint set hash.
(defonce ^:private compiled-cache (lru-cache 128))

;; Cache statistics.
(defonce ^:private stats-atom (atom {:hits 0 :misses 0}))

(defn- constraint-set-hash
  "Computes a hash for a constraint set.

  The hash includes the registry version to invalidate cached policies
  when operators change."
  [constraint-set]
  (hash [constraint-set (op/registry-version)]))

(defn get-cached
  "Returns a cached compiled policy, or nil if not cached."
  [constraint-set]
  (let [h (constraint-set-hash constraint-set)
        cached (.get ^Map compiled-cache h)]
    (if cached
      (do (swap! stats-atom update :hits inc)
          cached)
      (do (swap! stats-atom update :misses inc)
          nil))))

(defn put-cached
  "Caches a compiled policy."
  [constraint-set compiled]
  (let [h (constraint-set-hash constraint-set)]
    (.put ^Map compiled-cache h compiled)
    compiled))

(defn compile-cached
  "Compiles a constraint set, using cache if available.

  This is the main entry point for cached compilation. Returns a
  compiled policy, either from cache or freshly compiled."
  ([constraint-set]
   (compile-cached constraint-set {}))
  ([constraint-set opts]
   (or (get-cached constraint-set)
       (put-cached constraint-set (gen/compile-to-bytecode constraint-set opts)))))

(defn cache-stats
  "Returns cache statistics."
  []
  (let [{:keys [hits misses]} @stats-atom
        total (+ hits misses)]
    {:hits hits
     :misses misses
     :total total
     :hit-rate (if (zero? total) 0.0 (double (/ hits total)))
     :size (.size ^Map compiled-cache)}))

(defn clear-cache!
  "Clears the compiled policy cache.

  Useful for testing or when operator definitions change significantly."
  []
  (.clear ^Map compiled-cache)
  (reset! stats-atom {:hits 0 :misses 0}))

(defn warm-cache!
  "Pre-compiles and caches a collection of constraint sets.

  Use this to warm up the cache with commonly-used policies at
  application startup."
  [constraint-sets]
  (doseq [cs constraint-sets]
    (compile-cached cs)))
