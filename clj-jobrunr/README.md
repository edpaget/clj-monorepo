# clj-jobrunr

A Clojure wrapper for [JobRunr](https://www.jobrunr.io/), providing idiomatic APIs for background job processing with PostgreSQL persistence and a built-in dashboard.

## Features

- **`defjob` macro** - Define jobs as regular Clojure functions
- **Multimethod dispatch** - Job handlers with hierarchy support
- **EDN serialization** - Human-readable payloads with custom tagged literal support
- **Integrant components** - Lifecycle management for JobRunr server
- **java.time support** - Built-in serialization for Instant, Duration, LocalDate

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

### 2. Build the Bridge Class

JobRunr requires a Java class it can serialize. Build with:

```bash
clojure -T:build compile-bridge
```

This AOT-compiles `clj_jobrunr.ClojureBridge` to `target/classes/`.

### 3. Configure Integrant

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
    :poll-interval 15}})

(def system (ig/init config))
```

### 4. Enqueue Jobs

```clojure
(require '[clj-jobrunr.core :as jobrunr])

;; Immediate execution
(jobrunr/enqueue! ::send-email {:user-id 123 :email "user@example.com"})

;; Scheduled (in 1 hour)
(jobrunr/schedule! ::send-email {:user-id 123} (Duration/ofHours 1))

;; Scheduled (at specific time)
(jobrunr/schedule! ::send-email {:user-id 123} (Instant/parse "2024-06-15T10:00:00Z"))

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
;; => "{:at #time/instant \"2024-01-15T10:30:00Z\"}"

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

Starts the JobRunr background job server.

| Option | Description | Default |
|--------|-------------|---------|
| `:storage-provider` | Storage provider (required) | - |
| `:serialization` | Serializer (required) | - |
| `:dashboard?` | Enable web dashboard | `false` |
| `:dashboard-port` | Dashboard port | `8000` |
| `:poll-interval` | Job poll interval (seconds) | `15` |

## Build Tasks

```bash
# AOT compile the bridge class (required before running)
clojure -T:build compile-bridge

# Build a JAR
clojure -T:build jar

# Build an uberjar
clojure -T:build uber

# Clean build artifacts
clojure -T:build clean
```

## Running Tests

```bash
# Run all tests (requires AOT compilation first)
clojure -T:build compile-bridge
clojure -X:test
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
├── build.clj               # Build tasks
└── deps.edn
```

## Current Limitations

- **Job display names**: All jobs use the same `ClojureJobRequest` class in JobRunr, but custom names can be set via the `:name` option
- **Java 21+ required**: Uses virtual threads for worker execution

## License

Copyright © 2024

Distributed under the Eclipse Public License version 1.0.
