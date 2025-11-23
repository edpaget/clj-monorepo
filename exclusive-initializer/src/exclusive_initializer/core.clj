(ns exclusive-initializer.core
  "Provides a wrapper for test fixtures that makes sure a given fixture
  is run once across tests running parallel.")

(set! *warn-on-reflection* true)

(def ^:private locks (atom {}))

(defn do-handler!
  "Implements intialize!/de-initialize! macro"
  [lock-name state thunk]
  (locking lock-name
    (when-not (= state (lock-name @locks))
      (thunk))
    (swap! locks assoc lock-name state)))

(defn reset-locks!
  "Resets all locks created by the wrap macro."
  []
  (reset! locks {}))

(defmacro initialize!
  "Sets up something in a test fixture that runs once and won't run again until deinitialized.

   Handles multi-threaded test runners by using locks to ensure exclusive execution.
   Takes a lock name (typically a namespaced keyword) and a body to execute. The body
   will only execute once per lock name across all parallel test threads, even if the
   fixture is called multiple times.

   Example:

       (defn printer-fixture [f]
         (initialize! ::print-job
           (prn \"Good Job\"))
         (f))"
  {:style/indent 1}
  [lock-name & body]
  `(do-handler! ~lock-name true (fn [] ~@body)))

(defmacro deinitialize!
  "Tears down something previously set up in a test fixture.

   Takes a lock name (matching the one used in [[initialize!]]) and a body to execute.
   The body will execute once to clean up resources. Locks the initializer block from
   running while deinitializing, ensuring thread-safe cleanup in multi-threaded test
   runners.

   Example:

       (defn printer-fixture [f]
         (initialize! ::print-job
           (prn \"Good Job\"))
         (f)
         (deinitialize! ::print-job
           (prn \"We deinitialized\")))"
  {:style/indent 1}
  [lock-name & body]
  `(do-handler! ~lock-name false (fn [] ~@body)))
