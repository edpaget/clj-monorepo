(ns polix.optimized.cache
  "Cache for compiled policy evaluators.

  Caches compiled policies by their constraint set hash to avoid
  regenerating evaluators for equivalent policies.

  On the JVM, uses an LRU cache with LinkedHashMap.
  In ClojureScript, uses a simple atom-based cache with size limit."
  (:require
   [polix.operators :as op]
   [polix.optimized.evaluator :as eval])
  #?(:clj
     (:import
      [java.util LinkedHashMap Map Map$Entry])))

;; -----------------------------------------------------------------------------
;; Platform-specific cache implementations
;; -----------------------------------------------------------------------------

#?(:clj
   (defn- lru-cache
     "Creates an LRU cache with the given max size.

     Returns a synchronized LinkedHashMap that removes oldest entries
     when capacity is exceeded."
     [max-size]
     (let [access-order     true
           load-factor      0.75
           initial-capacity (inc max-size)]
       (java.util.Collections/synchronizedMap
        (proxy [LinkedHashMap] [initial-capacity load-factor access-order]
          (removeEldestEntry [^Map$Entry _eldest]
            (> (.size ^Map this) max-size)))))))

#?(:clj
   (defonce ^:private compiled-cache (lru-cache 128))
   :cljs
   (defonce ^:private compiled-cache (atom {})))

#?(:cljs
   (def ^:private max-cache-size 128))

(defonce ^:private stats-atom (atom {:hits 0 :misses 0}))

;; -----------------------------------------------------------------------------
;; Cache key generation
;; -----------------------------------------------------------------------------

(defn- constraint-set-hash
  "Computes a hash for a constraint set.

  The hash includes the registry version to invalidate cached policies
  when operators change."
  [constraint-set]
  (hash [constraint-set (op/registry-version)]))

;; -----------------------------------------------------------------------------
;; Cache operations
;; -----------------------------------------------------------------------------

(defn get-cached
  "Returns a cached compiled policy, or nil if not cached."
  [constraint-set]
  (let [h      (constraint-set-hash constraint-set)
        cached #?(:clj  (.get ^Map compiled-cache h)
                  :cljs (get @compiled-cache h))]
    (if cached
      (do (swap! stats-atom update :hits inc)
          cached)
      (do (swap! stats-atom update :misses inc)
          nil))))

(defn put-cached
  "Caches a compiled policy."
  [constraint-set compiled]
  (let [h (constraint-set-hash constraint-set)]
    #?(:clj
       (.put ^Map compiled-cache h compiled)
       :cljs
       (swap! compiled-cache
              (fn [cache]
                (let [new-cache (assoc cache h compiled)]
                  ;; Simple eviction: if over size, remove oldest entries
                  (if (> (count new-cache) max-cache-size)
                    (into {} (take max-cache-size new-cache))
                    new-cache)))))
    compiled))

(defn compile-cached
  "Compiles a constraint set, using cache if available.

  This is the main entry point for cached compilation. Returns a
  compiled policy, either from cache or freshly compiled."
  ([constraint-set]
   (compile-cached constraint-set {}))
  ([constraint-set opts]
   (or (get-cached constraint-set)
       (put-cached constraint-set (eval/compile-policy constraint-set opts)))))

;; -----------------------------------------------------------------------------
;; Cache management
;; -----------------------------------------------------------------------------

(defn cache-stats
  "Returns cache statistics."
  []
  (let [{:keys [hits misses]} @stats-atom
        total                 (+ hits misses)]
    {:hits hits
     :misses misses
     :total total
     :hit-rate (if (zero? total) 0.0 (double (/ hits total)))
     :size #?(:clj  (.size ^Map compiled-cache)
              :cljs (count @compiled-cache))}))

(defn clear-cache!
  "Clears the compiled policy cache.

  Useful for testing or when operator definitions change significantly."
  []
  #?(:clj  (.clear ^Map compiled-cache)
     :cljs (reset! compiled-cache {}))
  (reset! stats-atom {:hits 0 :misses 0}))

(defn warm-cache!
  "Pre-compiles and caches a collection of constraint sets.

  Use this to warm up the cache with commonly-used policies at
  application startup."
  [constraint-sets]
  (doseq [cs constraint-sets]
    (compile-cached cs)))
