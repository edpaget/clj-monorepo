# clj-jobrunr

A Clojure wrapper for [JobRunr](https://www.jobrunr.io/), providing idiomatic APIs for background job processing with PostgreSQL persistence and a built-in dashboard.

## Features

- **`defjob` macro** - Define jobs as regular Clojure functions
- **Multimethod dispatch** - Job handlers with hierarchy support
- **EDN serialization** - Human-readable payloads with custom tagged literal support
- **Integrant components** - Lifecycle management for JobRunr server
- **java.time support** - Built-in serialization for Instant, Duration, LocalDate
- **Virtual threads** - Java 21+ virtual thread execution for workers
- **No AOT required** - Uses deftype with custom classloader, no compilation step

## Requirements

- **Java 21+** (uses virtual threads)
- **PostgreSQL** (for job persistence)

## Installation

Add to your `deps.edn`:

```clojure
{:deps {com.github.your-org/clj-jobrunr {:local/root "../clj-jobrunr"}}}
```

## Quick Start

### 1. Define a Job

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

The `defjob` macro creates:
- A function `send-email` you can call directly
- A multimethod handler for `::send-email` (namespaced keyword)

### 2. Configure Integrant

```clojure
(require '[integrant.core :as ig])

(def config
  {:clj-jobrunr.integrant/serialization
   {:readers {'time/instant #(java.time.Instant/parse %)}}

   :clj-jobrunr.integrant/storage-provider
   {:datasource (ig/ref :your.app/datasource)}

   :clj-jobrunr.integrant/server
   {:storage-provider (ig/ref :clj-jobrunr.integrant/storage-provider)
    :serialization (ig/ref :clj-jobrunr.integrant/serialization)
    :dashboard? true
    :dashboard-port 8080
    :poll-interval 15
    :worker-count 4}})

(def system (ig/init config))
```

### 3. Enqueue Jobs

```clojure
(require '[clj-jobrunr.core :as jobrunr])

;; Immediate execution
(jobrunr/enqueue! ::send-email {:user-id 123 :email "user@example.com"})

;; Scheduled (in 1 hour)
(jobrunr/schedule! ::send-email {:user-id 123} (Duration/ofHours 1))

;; Scheduled (at specific time)
(jobrunr/schedule! ::send-email {:user-id 123} (Instant/parse "2026-06-15T10:00:00Z"))

;; Recurring (daily at 9 AM)
(jobrunr/recurring! "daily-digest" ::send-email {:template :digest} "0 9 * * *")

;; Delete recurring job
(jobrunr/delete-recurring! "daily-digest")
```

## API Reference

### Defining Jobs

```clojure
(defjob job-name
  "Optional docstring."
  {:job/derives [::optional-parent]}  ;; optional attr-map for hierarchies
  [payload]
  ;; job body
  )
```

The job type keyword is automatically namespaced: `::job-name` becomes `:my.namespace/job-name`.

### Job Options

All enqueue/schedule functions accept an optional options map:

```clojure
(jobrunr/enqueue! ::send-email {:to "user@example.com"}
  {:name "Send welcome to user@example.com"  ;; custom display name
   :labels ["email" "onboarding"]            ;; up to 3 labels for filtering
   :id #uuid "..."                           ;; custom UUID to prevent duplicates
   :retries 5})                              ;; number of retry attempts
```

### Job Hierarchies

Use `:job/derives` to create job type hierarchies:

```clojure
(defjob send-email
  {:job/derives [::notification]}
  [{:keys [to subject]}]
  (email/send! to subject))

(defjob send-sms
  {:job/derives [::notification]}
  [{:keys [phone message]}]
  (sms/send! phone message))

;; Fallback handler for all notifications
(defmethod handle-job ::notification [job-type payload]
  (log/info "Processing notification" job-type))
```

### Custom Serialization

The default serializer handles `java.time.Instant`, `Duration`, and `LocalDate` automatically:

```clojure
(require '[clj-jobrunr.serialization :as ser])

;; Default serializer handles java.time types
(def serializer (ser/default-serializer))

;; java.time types serialize as tagged literals
(ser/serialize serializer {:at (java.time.Instant/now)})
;; => "{:at #time/instant \"2026-01-15T10:30:00Z\"}"

;; Add custom types or exclude defaults
(ser/make-serializer
  {:writers {MyType (fn [v] (tagged-literal 'my/type (.toString v)))}
   :readers {'my/type #(MyType/parse %)}
   :exclude-writers [java.time.Duration]})  ; exclude Duration writer
```

### Testing Jobs

Jobs are regular functions, making them easy to test:

```clojure
;; Direct call
(send-email {:user-id 123 :email "test@example.com"})

;; Via multimethod
(require '[clj-jobrunr.job :refer [handle-job]])
(handle-job ::send-email {:user-id 123 :email "test@example.com"})

;; Check if handler exists
(contains? (methods handle-job) ::send-email)
```

## Integrant Components

### `:clj-jobrunr.integrant/serialization`

Creates an EDN serializer.

| Option | Description |
|--------|-------------|
| `:readers` | Map of tag symbols to reader functions |
| `:read-fn` | Custom read function (overrides `:readers`) |
| `:write-fn` | Custom write function (default: `pr-str`) |

### `:clj-jobrunr.integrant/storage-provider`

Creates a PostgresStorageProvider.

| Option | Description |
|--------|-------------|
| `:datasource` | JDBC DataSource (required) |

### `:clj-jobrunr.integrant/server`

Starts the JobRunr background job server with virtual thread workers.

| Option | Description | Default |
|--------|-------------|---------|
| `:storage-provider` | Storage provider (required) | - |
| `:serialization` | Serializer (required) | - |
| `:dashboard?` | Enable web dashboard | `false` |
| `:dashboard-port` | Dashboard port | `8000` |
| `:poll-interval` | Job poll interval (seconds) | `15` |
| `:worker-count` | Number of worker threads | Available processors |

## Running Tests

```bash
# Run unit tests (no Docker required)
clojure -X:test :excludes '[:integration]'

# Run integration tests (requires Docker for Testcontainers)
clojure -X:test :includes '[:integration]'

# Run all tests
clojure -X:test
```

The test suite includes:
- **89 unit tests** - Test job definition, serialization, multimethod dispatch
- **6 integration tests** - Test full job lifecycle with PostgreSQL via Testcontainers

## Build Tasks

```bash
# Build a JAR
clojure -T:build jar

# Build an uberjar
clojure -T:build uber

# Clean build artifacts
clojure -T:build clean
```

## Project Structure

```
clj-jobrunr/
├── src/clj_jobrunr/
│   ├── serialization.clj   # EDN serialization
│   ├── job.clj             # defjob macro, handle-job multimethod
│   ├── bridge.clj          # Job class name generation, execute!
│   ├── request.clj         # ClojureJobRequest/Handler deftypes
│   ├── classloader.clj     # Custom classloader for worker threads
│   ├── worker_policy.clj   # Virtual thread worker policy
│   ├── core.clj            # Public API (enqueue!, schedule!, recurring!)
│   └── integrant.clj       # Lifecycle components
├── test/clj_jobrunr/
│   ├── *_test.clj          # Unit tests
│   ├── integration_test.clj # Integration tests (Testcontainers)
│   └── test_utils.clj      # Test fixtures and utilities
├── build.clj               # Build tasks
└── deps.edn
```

## How It Works

Unlike typical Java-based JobRunr usage, this library doesn't require AOT compilation. Instead:

1. `deftype` creates `ClojureJobRequest` and `ClojureJobRequestHandler` at namespace load time
2. A composite classloader makes these classes visible to JobRunr's worker threads
3. Virtual threads execute jobs with the correct context classloader set
4. The `handle-job` multimethod dispatches to the appropriate Clojure handler

See [DESIGN.md](DESIGN.md) for detailed architecture documentation.

## License

Copyright © 2026

Distributed under the [GNU Affero General Public License v3.0](../LICENSE) (AGPLv3).
