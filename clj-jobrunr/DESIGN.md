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

Our solution uses a bridge class generated via `gen-class` that JobRunr can serialize. The `defjob` macro registers Clojure handlers on a multimethod, and the bridge class dispatches to these handlers at runtime.

### How It Works

1. **At compile time**:
   - AOT compilation generates `clj_jobrunr.ClojureBridge` class with static `run(String)` method
   - The `defjob` macro creates a function and registers a `defmethod` on `handle-job`

2. **When enqueueing**, the library:
   - Serializes the job type and payload as EDN
   - Creates a job request referencing `ClojureBridge.run(edn)`
   - JobRunr serializes this method reference

3. **When executing**, JobRunr:
   - Deserializes the job and invokes `ClojureBridge.run(edn)`
   - The bridge deserializes EDN and calls `handle-job` multimethod
   - The multimethod dispatches to the appropriate handler

### Example

```clojure
(ns my.app.jobs
  (:require [clj-jobrunr.job :refer [defjob]]))

(defjob send-email
  "Sends a welcome email to a new user."
  [{:keys [user-id email template]}]
  (let [user (db/get-user user-id)
        content (templates/render template user)]
    (mailer/send! email content)))
```

This generates:

- A function `send-email` with the docstring, for direct invocation and testing
- A `defmethod` on `handle-job` for `::send-email` (namespaced keyword)

The generated function enables standard Clojure tooling:

```clojure
(doc send-email)
;; => "Sends a welcome email to a new user."

;; Direct invocation for unit testing
(send-email {:user-id 123 :email "test@example.com" :template :welcome})

;; Via multimethod (note: namespaced keyword)
(handle-job ::send-email {:user-id 123 :email "test@example.com"})
```

## Architecture

### Module Structure

```
clj-jobrunr/
├── src/clj_jobrunr/
│   ├── serialization.clj   # EDN serialization with custom readers
│   ├── job.clj             # defjob macro, handle-job multimethod
│   ├── bridge.clj          # Job class name generation, EDN creation
│   ├── enqueue.clj         # Job request creation functions
│   ├── integrant.clj       # Lifecycle components
│   └── java_bridge.clj     # AOT gen-class for ClojureBridge
├── build.clj               # Build tasks for AOT compilation
└── deps.edn
```

### Key Components

- **serialization**: Configurable EDN read/write with custom tagged literal support
- **job**: The `defjob` macro and `handle-job` multimethod for dispatch
- **bridge**: Utilities for converting job keywords to class names and creating EDN
- **enqueue**: Functions to create job request maps for JobRunr
- **integrant**: Integrant components for lifecycle management
- **java_bridge**: AOT-compiled Java class that JobRunr can serialize

## Key Design Decisions

### EDN for Payload Serialization

Job payloads are serialized as EDN strings rather than using Java serialization or JSON. This keeps payloads readable in the JobRunr dashboard, works naturally with Clojure data structures, and avoids the complexity of custom serializers.

### Configurable EDN Reader/Writer

Users can provide custom serialization functions to support tagged literals like `#inst`, `#uuid`, or application-specific types like `#time/instant` for java.time.

```clojure
;; Integrant config with custom EDN handling
{:clj-jobrunr.integrant/serialization
 {:readers {'time/instant #(java.time.Instant/parse %)
            'time/duration #(java.time.Duration/parse %)}}

 :clj-jobrunr.integrant/server
 {:serialization (ig/ref :clj-jobrunr.integrant/serialization)
  ...}}
```

By default, the serializer handles `java.time.Instant`, `java.time.Duration`, and `java.time.LocalDate` as tagged literals without requiring global state modifications:

```clojure
(require '[clj-jobrunr.serialization :as ser])

(def s (ser/default-serializer))

;; java.time objects serialize as tagged literals automatically
(ser/serialize s {:scheduled-at (java.time.Instant/now)})
;; => "{:scheduled-at #time/instant \"2024-01-15T10:30:00Z\"}"

;; And deserialize back
(ser/deserialize s "{:at #time/instant \"2024-01-15T10:30:00Z\"}")
;; => {:at #inst "2024-01-15T10:30:00Z"} (as java.time.Instant)
```

Custom readers/writers can be added or defaults can be excluded:

