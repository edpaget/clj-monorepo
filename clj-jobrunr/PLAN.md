# Implementation Plan

This plan follows Test-Driven Development: write failing tests first, then implement to make them pass.

## Phase 1: Core Library (Unit Tests)

### 1.1 Serialization Module

**Namespace**: `clj-jobrunr.serialization`

**Tests** (`clj-jobrunr.serialization-test`):
- `serialize-simple-map` - round-trips a basic map through write/read
- `serialize-with-standard-readers` - handles `#inst` and `#uuid` tagged literals
- `serialize-with-custom-readers` - custom reader for `#time/instant` works
- `serialize-with-custom-write-fn` - custom write function is used
- `default-serialization` - works without any configuration

**Implementation**:
- `(defn make-serializer [{:keys [readers read-fn write-fn]}])` - returns `{:read-fn :write-fn}`
- `(defn default-serializer [])` - returns default serializer
- `(defn serialize [serializer data])` - calls write-fn
- `(defn deserialize [serializer s])` - calls read-fn
- `(defn install-time-print-methods! [])` - installs print-method for java.time types

### 1.2 Multimethod and Handler Generation

**Namespace**: `clj-jobrunr.job`

**Tests** (`clj-jobrunr.job-test`):
- `defjob-creates-function` - macro creates a callable function with correct name
- `defjob-function-has-docstring` - generated function has docstring metadata
- `defjob-registers-multimethod` - handler is registered on `handle-job`
- `defjob-multimethod-dispatches` - `handle-job` routes to correct handler
- `defjob-without-docstring` - works when docstring is omitted
- `handle-job-default` - `:default` method handles unknown job types
- `handle-job-hierarchy` - `derive` hierarchies work for job type grouping

**Implementation**:
- `(defmulti handle-job (fn [job-type payload] job-type))`
- `(defmethod handle-job :default ...)` - throws or logs unknown job type
- `(defmacro defjob [name & args])` - generates defn + defmethod

### 1.3 Job Class Generation

**Namespace**: `clj-jobrunr.bridge`

**Tests** (`clj-jobrunr.bridge-test`):
- `job-class-name` - converts job keyword to Java class name correctly
- `job-edn-format` - job EDN includes job-type and payload
- `execute-deserializes-and-dispatches` - bridge execute fn calls handle-job

**Implementation**:
- `(defn job-class-name [job-kw])` - `:send-email` → `"clj_jobrunr.jobs.SendEmail"`
- `(defn job-edn [job-type payload])` - creates the EDN string for storage
- `(defn execute! [job-edn])` - deserializes and calls handle-job

Note: Actual gen-class testing is deferred to integration tests since it requires AOT.

### 1.4 Enqueue API (Mocked)

**Namespace**: `clj-jobrunr.enqueue`

**Tests** (`clj-jobrunr.enqueue-test`):
- `enqueue!-creates-job-lambda` - constructs correct lambda for JobRunr
- `schedule!-with-instant` - accepts Instant for scheduling
- `schedule!-with-duration` - accepts Duration, converts to Instant
- `recurring!-with-cron` - accepts cron expression and job-id

**Implementation** (interfaces only, actual JobRunr calls mocked):
- `(defn enqueue! [job-type payload])`
- `(defn schedule! [job-type payload time])`
- `(defn recurring! [job-id job-type cron payload])`
- `(defn delete-recurring! [job-id])`

---

## Phase 2: Integrant Components (Unit Tests)

**Namespace**: `clj-jobrunr.integrant`

**Tests** (`clj-jobrunr.integrant-test`):
- `serialization-component-default` - creates default serializer when no config
- `serialization-component-custom-readers` - merges custom readers
- `storage-provider-component` - creates PostgresStorageProvider from datasource (mocked)
- `server-component-lifecycle` - init starts server, halt stops it (mocked)

**Implementation**:
- `(defmethod ig/init-key ::serialization [_ opts])`
- `(defmethod ig/init-key ::storage-provider [_ {:keys [datasource]}])`
- `(defmethod ig/init-key ::server [_ opts])`
- `(defmethod ig/halt-key! ::server [_ server])`

---

## Phase 3: Integration Tests

These tests require a real PostgreSQL database and actually run JobRunr.

### 3.1 Test Infrastructure

**Namespace**: `clj-jobrunr.test-utils`

