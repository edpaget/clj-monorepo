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

Our solution uses JobRunr's **JobRequest/JobRequestHandler pattern** combined with a **custom classloader** that enables Clojure's dynamically-generated classes to be found by JobRunr's worker threads. This eliminates the need for AOT compilation.

### How It Works

1. **At namespace load time**:
   - `deftype` creates `ClojureJobRequest` (implements `JobRequest`)
   - `deftype` creates `ClojureJobRequestHandler` (implements `JobRequestHandler`)
   - These classes exist in Clojure's `DynamicClassLoader`
   - The `defjob` macro creates a var holding the job keyword and registers a `defmethod` on `handle-job`

2. **When the JobRunr server starts**:
   - A composite classloader is created that delegates to Clojure's `DynamicClassLoader`
   - A custom `BackgroundJobServerWorkerPolicy` configures virtual thread workers with this classloader
   - Worker threads can now load Clojure-generated classes via `Class.forName()`

3. **When enqueueing**, the library:
   - Serializes the job type and payload as EDN
   - Creates a `ClojureJobRequest` instance with the EDN
   - Uses `JobBuilder` to set a human-readable job name (e.g., "my.app/send-email")
   - JobRunr serializes the request as JSON and stores it

4. **When executing**, JobRunr:
   - Deserializes the `ClojureJobRequest` from JSON
   - Instantiates `ClojureJobRequestHandler` via reflection
   - Calls `handler.run(request)` which dispatches to `handle-job` multimethod
   - The multimethod dispatches to the appropriate Clojure handler

### Why This Works Without AOT

JobRunr uses `Class.forName(className, true, contextClassLoader)` to load classes. By default, worker threads use the system classloader which can't see Clojure's dynamically-generated classes.

Our solution:
1. **Composite ClassLoader**: Wraps Clojure's `DynamicClassLoader` and delegates to it for class lookups
2. **Custom ThreadFactory**: Sets the composite classloader as the context classloader on worker threads
3. **Custom WorkerPolicy**: Configures JobRunr to use Java 21+ virtual threads with our ThreadFactory
4. **Virtual Threads**: Uses `Executors.newThreadPerTaskExecutor()` for efficient per-job thread allocation

This allows `deftype` classes to be found without AOT compilation.

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

- A var `send-email` holding the namespaced keyword `::send-email`
- A `defmethod` on `handle-job` for `::send-email` with the job body

The generated var enables standard Clojure tooling:

```clojure
(doc send-email)
;; => "Sends a welcome email to a new user."

;; Test via multimethod dispatch
(handle-job send-email {:user-id 123 :email "test@example.com" :template :welcome})

;; Or with the keyword directly
(handle-job ::send-email {:user-id 123 :email "test@example.com"})
```

## Architecture

### Module Structure

```
clj-jobrunr/
├── src/clj_jobrunr/
│   ├── serialization.clj   # EDN serialization with custom readers
│   ├── job.clj             # defjob macro, handle-job multimethod
│   ├── bridge.clj          # Job EDN creation and execution
│   ├── request.clj         # ClojureJobRequest/Handler deftypes
│   ├── classloader.clj     # Composite classloader, ThreadFactory
│   ├── worker_policy.clj   # Virtual thread worker policy
│   ├── core.clj            # Public API: enqueue!, schedule!, etc.
│   └── integrant.clj       # Lifecycle components
└── deps.edn
```

### Key Components

- **serialization**: Configurable EDN read/write with custom tagged literal support
- **job**: The `defjob` macro and `handle-job` multimethod for dispatch
- **bridge**: Utilities for creating job EDN and executing handlers
- **request**: `deftype` implementations of `JobRequest` and `JobRequestHandler`
- **classloader**: Composite classloader and ThreadFactory for dynamic class loading
- **worker_policy**: Virtual thread executor and worker policy for JobRunr
- **core**: Public API (`enqueue!`, `schedule!`, `recurring!`, `delete-recurring!`)
- **integrant**: Integrant components for lifecycle management

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

### Single Bridge Class with Custom Job Names

The implementation uses a single pair of classes (`ClojureJobRequest` and `ClojureJobRequestHandler`) for all jobs, but leverages JobRunr's `JobBuilder` API to set custom job names that appear in the dashboard.

```clojure
;; When enqueueing:
(enqueue! send-email {:to "user@example.com"})

;; Creates a job with:
;; - Name: "my.app/send-email" (shown in dashboard)
;; - Labels: ["clj-jobrunr"] (optional, for filtering)
;; - Request: ClojureJobRequest with EDN payload
```

This gives the best of both worlds:
- **Simple implementation**: One pair of classes, no per-job code generation
- **Dashboard visibility**: Human-readable job names via `JobBuilder.withName()`
- **Filtering support**: Optional labels via `JobBuilder.withLabels()`

### No AOT Compilation Required

Unlike traditional approaches using `gen-class`, this library uses `deftype` to create the bridge classes at namespace load time. Combined with a custom classloader that makes these classes visible to JobRunr's worker threads, **no AOT compilation is needed**.

Simply require the namespace and start using:

```clojure
(require '[clj-jobrunr.core :as jobrunr])
(jobrunr/enqueue! send-email {:to "user@example.com"})
```

The classloader magic happens automatically when the Integrant server component starts.

### Virtual Threads for Workers

The library uses Java 21+ virtual threads for job execution. This provides:

- **Efficient I/O**: Virtual threads park during blocking I/O without consuming OS threads
- **High concurrency**: Can handle many concurrent jobs without thread pool exhaustion
- **Simple model**: One thread per job, no callback complexity

