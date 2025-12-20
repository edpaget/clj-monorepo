(ns polix.result
  "Tagged map result type for success/error handling.

  Provides a simple alternative to monadic error handling using plain
  data structures. Results are maps with either `:ok` or `:error` keys.")

(defn ok
  "Wraps a value as a success result."
  [value]
  {:ok value})

(defn error
  "Wraps an error map as an error result."
  [error-map]
  {:error error-map})

(defn ok?
  "Returns true if result is a success."
  [result]
  (contains? result :ok))

(defn error?
  "Returns true if result is an error."
  [result]
  (contains? result :error))

(defn unwrap
  "Extracts value from success, or error-map from error."
  [result]
  (if (ok? result)
    (:ok result)
    (:error result)))

(defn map-ok
  "Applies f to the value if result is ok, returns error unchanged."
  [result f]
  (if (ok? result)
    (ok (f (:ok result)))
    result))

(defn bind
  "Applies f to value if ok (f must return a result), returns error unchanged."
  [result f]
  (if (ok? result)
    (f (:ok result))
    result))

(defn sequence-results
  "Converts a seq of results into a result of seq. Fails on first error."
  [results]
  (reduce (fn [acc r]
            (if (error? r)
              (reduced r)
              (ok (conj (:ok acc) (:ok r)))))
          (ok [])
          results))
