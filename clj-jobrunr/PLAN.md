# Implementation Plan

This plan follows Test-Driven Development: write failing tests first, then implement to make them pass.

## Status Overview

| Phase | Status | Tests |
|-------|--------|-------|
| Phase 1: Core Library | ✅ Complete | 43 |
| Phase 2: Integrant Components | ✅ Complete | 5 |
| Phase 3: Test Infrastructure | ✅ Complete | 4 |
| Phase 4: Edge Cases | ✅ Complete | 5 |
| Phase 5: AOT/gen-class | ✅ Complete | 4 |
| Phase 5.5: JobRunr 8.x Upgrade | ✅ Complete | - |
| Phase 6.1: ClassLoader Spike | ✅ Complete | 5 |
| Phase 6.2: JobRequest Types | ✅ Complete | 12 |
| Phase 6.3-6.6: Integration | ⏳ Not Started | - |
| **Total** | | **79 tests, 147 assertions** |

---

## Phase 5.5: JobRunr 8.x Upgrade ✅ COMPLETE

Upgraded from JobRunr 7.3.2 to 8.4.0.

**Changes**:
- Updated `deps.edn` to JobRunr 8.4.0
- Added Gson 2.11.0 dependency (JobRunr requires a JSON library)
- Updated `JobRunrDashboardWebServer` constructor to include `GsonJsonMapper`

**Breaking changes handled**:
- Dashboard constructor now requires `JsonMapper` parameter

**New capabilities available**:
- Carbon-aware job scheduling (EU only for now)
- Improved database performance
- Kotlin serialization support (if needed)

---

## Phase 1: Core Library (Unit Tests) ✅ COMPLETE

### 1.1 Serialization Module ✅

**Namespace**: `clj-jobrunr.serialization`

**Implemented**:
- `make-serializer` - creates serializer with custom readers/writers
- `default-serializer` - returns default serializer with java.time support
- `serialize` / `deserialize` - EDN round-trip
- `default-readers` / `default-writers` - built-in support for java.time types
- `*serializer*` - dynamic var for runtime access
- `:exclude-readers` / `:exclude-writers` options for customization

### 1.2 Multimethod and Handler Generation ✅

**Namespace**: `clj-jobrunr.job`

**Implemented**:
- `handle-job` multimethod with `:default` handler
- `defjob` macro generating function + defmethod
- Docstring support
- `:job/derives` attr-map for hierarchies
- Namespaced keyword dispatch

### 1.3 Bridge Module ✅

**Namespace**: `clj-jobrunr.bridge`

**Implemented**:
- `job-class-name` - converts keyword to Java class name
- `job-edn` - creates EDN string with job-type and payload
- `execute!` - deserializes and dispatches to handler

### 1.4 Enqueue API ✅

**Namespace**: `clj-jobrunr.enqueue`

**Implemented**:
- `make-job-request` - immediate execution request
- `make-scheduled-request` - scheduled execution (Instant or Duration)
- `make-recurring-request` - cron-based scheduling
- `make-delete-recurring-request` - delete recurring job

---

## Phase 2: Integrant Components ✅ COMPLETE

**Namespace**: `clj-jobrunr.integrant`

**Implemented**:
- `::serialization` - creates serializer from config
- `::storage-provider` - creates PostgresStorageProvider
- `::server` - starts/stops JobRunr server with dashboard

---

## Phase 3: Test Infrastructure ✅ COMPLETE

**Namespace**: `clj-jobrunr.test-utils`

**Implemented**:
- `job-status` - query job status from storage
- `wait-for-job` - poll until job completes
- `with-jobrunr-fixture` - test fixture for integration tests
- `with-test-serializer` - macro for binding test serializer

**Namespace**: `clj-jobrunr.integration-test`

**Implemented**:
- Test jobs (`test-simple-job`, `test-failing-job`)
- Unit tests for job execution without database
- Placeholder integration tests (require PostgreSQL)

---

## Phase 4: Edge Cases ✅ COMPLETE

