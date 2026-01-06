(ns bashketball-game.polix.stats
  "Stat calculation with event-based modifier injection.

  Provides functions to calculate effective stats by:
  1. Getting base value from player
  2. Firing calculation event
  3. Collecting modifiers from triggers
  4. Applying modifiers (additives then multipliers)

  All functions require a context map with `:state` and `:registry` keys."
  (:require
   [bashketball-game.state :as state]
   [bashketball-game.polix.triggers :as triggers]
   [clojure.string :as str]))

(defn- normalize-stat
  "Normalizes a stat keyword to lowercase non-namespaced form.

  e.g. :stat/SHOOTING -> :shooting, :shooting -> :shooting"
  [stat]
  (-> stat name str/lower-case keyword))

(defn- apply-modifiers
  "Applies modifiers to a base value.

  Order: additives first, then multipliers. Floors at 0."
  [base-value modifiers]
  (let [additives (filter :amount modifiers)
        multipliers (filter :multiplier modifiers)
        after-add (reduce + base-value (map :amount additives))
        after-mult (if (seq multipliers)
                     (reduce * after-add (map :multiplier multipliers))
                     after-add)]
    (max 0 (int after-mult))))

(defn- collect-modifiers-from-event
  "Fires a calculation event and collects modifiers from trigger results."
  [ctx event]
  (let [result (triggers/fire-request-event ctx event)]
    {:state (:state result)
     :registry (:registry result)
     :modifiers (->> (:results result)
                     (filter :fired?)
                     (mapcat #(get-in % [:effect-result :modifiers] [])))}))

(defn get-effective-speed
  "Returns effective speed for a player, including modifier injection.

  Fires `:bashketball/calculate-speed.request` to allow triggers to add
  modifiers before the final value is computed.

  Requires context with `:state` and `:registry`."
  [{:keys [state registry] :as ctx} player-id]
  {:pre [(some? state) (some? registry)]}
  (let [player (state/get-basketball-player state player-id)
        base-speed (or (get-in player [:stats :speed]) 2)
        event {:type :bashketball/calculate-speed.request
               :event-type :bashketball/calculate-speed.request
               :player-id player-id
               :base-value base-speed}
        {:keys [modifiers] :as result} (collect-modifiers-from-event ctx event)
        player-modifiers (filter #(= (:stat %) :speed) (:modifiers player))
        all-modifiers (concat player-modifiers modifiers)]
    {:value (apply-modifiers base-speed all-modifiers)
     :state (:state result)
     :registry (:registry result)}))

(defn get-effective-stat
  "Returns effective value for any stat, including modifier injection.

  Fires `:bashketball/calculate-stat.request` to allow triggers to add
  modifiers before the final value is computed.

  Requires context with `:state` and `:registry`."
  [{:keys [state registry] :as ctx} player-id stat]
  {:pre [(some? state) (some? registry)]}
  (let [player (state/get-basketball-player state player-id)
        normalized-stat (normalize-stat stat)
        base-value (get-in player [:stats normalized-stat] 0)
        event {:type :bashketball/calculate-stat.request
               :event-type :bashketball/calculate-stat.request
               :player-id player-id
               :stat normalized-stat
               :base-value base-value}
        {:keys [modifiers] :as result} (collect-modifiers-from-event ctx event)
        player-modifiers (filter #(= (normalize-stat (:stat %)) normalized-stat) (:modifiers player))
        all-modifiers (concat player-modifiers modifiers)]
    {:value (apply-modifiers base-value all-modifiers)
     :state (:state result)
     :registry (:registry result)}))
