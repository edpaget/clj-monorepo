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
  "```Setup something in test fixture that will run once then then not run again
  until it is deinitialized. Handle multi-threaded test runners.

  (defn printer-fixture [f]
    (initialize! ::print-job
      (prn \"Good Job\"))
    (f))
  ```

  Args:
    body: the body to execute"
  {:style/indent 1}
  [lock-name & body]
  `(do-handler! ~lock-name true (fn [] ~@body)))

(defmacro deinitialize!
  "```Teardown something previously setup in a test fixture.

  (defn printer-fixture [f]
    (initialize! ::print-job
      (prn \"Good Job\"))
    (f)
    (deintialize! ::print-job
      (prn \"We deinitialized\")))
  ```

  This also locks the block in the initializer from running while it is
  deinitializing.

  Args:
    binding-form: three element vector that bind the functions provided by the macro
    body: the body to execute"
  {:style/indent 1}
  [lock-name & body]
  `(do-handler! ~lock-name false (fn [] ~@body)))