**Added tests**:
- `defjob-nested-destructuring-test`
- `defjob-side-effect-only-test`
- `defjob-with-let-binding-test`
- `defjob-job-type-keyword-format-test`
- `defjob-exception-propagation-test`

---

## Phase 5: AOT Compilation ✅ COMPLETE

**Namespace**: `clj-jobrunr.java-bridge`

**Implemented**:
- `gen-class` for `clj_jobrunr.ClojureBridge`
- Static `run(String)` method for JobRunr
- `build.clj` with `compile-bridge` task

**Build command**: `clojure -T:build compile-bridge`

---

## Remaining Work

### Phase 6: Dynamic ClassLoader & JobRequest Pattern (Not Started)

This phase eliminates AOT compilation by using `deftype` with a custom classloader.

#### 6.1 Custom ClassLoader Module ✅ SPIKE COMPLETE

**Namespace**: `clj-jobrunr.classloader`

**Spike validated**:
- Composite classloader delegates to Clojure's `DynamicClassLoader`
- Custom `ThreadFactory` sets context classloader on worker threads
- `deftype` classes can be loaded via `Class.forName()` in worker threads

**Implementation**:
- `get-clojure-classloader` - returns appropriate `DynamicClassLoader`
- `make-composite-classloader` - creates classloader that delegates to Clojure's DL
- `make-clojure-aware-thread-factory` - creates threads with correct context classloader

#### 6.2 JobRequest/JobRequestHandler Types ✅ COMPLETE

**Namespace**: `clj-jobrunr.request`

**Implemented**:
- `ClojureJobRequestHandler` deftype implementing `JobRequestHandler`
- `ClojureJobRequest` deftype implementing `JobRequest`
- `make-job-request` factory function (2 arities: with/without explicit serializer)
- `request-edn`, `handler-class`, `request-class` utility functions

**Tests** (12 tests):
- Interface implementation validation
- `getJobRequestHandler` returns correct class
- Factory function creates proper requests
- Handler executes jobs correctly via multimethod dispatch
- Gson JSON serialization/deserialization round-trip
- Class loading via reflection (no-arg constructor)

**Key design decisions**:
- Handler defined before Request to avoid forward declaration issues with Class return type
- Handler uses reflection to access `.edn` field to avoid compile-time dependency
- Uses `ser/*serializer*` dynamic var for runtime serializer access

#### 6.3 Custom WorkerPolicy

**Namespace**: `clj-jobrunr.worker-policy`

**To implement**:
```clojure
(defn make-clojure-worker-policy
  "Creates a BackgroundJobServerWorkerPolicy that uses our custom classloader."
  [worker-count classloader]
  (let [executor-fn (fn [n]
                      (make-clojure-executor n classloader))]
    (DefaultBackgroundJobServerWorkerPolicy. worker-count executor-fn)))

(defn make-clojure-executor
  "Creates a JobRunrExecutor with threads using our composite classloader."
  [worker-count classloader]
  ;; Wrap PlatformThreadPoolJobRunrExecutor or create custom
  )
```

#### 6.4 Core API

**Namespace**: `clj-jobrunr.core`

**To implement**:
```clojure
(defn enqueue!
  "Enqueues a job for immediate execution. Returns job ID."
  ([job-type payload] (enqueue! job-type payload {}))
  ([job-type payload opts]
   (let [edn (bridge/job-edn *serializer* job-type payload)
         request (ClojureJobRequest. edn)
         job-name (or (:name opts) (job-type->name job-type))
         builder (-> (aJob)
                     (.withName job-name)
                     (.withJobRequest request))]
     (when-let [labels (:labels opts)]
       (.withLabels builder (into-array String labels)))
     (.enqueue JobScheduler builder))))

(defn schedule!
  "Schedules a job for future execution. Time is Duration or Instant."
  ([job-type payload time] (schedule! job-type payload time {}))
  ([job-type payload time opts]
   ;; Similar to enqueue! but with .scheduledAt
   ))

(defn recurring!
  "Creates or updates a recurring job with cron schedule."
  [recurring-id job-type payload cron & [opts]]
  ;; Use RecurringJobBuilder
  )

(defn delete-recurring!
  "Deletes a recurring job by ID."
  [recurring-id]
  (JobScheduler/deleteRecurringJob recurring-id))
```