The worker policy creates a `ThreadPerTaskExecutor` backed by virtual threads, with each thread's context classloader set to our composite classloader.

### Multimethod Dispatch

Job handlers are implemented as methods on the `handle-job` multimethod, dispatching on namespaced job type keywords:

```clojure
(defmulti handle-job
  "Dispatches job execution by job type keyword."
  (fn [job-type payload] job-type))

;; The defjob macro expands to a def + defmethod
(def send-email
  "Sends an email."
  ::send-email)

;; Note: namespaced keyword, job body is inline
(defmethod handle-job ::send-email [_ {:keys [to subject body]}]
  (email/send! to subject body))
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
  :poll-interval 15
  :worker-count 4}}
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

### Enqueueing Jobs

The `core` module provides the public API for job operations:

```clojure
(require '[clj-jobrunr.core :as jobrunr])

;; Immediate execution - use the var or keyword
(jobrunr/enqueue! send-email {:to "user@example.com"})
(jobrunr/enqueue! ::send-email {:to "user@example.com"})
;; => #uuid "550e8400-e29b-41d4-a716-446655440000"

;; Scheduled execution - Duration or Instant
(jobrunr/schedule! send-email {:to "..."} (Duration/ofHours 1))
(jobrunr/schedule! send-email {:to "..."} (Instant/parse "2024-01-15T10:00:00Z"))

;; Recurring (cron) - requires a unique job ID
(jobrunr/recurring! "daily-digest" send-digest {:template :digest} "0 9 * * *")

;; Delete recurring job
(jobrunr/delete-recurring! "daily-digest")
```

### Advanced Options

For more control, use the options map:

```clojure
;; Custom job name (shown in dashboard)
(jobrunr/enqueue! send-email {:to "user@example.com"}
  {:name "Send welcome to user@example.com"})

;; With labels (up to 3, for filtering in dashboard)
(jobrunr/enqueue! send-email {:to "user@example.com"}
  {:labels ["email" "onboarding"]})

;; Both
(jobrunr/enqueue! process-order {:order-id 123}
  {:name "Process order #123"
   :labels ["orders" "priority"]})
```

### Testing Handlers

Job handlers can be tested via the `handle-job` multimethod:

```clojure
;; Test via dispatch - using the var
(handle-job send-email {:to "test@example.com" :subject "Test"})

;; Or using the keyword directly
(handle-job ::send-email {:to "test@example.com" :subject "Test"})

;; Check if handler exists
(contains? (methods handle-job) send-email)
```

## Testing Infrastructure

The library includes comprehensive test coverage with both unit and integration tests.

### Unit Tests (89 tests)

Unit tests run without external dependencies and cover:

- **Serialization**: EDN round-trips, custom tagged literals, java.time types
- **Job definition**: `defjob` macro expansion, docstrings, hierarchies
- **Multimethod dispatch**: Handler registration, hierarchy dispatch, default handlers
- **Request/Handler types**: Interface implementation, class loading, JSON serialization
- **Worker policy**: Virtual thread creation, classloader propagation
- **Core API**: Job builder construction, option handling

### Integration Tests (6 tests)

Integration tests use Testcontainers to spin up isolated PostgreSQL instances:

- **enqueue-executes-job-test**: Immediate job execution
- **schedule-executes-at-time-test**: Delayed execution
- **recurring-executes-on-schedule-test**: Cron scheduling
- **failed-job-retries-test**: Retry on failure with exponential backoff
- **custom-serialization-roundtrip-test**: java.time serialization end-to-end
- **job-survives-restart-test**: Persistence across server restarts

### Test Utilities

The `test-utils` namespace provides:

- `postgres-fixture` - Starts PostgreSQL Testcontainer
- `jobrunr-fixture` - Starts JobRunr server with 1-second poll interval
- `integration-fixture` - Combined fixture for integration tests
- `wait-for-job` - Polls until job reaches terminal state
- `job-status` - Queries job status from storage
- `restart-jobrunr!` - Restarts server while preserving database

### Running Tests

```bash
# Unit tests only (no Docker required)
clojure -X:test :excludes '[:integration]'

# Integration tests only (requires Docker)
clojure -X:test :includes '[:integration]'

# All tests
clojure -X:test
```

## Tradeoffs

### Custom ClassLoader Complexity

The library uses a custom classloader to enable dynamic class loading. While this eliminates AOT compilation, it adds complexity to the server initialization. The classloader is automatically configured by the Integrant component, so users don't need to manage it directly.

### Java 21+ Requirement

The library requires Java 21+ for virtual thread support. This provides significant benefits for I/O-bound jobs but limits compatibility with older JVMs.

### JobRunr Version

This library uses JobRunr 8.x which dropped Redis and Elasticsearch support. If you need those storage backends, you'll need to use an older JobRunr version.

### Payload Size Limits

EDN payloads are stored as strings in the database. Very large payloads should store references (IDs, S3 keys) rather than embedding data directly.

### Thread Context ClassLoader

Worker threads must have their context classloader set correctly for job execution. This is handled automatically when using the Integrant components. If you're manually configuring JobRunr, you'll need to use the custom `BackgroundJobServerWorkerPolicy` from this library.

## Future Considerations

- **Batch jobs**: JobRunr Pro supports batch processing
- **Job chaining**: Parent/child job relationships for workflows
- **Custom retry policies**: Per-job retry configuration via metadata
- **Metrics integration**: Hook into JobRunr's metrics for monitoring systems
- **Per-job classes**: Generate a class per job type (currently unnecessary due to JobBuilder names)
