(ns cljs-tlr.async
  "Async testing utilities for React Testing Library.

  Provides utilities for waiting on async operations and a macro
  for cleaner async test syntax."
  #?(:cljs (:require ["@testing-library/react" :as tlr])))

#?(:cljs
   (defn wait-for
     "Waits for a callback to not throw. Returns a Promise.

  Repeatedly calls the callback until it doesn't throw or times out.
  Useful for waiting on async state changes.

  Options:

  - `:timeout` - Max wait time in ms (default: 1000)
  - `:interval` - Polling interval in ms (default: 50)
  - `:on-timeout` - Custom error on timeout

  Example:

      (wait-for #(get-by-text \"Loaded\"))
      (wait-for #(is (= 5 @counter)) {:timeout 2000})"
     (^js [callback]
      (tlr/waitFor callback))
     (^js [callback opts]
      (tlr/waitFor callback (clj->js opts)))))

#?(:cljs
   (defn wait-for-element-to-be-removed
     "Waits for an element to be removed from the DOM. Returns a Promise.

  Takes either an element or a callback that returns an element.

  Example:

      (wait-for-element-to-be-removed #(query-by-text \"Loading...\"))"
     (^js [element-or-callback]
      (tlr/waitForElementToBeRemoved element-or-callback))
     (^js [element-or-callback opts]
      (tlr/waitForElementToBeRemoved element-or-callback (clj->js opts)))))

#?(:cljs
   (defn act
     "Wraps code that causes React state updates.

  React requires state updates to be wrapped in `act()` for proper
  batching. Testing Library handles this automatically for its
  utilities, but you may need it for custom async operations.

  Returns a Promise if the callback returns a Promise."
     [callback]
     (tlr/act callback)))

#?(:clj
   (defmacro async-test
     "Macro for async tests with automatic done handling.

  Wraps the body in `cljs.test/async` and handles Promise resolution
  and error catching. The test completes when the Promise chain resolves
  or rejects.

  Example:

      (deftest my-async-test
        (async-test
          (render ($ my-component))
          (-> (wait-for #(get-by-text \"Ready\"))
              (.then #(is (some? %))))))"
     [& body]
     `(cljs.test/async
       ~'done
       (-> (do ~@body)
           (.then (fn [_#] (~'done)))
           (.catch (fn [e#]
                     (cljs.test/is false (str "Async error: " e#))
                     (~'done)))))))