#### 6.5 Integrant Updates

**Namespace**: `clj-jobrunr.integrant`

**Changes needed**:
- Capture classloader from `ClojureJobRequest` at server start
- Create composite classloader
- Create custom `BackgroundJobServerWorkerPolicy`
- Pass to JobRunr configuration

```clojure
(defmethod ig/init-key ::server
  [_ {:keys [storage-provider serialization ...]}]
  (let [;; Get classloader from our deftype
        source-cl (.getClassLoader ClojureJobRequest)
        composite-cl (cl/make-composite-classloader source-cl)
        worker-policy (make-clojure-worker-policy worker-count composite-cl)
        config (-> (JobRunr/configure)
                   (.useStorageProvider storage-provider)
                   (.useBackgroundJobServerWithWorkerPolicy worker-policy)
                   ...)]
    ...))
```

#### 6.6 Remove AOT Code

**Files to remove/modify**:
- Delete `src/clj_jobrunr/java_bridge.clj`
- Delete `test/clj_jobrunr/java_bridge_test.clj`
- Simplify `build.clj` (no more `compile-bridge` task)
- Update `deps.edn` paths

### Phase 7: Integration Tests with PostgreSQL (Not Started)

Requires running PostgreSQL database:

- `enqueue-executes-job-test`
- `schedule-executes-at-time-test`
- `recurring-executes-on-schedule-test`
- `failed-job-retries-test`
- `custom-serialization-roundtrip-test`
- `job-survives-restart-test`

### Phase 8: Documentation & Polish (Future)

- README with getting started guide
- Example application
- Migration guide from AOT approach (for early adopters)

---

## File Structure

```
clj-jobrunr/
├── deps.edn
├── DESIGN.md
├── PLAN.md
├── README.md
├── src/
│   └── clj_jobrunr/
│       ├── serialization.clj    ✅
│       ├── job.clj              ✅
│       ├── bridge.clj           ✅
│       ├── enqueue.clj          ✅ (to be replaced by core.clj)
│       ├── integrant.clj        ✅ (needs updates for Phase 6)
│       ├── classloader.clj      ✅ (spike complete)
│       ├── request.clj          ✅ (Phase 6.2)
│       ├── worker_policy.clj    ⏳ (Phase 6.3)
│       └── core.clj             ⏳ (Phase 6.4)
├── test/
│   └── clj_jobrunr/
│       ├── serialization_test.clj  ✅
│       ├── job_test.clj            ✅
│       ├── bridge_test.clj         ✅
│       ├── enqueue_test.clj        ✅
│       ├── integrant_test.clj      ✅
│       ├── classloader_test.clj    ✅
│       ├── request_test.clj        ✅ (Phase 6.2)
│       ├── integration_test.clj    ✅ (scaffolding)
│       └── test_utils.clj          ✅
└── build.clj                    # Simplified (no AOT needed)
```

---

## Success Criteria

### Unit Tests ✅
- [x] All serialization round-trips work
- [x] `defjob` generates function with docstring
- [x] `defjob` registers multimethod handler
- [x] `handle-job` dispatches correctly
- [x] Hierarchy dispatch works
- [x] Enqueue functions create correct structures
- [x] AOT bridge class compiles and executes

### Integration Tests (Requires PostgreSQL)
- [ ] Jobs enqueue and execute
- [ ] Scheduled jobs execute at correct time
- [ ] Recurring jobs execute on schedule
- [ ] Failed jobs retry
- [ ] Custom serialization works end-to-end
- [ ] Jobs survive server restart
- [ ] Dashboard shows jobs correctly
