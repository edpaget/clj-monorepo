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
| **Total** | | **61 tests, 120 assertions** |

---

## Phase 1: Core Library (Unit Tests) ✅ COMPLETE

### 1.1 Serialization Module ✅

**Namespace**: `clj-jobrunr.serialization`

**Implemented**:
- `make-serializer` - creates serializer with custom readers/writers
- `default-serializer` - returns default serializer
- `serialize` / `deserialize` - EDN round-trip
- `install-time-print-methods!` - java.time tagged literal support
- `*serializer*` - dynamic var for runtime access

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

### Phase 6: JobRunr API Integration (Not Started)

Add functions that actually call JobRunr APIs:

```clojure
;; In clj-jobrunr.core or new namespace
(defn enqueue! [job-type payload]
  ;; Calls BackgroundJob/enqueue with ClojureBridge lambda
  )

(defn schedule! [job-type payload time]
  ;; Calls BackgroundJob/schedule
  )

(defn recurring! [job-id job-type cron payload]
  ;; Calls BackgroundJob/scheduleRecurrently
  )

(defn delete-recurring! [job-id]
  ;; Calls BackgroundJob/delete
  )
```

### Phase 7: Integration Tests with PostgreSQL (Not Started)

Requires running PostgreSQL database:

- `enqueue-executes-job-test`
- `schedule-executes-at-time-test`
- `recurring-executes-on-schedule-test`
- `failed-job-retries-test`
- `custom-serialization-roundtrip-test`
- `job-survives-restart-test`

### Phase 8: Per-Job Class Generation (Future Enhancement)

Generate a class per job type for better dashboard display:

- Build-time code generation
- Or runtime bytecode generation with ASM

---

## File Structure

```
clj-jobrunr/
├── deps.edn
├── build.clj                    # AOT compilation tasks
├── DESIGN.md
├── PLAN.md
├── README.md
├── src/
│   └── clj_jobrunr/
│       ├── serialization.clj    ✅
│       ├── job.clj              ✅
│       ├── bridge.clj           ✅
│       ├── enqueue.clj          ✅
│       ├── integrant.clj        ✅
│       └── java_bridge.clj      ✅
├── test/
│   └── clj_jobrunr/
│       ├── serialization_test.clj  ✅
│       ├── job_test.clj            ✅
│       ├── bridge_test.clj         ✅
│       ├── enqueue_test.clj        ✅
│       ├── integrant_test.clj      ✅
│       ├── java_bridge_test.clj    ✅
│       ├── integration_test.clj    ✅ (scaffolding)
│       └── test_utils.clj          ✅
└── target/
    └── classes/                 # AOT-compiled classes
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
