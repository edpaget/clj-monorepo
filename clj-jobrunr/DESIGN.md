# clj-jobrunr Design Document

## Overview

clj-jobrunr is a Clojure wrapper for JobRunr, a mature Java background job processing library. It provides idiomatic Clojure APIs for defining, enqueueing, and scheduling background jobs while leveraging JobRunr's battle-tested infrastructure for persistence, retries, and monitoring.

## Motivation

The Clojure ecosystem lacks a PostgreSQL-backed job queue with a built-in dashboard. The current options are:

- **Goose**: Excellent library, but only supports Redis and RabbitMQ
- **Proletarian**: PostgreSQL-backed, but no dashboard and runs jobs inside transactions
- **Carmine MQ**: Redis-only, no dashboard

JobRunr fills this gap perfectly—it supports PostgreSQL (and other RDBMS), includes a polished web dashboard, handles retries with exponential backoff, and has been battle-tested in production Java applications. The challenge is that JobRunr relies on Java lambda serialization via bytecode inspection, which doesn't work directly with Clojure functions.

## Approach

The core insight is that JobRunr serializes jobs by inspecting lambda bytecode to extract a static method reference and its arguments. Clojure functions, while implementing Java functional interfaces, don't produce bytecode that JobRunr can meaningfully serialize.

Our solution uses a `defjob` macro that generates a Java class (via `gen-class`) for each job type. Each generated class has a static method that JobRunr can serialize. When JobRunr invokes the method, it dispatches to the registered Clojure handler function.

### How It Works

1. **At compile time**, the `defjob` macro:
   - Generates a Java class with a static `run(String)` method
   - Defines a `defmethod` on the `handle-job` multimethod
   - Creates helper functions for enqueueing and scheduling

2. **When enqueueing**, the library:
   - Serializes the job payload as EDN
   - Creates a lambda that calls the generated static method with the EDN string
   - JobRunr serializes this as a method reference it understands

3. **When executing**, JobRunr:
   - Deserializes the job and invokes the static method
   - The static method calls `handle-job` multimethod with job type and payload
   - The multimethod dispatches to the appropriate handler

### Example

```clojure
(defjob send-email
  "Sends a welcome email to a new user."
  [{:keys [user-id email template]}]
  (let [user (db/get-user user-id)
        content (templates/render template user)]
    (mailer/send! email content)))
```

This generates:

- A function `send-email` with the docstring, for direct invocation and testing
- A `defmethod` on `handle-job` for `::send-email` (namespaced keyword) that delegates to the function
- A class `clj_jobrunr.jobs.SendEmail` with static method `run(String)`
- A function `send-email!` for immediate enqueueing
- A function `schedule-send-email!` for delayed execution
- A function `recurring-send-email!` for cron-based scheduling

The generated function enables standard Clojure tooling:

```clojure
(doc send-email)
;; => "Sends a welcome email to a new user."

;; Direct invocation for unit testing
(send-email {:user-id 123 :email "test@example.com" :template :welcome})

;; Via multimethod (note: namespaced keyword)
(handle-job ::send-email {:user-id 123 :email "test@example.com"})
```

## Key Design Decisions

### EDN for Payload Serialization

Job payloads are serialized as EDN strings rather than using Java serialization or JSON. This keeps payloads readable in the JobRunr dashboard, works naturally with Clojure data structures, and avoids the complexity of custom serializers.

### Configurable EDN Reader/Writer

Users can provide custom serialization functions to support tagged literals like `#inst`, `#uuid`, or application-specific types like `#time/instant` for java.time. The library stores configured reader/writer functions in an atom that the execution path consults during deserialization.

```clojure
;; Integrant config with custom EDN handling
{:clj-jobrunr.core/serialization
 {:read-fn (fn [s] (edn/read-string {:readers my-readers} s))
  :write-fn (fn [x] (pr-str x))}  ;; or use a custom print-method setup

 :clj-jobrunr.core/server
 {:serialization #ig/ref :clj-jobrunr.core/serialization
  ...}}
```

By default, the library uses `clojure.edn/read-string` with standard readers and `pr-str` for writing. The `:readers` option can be set to merge additional tagged literal handlers:

```clojure
;; Example: java.time support
(def time-readers
  {'time/instant #(java.time.Instant/parse %)
   'time/duration #(java.time.Duration/parse %)
   'time/local-date #(java.time.LocalDate/parse %)})

{:clj-jobrunr.core/serialization
 {:readers time-readers}}
```

For writing, users can either configure `print-method` multimethods globally or provide a custom `:write-fn`. The library provides a helper for common java.time types:

```clojure
(require '[clj-jobrunr.serialization :as ser])

;; Install print-methods for java.time types
(ser/install-time-print-methods!)

;; Now java.time objects serialize as tagged literals
(pr-str {:scheduled-at (Instant/now)})
;; => "{:scheduled-at #time/instant \"2024-01-15T10:30:00Z\"}"
```

### One Class Per Job Type

