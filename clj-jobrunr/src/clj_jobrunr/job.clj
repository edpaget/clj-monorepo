(ns clj-jobrunr.job
  "Job handler definition and dispatch.

  Provides the [[defjob]] macro for defining background job handlers and
  the [[handle-job]] multimethod for dispatching job execution by job type.

  Jobs are defined using the [[defjob]] macro which generates:
  - A var holding the namespaced keyword for the job type
  - A method on [[handle-job]] for multimethod dispatch

  Example:
  ```clojure
  (defjob send-email
    \"Sends an email to a user.\"
    [{:keys [to subject body]}]
    (email/send! to subject body))

  ;; Via multimethod dispatch
  (handle-job send-email {:to \"user@example.com\" :subject \"Hi\"})

  ;; Or with the keyword directly
  (handle-job ::send-email {:to \"user@example.com\" :subject \"Hi\"})

  ;; Use with enqueue!
  (enqueue! send-email {:to \"user@example.com\"})
  ```

  Jobs can participate in hierarchies using the `:job/derives` attr-map:
  ```clojure
  (defjob send-email
    \"Sends an email.\"
    {:job/derives [::notification]}
    [{:keys [to subject]}]
    (email/send! to subject))
  ```")

(defmulti handle-job
  "Dispatches job execution by job type keyword.

  Takes a job type keyword and payload map, dispatching to the appropriate
  handler registered via [[defjob]]. Supports Clojure hierarchies via `derive`
  for grouping related job types.

  Job types are namespaced keywords (e.g., `::send-email` or `:my.ns/send-email`).

  Throws ExceptionInfo if no handler is registered for the job type."
  (fn [job-type _payload] job-type))

(defmethod handle-job :default [job-type _payload]
  (throw (ex-info "No handler registered for job type"
                  {:job-type job-type})))

(defn- parse-defjob-args
  "Parses defjob arguments into [docstring attr-map params body].
  Follows defn conventions: name docstring? attr-map? [params] body."
  [args]
  (let [[docstring args] (if (string? (first args))
                           [(first args) (rest args)]
                           [nil args])
        [attr-map args]  (if (map? (first args))
                           [(first args) (rest args)]
                           [nil args])
        [params & body]  args]
    [docstring attr-map params body]))

(defmacro defjob
  "Defines a background job handler.

  Creates a var holding the namespaced job type keyword and registers a method
  on the [[handle-job]] multimethod. The job type keyword is derived from the
  var name and the current namespace.

  Follows `defn` conventions for optional docstring and attr-map.

  The attr-map supports:
    :job/derives - Vector of parent keywords for hierarchy (via `derive`)

  Usage:
  ```clojure
  (defjob my-job
    \"Optional docstring.\"
    {:job/derives [::some-category]}
    [payload]
    (process payload))
  ```

  Generates:
  - `(def my-job ::my-job)` - var holding the job type keyword
  - `(defmethod handle-job ::my-job ...)` - multimethod registration
  - `(derive ::my-job ::some-category)` - for each parent in :job/derives"
  {:arglists '([name docstring? attr-map? [params] & body])}
  [name & args]
  (let [[docstring attr-map params body] (parse-defjob-args args)
        derives                          (:job/derives attr-map)
        ;; params is a vector like [{:keys [x y]}], extract the binding form
        payload-binding                  (first params)
        ;; Create namespaced keyword using the current namespace
        job-type                         (keyword (str *ns*) (str name))]
    `(do
       (def ~name ~@(when docstring [docstring]) ~job-type)
       (defmethod handle-job ~job-type [~'_job-type ~payload-binding]
         ~@body)
       ~@(for [parent derives]
           `(derive ~job-type ~parent))
       ~job-type)))
