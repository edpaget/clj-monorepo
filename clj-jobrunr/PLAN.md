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
| Phase 6.3: Worker Policy | ✅ Complete | 10 |
| Phase 6.4: Core API | ✅ Complete | 13 |
| Phase 6.5: Integrant Updates | ✅ Complete | - |
| Phase 6.6: Cleanup | ✅ Complete | -4 |
| **Total** | | **98 tests, 169 assertions** |

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

#### 6.3 Custom WorkerPolicy ✅ COMPLETE

**Namespace**: `clj-jobrunr.worker-policy`

**Implemented**:
- `make-clojure-executor` - creates a `JobRunrExecutor` using Java 21+ virtual threads
  with the composite classloader set as their context classloader
- `make-clojure-worker-policy` - creates a `BackgroundJobServerWorkerPolicy`
  that returns our custom executor and uses `BasicWorkDistributionStrategy`
- `default-worker-count` - returns available processors

**Tests** (10 tests):
- Executor implements JobRunrExecutor interface
- Executor reports correct worker count
- Executor can start and stop
- Executor creates virtual threads (verified via `Thread.isVirtual()`)
- Worker threads have correct context classloader
- Worker threads can load ClojureJobRequest via Class.forName
- Worker policy implements BackgroundJobServerWorkerPolicy
- Worker policy creates working executor
- Default worker count returns available processors
- Full integration test: policy → executor → thread → Class.forName → instantiate handler

**Key design decisions**:
- Uses Java 21+ virtual threads via `Executors.newThreadPerTaskExecutor()`
- Custom `ThreadFactory` wraps virtual thread factory and sets context classloader
- Uses `BasicWorkDistributionStrategy` (same as `DefaultBackgroundJobServerWorkerPolicy`)
- `worker-count` is logical for work distribution, not actual thread limit (virtual threads are per-task)

#### 6.4 Core API ✅ COMPLETE

**Namespace**: `clj-jobrunr.core`

**Implemented**:
- `enqueue!` - immediate job execution with JobBuilder
- `schedule!` - scheduled execution (Duration or Instant) with JobBuilder
- `recurring!` - cron-based recurring jobs with RecurringJobBuilder
- `delete-recurring!` - delete recurring jobs by ID

**Options supported**:
- `:name` - custom display name for dashboard
- `:labels` - vector of up to 3 labels for filtering
- `:id` - custom UUID to prevent duplicates
- `:retries` - number of retry attempts on failure
- `:zone` - timezone for recurring jobs (recurring! only)

**Tests** (13 tests):
- `job-type->name` conversion (namespaced, simple, current namespace)
- `build-job` with various option combinations
- API function existence verification
- Integration tests (commented, require running JobRunr server)

**Key design decisions**:
- Uses `BackgroundJob.create(JobBuilder)` for immediate and scheduled jobs
- Uses `BackgroundJob.createRecurrently(RecurringJobBuilder)` for recurring
- Converts job-type keyword to human-readable name for dashboard
- Supports all JobBuilder options via opts map

#### 6.5 Integrant Updates ✅ COMPLETE

**Namespace**: `clj-jobrunr.integrant`

**Implemented**:
- `::server` component now uses custom worker policy with virtual threads
- Gets classloader from `ClojureJobRequest` at server start
- Creates `BackgroundJobServerConfiguration` with custom worker policy
- Sets serializer before workers start (via `alter-var-root`)
- Supports `:worker-count` config option (defaults to available processors)

**Configuration example**:
```clojure
{::server
 {:storage-provider #ig/ref ::storage-provider
  :serialization #ig/ref ::serialization
  :dashboard? true
  :dashboard-port 8080
  :poll-interval 15
  :worker-count 4}}
```

**Key implementation**:
```clojure
(defmethod ig/init-key ::server
  [_ {:keys [storage-provider serialization dashboard? dashboard-port
             poll-interval worker-count]
      :or {dashboard? false
           dashboard-port 8000
           poll-interval 15}}]
  (alter-var-root #'ser/*serializer* (constantly serialization))
  (let [source-cl     (.getClassLoader (req/request-class))
        worker-count  (or worker-count (wp/default-worker-count))
        worker-policy (wp/make-clojure-worker-policy worker-count source-cl)
        server-config (-> (BackgroundJobServerConfiguration/usingStandardBackgroundJobServerConfiguration)
                          (.andPollIntervalInSeconds poll-interval)
                          (.andBackgroundJobServerWorkerPolicy worker-policy))
        config        (-> (JobRunr/configure)
                          (.useStorageProvider storage-provider)
                          (.useBackgroundJobServer server-config))
        ...]
    ...))
```

#### 6.6 Remove AOT Code ✅ COMPLETE

**Removed**:
- Deleted `src/clj_jobrunr/java_bridge.clj` - AOT gen-class bridge no longer needed
- Deleted `test/clj_jobrunr/java_bridge_test.clj` - removed 4 tests for deleted code
- Simplified `build.clj` - removed `compile-bridge` and `compile-all` tasks
- Updated `deps.edn` - removed `target/classes` from test paths
- Updated `.clj-kondo/config.edn` - removed java-bridge-test config

**Kept**:
- `enqueue.clj` - still used by integration tests for creating request maps

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
├── build.clj                    ✅ (simplified - no AOT needed)
├── src/
│   └── clj_jobrunr/
│       ├── serialization.clj    ✅
│       ├── job.clj              ✅
│       ├── bridge.clj           ✅
│       ├── enqueue.clj          ✅ (utility for request maps)
│       ├── integrant.clj        ✅ (with worker policy)
│       ├── classloader.clj      ✅
│       ├── request.clj          ✅ (ClojureJobRequest/Handler)
│       ├── worker_policy.clj    ✅ (virtual threads)
│       └── core.clj             ✅ (public API)
└── test/
    └── clj_jobrunr/
        ├── serialization_test.clj  ✅
        ├── job_test.clj            ✅
        ├── bridge_test.clj         ✅
        ├── enqueue_test.clj        ✅
        ├── integrant_test.clj      ✅
        ├── classloader_test.clj    ✅
        ├── request_test.clj        ✅
        ├── worker_policy_test.clj  ✅
        ├── core_test.clj           ✅
        ├── integration_test.clj    ✅ (scaffolding)
        └── test_utils.clj          ✅
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
- [x] ClojureJobRequest/Handler deftype classes work (replaced AOT)
- [x] Virtual thread worker policy sets correct classloader
- [x] Core API functions (enqueue!, schedule!, recurring!) build jobs correctly

### Integration Tests (Requires PostgreSQL)
- [ ] Jobs enqueue and execute
- [ ] Scheduled jobs execute at correct time
- [ ] Recurring jobs execute on schedule
- [ ] Failed jobs retry
- [ ] Custom serialization works end-to-end
- [ ] Jobs survive server restart
- [ ] Dashboard shows jobs correctly
