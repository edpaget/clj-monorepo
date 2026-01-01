(ns bashketball-game.polix.functions
  "Registry of game-specific functions callable from effect parameters.

  Functions are called during effect parameter resolution, enabling
  declarative rules to compute values like movement costs. This keeps
  game logic centralized in the rules layer rather than scattered in
  effect handlers.

  ## Function Signature

  Functions receive `(state ctx & resolved-args)` where:
  - `state` - current game state
  - `ctx` - effect context with `:event`, `:trigger`, etc.
  - `resolved-args` - arguments after path resolution

  ## Usage in Rules

  ```clojure
  {:effect {:type :bashketball/do-move-step
            :cost [:bashketball-fn/step-cost
                   [:ctx :event :player-id]
                   [:ctx :event :to-position]]}}
  ```"
  (:require
   [bashketball-game.movement :as movement]))

(defonce ^:private registry (atom {}))

(defn register-fn!
  "Registers a game function by key.

  The function implementation receives `(state ctx & args)` where args
  have already been resolved from path expressions."
  [fn-key fn-impl]
  (swap! registry assoc fn-key fn-impl))

(defn get-fn
  "Returns the registered function for the given key, or nil if not found."
  [fn-key]
  (get @registry fn-key))

(defn registered-fns
  "Returns a set of all registered function keys."
  []
  (set (keys @registry)))

(defn register-game-functions!
  "Registers all game-specific functions.

  Call once during initialization."
  []
  ;; Movement cost functions
  (register-fn! :bashketball-fn/step-cost
                (fn [state _ctx player-id to-position]
                  (:total-cost (movement/compute-step-cost state player-id to-position))))

  (register-fn! :bashketball-fn/base-cost
                (fn [_state _ctx]
                  1))

  (register-fn! :bashketball-fn/zoc-cost
                (fn [state _ctx player-id to-position]
                  (:zoc-cost (movement/compute-step-cost state player-id to-position))))

  (register-fn! :bashketball-fn/zoc-defender-ids
                (fn [state _ctx player-id to-position]
                  (:zoc-defender-ids (movement/compute-step-cost state player-id to-position))))

  ;; Arithmetic helpers
  (register-fn! :bashketball-fn/add
                (fn [_state _ctx & nums]
                  (apply + nums)))

  (register-fn! :bashketball-fn/max
                (fn [_state _ctx & nums]
                  (if (seq nums)
                    (apply max nums)
                    0))))