- Docker-based PostgreSQL for CI (or use existing test DB)
- Helper to start/stop JobRunr server for tests
- Helper to wait for job completion
- Helper to inspect job status via JobRunr API

```clojure
(defn with-jobrunr [f]
  ;; Fixture that starts JobRunr, runs test, stops JobRunr
  )

(defn wait-for-job [job-id & {:keys [timeout-ms] :or {timeout-ms 5000}}]
  ;; Polls until job completes or times out
  )

(defn job-status [job-id]
  ;; Returns :enqueued, :processing, :succeeded, :failed
  )
```

### 3.2 Integration Test Cases

**Namespace**: `clj-jobrunr.integration-test`

**Setup**: Define test jobs that record their execution to an atom

```clojure
(def executions (atom []))

(defjob test-job
  "A test job that records its execution."
  [payload]
  (swap! executions conj {:job-type :test-job :payload payload :at (Instant/now)}))
```

**Tests**:

- `enqueue-executes-job`
  - Enqueue a job
  - Wait for completion
  - Verify execution was recorded

- `schedule-executes-at-time`
  - Schedule job for 2 seconds in future
  - Verify not executed immediately
  - Wait and verify executed after delay

- `recurring-executes-on-schedule`
  - Schedule recurring job with short interval (every 2 seconds)
  - Wait for multiple executions
  - Delete recurring job
  - Verify no more executions

- `failed-job-retries`
  - Define job that fails first N times, then succeeds
  - Enqueue and wait
  - Verify retries happened and job eventually succeeded

- `custom-serialization-roundtrip`
  - Configure custom reader for `#time/instant`
  - Enqueue job with Instant in payload
  - Verify handler receives correct Instant value

- `gen-class-serialization`
  - Verify JobRunr can serialize/deserialize the generated class
  - Restart JobRunr server
  - Verify pending job still executes (survives restart)

### 3.3 Dashboard Verification (Manual)

- Start server with dashboard enabled
- Navigate to `http://localhost:8080`
- Verify jobs appear with readable EDN payloads
- Verify job details show correct class names

---

## Phase 4: Macro Polish and Edge Cases

**Tests** (`clj-jobrunr.job-test` additions):
- `defjob-with-destructuring` - complex destructuring in params works
- `defjob-arglists-metadata` - function has correct `:arglists` metadata
- `defjob-multiple-jobs` - multiple defjobs in same namespace work
- `defjob-namespaced-keyword` - job type can be namespaced keyword

---

## Implementation Order

```
1. clj-jobrunr.serialization      (tests → impl)
2. clj-jobrunr.job                (tests → impl)
3. clj-jobrunr.bridge             (tests → impl)
4. clj-jobrunr.enqueue            (tests → impl, mocked)
5. clj-jobrunr.integrant          (tests → impl, mocked)
6. Integration test infrastructure
7. Integration tests (with real JobRunr)
8. AOT compilation setup + gen-class
9. Edge cases and polish
```

---

## File Structure

```
clj-jobrunr/
├── deps.edn
├── build.clj
├── DESIGN.md
├── PLAN.md
├── src/
│   └── clj_jobrunr/
│       ├── core.clj          # Public API, re-exports
│       ├── serialization.clj
│       ├── job.clj           # defjob, handle-job
│       ├── bridge.clj        # Java interop, gen-class
│       ├── enqueue.clj       # enqueue!, schedule!, etc.
│       └── integrant.clj     # Integrant components
├── test/
│   └── clj_jobrunr/
│       ├── serialization_test.clj
│       ├── job_test.clj
│       ├── bridge_test.clj
│       ├── enqueue_test.clj
│       ├── integrant_test.clj
│       ├── integration_test.clj
│       └── test_utils.clj
└── dev/
    └── user.clj
```

---

## Success Criteria

### Unit Tests
- [ ] All serialization round-trips work
- [ ] `defjob` generates function with docstring
- [ ] `defjob` registers multimethod handler
- [ ] `handle-job` dispatches correctly
- [ ] Hierarchy dispatch works
- [ ] Enqueue functions create correct structures

### Integration Tests
- [ ] Jobs enqueue and execute
- [ ] Scheduled jobs execute at correct time
- [ ] Recurring jobs execute on schedule
- [ ] Failed jobs retry
- [ ] Custom serialization works end-to-end
- [ ] Jobs survive server restart
- [ ] Dashboard shows jobs correctly
