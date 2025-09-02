(ns exclusive-initializer.core
  "Provides a wrapper for test fixtures that makes sure a given fixture
  is run once across tests running parallel."
  (:import
   [java.util.concurrent.locks ReentrantLock]))

(def ^:private locks (atom {}))

(defn- make-lock
  [lock-name]
  (let [[_ lock-obj] (-> (swap! locks (fn [val]
                                        (cond-> val
                                          (not (contains? val lock-name))
                                          (assoc lock-name [false (ReentrantLock.)]))))
                         (get lock-name))]
    {:lock          (fn lock [] (.lock lock-obj))
     :unlock        (fn unlock [] (.unlock lock-obj))
     :initialize!   (fn initialize! [] (swap! locks assoc lock-name [true lock-obj]))
     :deinitialize! (fn deinitialize! [] (swap! locks assoc lock-name [false lock-obj]))
     :initialized?  (fn initialized? [] (-> @locks lock-name first))}))

(defn do-wrap
  "Implements wrap macro"
  [lock-name thunk]
  (let [lock (make-lock lock-name)]
    (thunk lock)))

(defn reset-locks!
  "Resets all locks created by the wrap macro."
  []
  (reset! locks {}))

(defmacro wrap
  "Waps a body of code providing the functions `lock`, `unlock`, `initialize!`,
  `deinitialize!` and  `initialized?` as keys of a map that can be destructured,
  which can then be used to create a test fixture that runs only one even if tests
  using the fixture are run concurrently.

  For example a fixture that prints \"Good Job\" could use wrap like:

  ```
  (defn printer-fixture [f]
    (exclusive-initializer.core/wrap [{:keys [lock unlock initialize! initialized?]} ::print-lock]
       (lock)

       (when-not (initialized?)
         (prn \"Good Job\")
         (initialize!))
       (unlock)
       (f)))
  ```

  The entire body is wrapped in a try ... finally form so the fixture will be
  unlocked if it throws.

  Args:
    binding-form: three element vector that bind the functions provided by the macro
    body: the body to execute"
  [[binding-form lock-name] & body]
  `(do-wrap ~lock-name (fn [~binding-form] ~@body)))