Each `defjob` creates a dedicated Java class rather than using a single bridge class with job-type discriminator. This provides cleaner organization in the JobRunr dashboard—you see `SendEmail` rather than `Bridge.execute("send-email", ...)`. It also enables future per-job configuration like custom retry policies.

### AOT Compilation Requirement

The macro approach requires AOT compilation since `gen-class` generates classes at compile time. This is an acceptable tradeoff for most applications where job types are known at build time. The alternative—runtime bytecode generation—adds significant complexity.

### Multimethod Dispatch

Job handlers are implemented as methods on the `handle-job` multimethod, dispatching on namespaced job type keywords. This is idiomatic Clojure and provides several advantages over an atom-based registry:

```clojure
(defmulti handle-job
  "Dispatches job execution by job type keyword."
  (fn [job-type payload] job-type))

;; The defjob macro expands to a defn + defmethod
(defn send-email
  "Sends an email."
  [{:keys [to subject body]}]
  (email/send! to subject body))

;; Note: namespaced keyword based on defining namespace
(defmethod handle-job ::send-email [_ payload]
  (send-email payload))
```

**REPL and testing**: Handlers are directly callable as regular functions:

```clojure
;; Direct function call (preferred for unit tests)
(send-email {:to "test@example.com" :subject "Test"})

;; Via multimethod dispatch (note: namespaced keyword)
(handle-job ::send-email {:to "test@example.com" :subject "Test"})
```

**Introspection**: List all registered handlers with `(keys (methods handle-job))`.

**Hierarchy support**: Use the `:job/derives` attr-map to create job type hierarchies:

```clojure
;; Define jobs with hierarchy via attr-map
(defjob send-email
  "Sends an email notification."
  {:job/derives [::notification]}
  [{:keys [to subject]}]
  (email/send! to subject))

(defjob send-sms
  {:job/derives [::notification]}
  [{:keys [phone message]}]
  (sms/send! phone message))

;; Define a fallback handler for the parent category
(defmethod handle-job ::notification [job-type payload]
  (log/info "Processing notification" job-type))
```

Hierarchies enable patterns like fallback handlers, job categorization for monitoring, or shared pre/post processing logic for related job types. Jobs can derive from multiple parents by providing a vector: `{:job/derives [::notification ::async-job]}`.

### Integrant Integration

The library provides Integrant components for the storage provider and background job server. This fits naturally into Integrant-based applications and handles lifecycle management cleanly.

## API Surface

### Defining Jobs

```clojure
(defjob job-name
  "Optional docstring."
  {:job/derives [::optional-parent]}  ;; optional attr-map
  [payload-binding]
  body...)
```

### Testing Handlers

Each `defjob` generates a regular function, making handlers easy to test:

```clojure
;; Unit test - call the function directly
(send-email {:to "test@example.com" :subject "Test"})

;; Integration test - verify dispatch works (note: namespaced keyword)
(handle-job ::send-email {:to "test@example.com" :subject "Test"})

;; Check if handler exists
(contains? (methods handle-job) ::send-email)

;; Documentation
(doc send-email)
```

### Enqueueing

```clojure
;; Immediate execution
(send-email! {:user-id 123 :email "user@example.com"})

;; Scheduled execution
(schedule-send-email! {:user-id 123} (Duration/ofHours 1))
(schedule-send-email! {:user-id 123} (Instant/parse "2024-01-15T10:00:00Z"))

;; Recurring (cron)
(recurring-send-email! "daily-digest" "0 9 * * *" {:template :digest})
```

### Configuration

```clojure
;; Integrant config
{:clj-jobrunr.core/serialization
 {:readers {'time/instant #(java.time.Instant/parse %)
            'time/duration #(java.time.Duration/parse %)}}

 :clj-jobrunr.core/storage-provider
 {:datasource #ig/ref :datasource/postgres}

 :clj-jobrunr.core/server
 {:storage-provider #ig/ref :clj-jobrunr.core/storage-provider
  :serialization #ig/ref :clj-jobrunr.core/serialization
  :dashboard? true
  :dashboard-port 8080
  :poll-interval 15
  :worker-count 4}}
```

The `:serialization` component is optional. When omitted, the library uses standard EDN readers (`inst`, `uuid`) and `pr-str` for writing.

## Tradeoffs

### Requires AOT Compilation

Job namespaces must be AOT-compiled before deployment. This adds a build step but is standard practice for production Clojure applications. Dynamic job definition at runtime is not supported.

### JobRunr Version Constraints

JobRunr 8.x dropped Redis and Elasticsearch support. This library targets JobRunr 7.x for broader storage options or 8.x for PostgreSQL/MySQL-only deployments.

### Payload Size Limits

EDN payloads are stored as strings in the database. Very large payloads should store references (IDs, S3 keys) rather than embedding data directly.

## Future Considerations

- **Batch jobs**: JobRunr Pro supports batch processing; the wrapper could expose this
- **Job chaining**: Parent/child job relationships for workflows
- **Custom retry policies**: Per-job retry configuration via metadata
- **Metrics integration**: Hook into JobRunr's metrics for monitoring systems