```clojure
;; Add custom writer for a type
(ser/make-serializer
  {:writers {MyType (fn [v] (tagged-literal 'my/type (.toString v)))}
   :readers {'my/type #(MyType/parse %)}})

;; Exclude default time writers
(ser/make-serializer {:exclude-writers [java.time.Instant]})
```

### Single Bridge Class (Current Implementation)

The current implementation uses a single `clj_jobrunr.ClojureBridge` class for all jobs. This simplifies AOT compilation—only one class needs to be generated. The tradeoff is that all jobs appear as `ClojureBridge` in the JobRunr dashboard rather than showing individual job names like `SendEmail`.

**Future Enhancement**: Per-job class generation would show job names in the dashboard. This would require either:
- Build-time code generation to create a namespace per job type
- Runtime bytecode generation (adds complexity)

### AOT Compilation Requirement

The bridge class requires AOT compilation since `gen-class` generates classes at compile time. Build with:

```bash
clojure -T:build compile-bridge
```

This is an acceptable tradeoff for most applications where job types are known at build time.

### Multimethod Dispatch

Job handlers are implemented as methods on the `handle-job` multimethod, dispatching on namespaced job type keywords:

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

**Introspection**: List all registered handlers with `(keys (methods handle-job))`.

**Hierarchy support**: Use the `:job/derives` attr-map to create job type hierarchies:

```clojure
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

### Integrant Integration

The library provides Integrant components for lifecycle management:

```clojure
{:clj-jobrunr.integrant/serialization
 {:readers {'time/instant #(java.time.Instant/parse %)}}

 :clj-jobrunr.integrant/storage-provider
 {:datasource (ig/ref :datasource/postgres)}

 :clj-jobrunr.integrant/server
 {:storage-provider (ig/ref :clj-jobrunr.integrant/storage-provider)
  :serialization (ig/ref :clj-jobrunr.integrant/serialization)
  :dashboard? true
  :dashboard-port 8080
  :poll-interval 15}}
```

## API Surface

### Defining Jobs

```clojure
(defjob job-name
  "Optional docstring."
  {:job/derives [::optional-parent]}  ;; optional attr-map
  [payload-binding]
  body...)
```

### Creating Job Requests

The `enqueue` module provides functions to create job request maps:

```clojure
(require '[clj-jobrunr.enqueue :as enqueue])

;; Immediate execution
(enqueue/make-job-request serializer ::send-email {:to "user@example.com"})
;; => {:job-type ::send-email, :payload {...}, :edn "...", :class-name "..."}

;; Scheduled execution
(enqueue/make-scheduled-request serializer ::send-email {:to "..."} (Duration/ofHours 1))
(enqueue/make-scheduled-request serializer ::send-email {:to "..."} (Instant/parse "2024-01-15T10:00:00Z"))

;; Recurring (cron)
(enqueue/make-recurring-request serializer "daily-digest" ::send-email "0 9 * * *" {:template :digest})

;; Delete recurring
(enqueue/make-delete-recurring-request "daily-digest")
```

### Testing Handlers

Each `defjob` generates a regular function, making handlers easy to test:

```clojure
;; Unit test - call the function directly
(send-email {:to "test@example.com" :subject "Test"})

;; Integration test - verify dispatch works
(handle-job ::send-email {:to "test@example.com" :subject "Test"})

;; Check if handler exists
(contains? (methods handle-job) ::send-email)
```

## Tradeoffs

### Requires AOT Compilation

The bridge class must be AOT-compiled before deployment. This adds a build step but is standard practice for production Clojure applications.

### JobRunr Version Constraints

JobRunr 8.x dropped Redis and Elasticsearch support. This library targets JobRunr 7.x for broader storage options.

### Payload Size Limits

EDN payloads are stored as strings in the database. Very large payloads should store references (IDs, S3 keys) rather than embedding data directly.

### Dashboard Job Names

With the single bridge class approach, all jobs appear as `ClojureBridge` in the dashboard. The EDN payload is still visible and readable.

## Future Considerations

- **Per-job classes**: Generate a class per job type for better dashboard display
- **Actual enqueue functions**: Add `enqueue!`, `schedule!`, `recurring!` that call JobRunr APIs
- **Batch jobs**: JobRunr Pro supports batch processing
- **Job chaining**: Parent/child job relationships for workflows
- **Custom retry policies**: Per-job retry configuration via metadata
- **Metrics integration**: Hook into JobRunr's metrics for monitoring systems
