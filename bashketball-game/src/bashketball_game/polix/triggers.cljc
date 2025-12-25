(ns bashketball-game.polix.triggers
  "Trigger integration layer for bashketball-game.

  Connects bashketball actions to the polix.triggers system, enabling card
  abilities to react to game events. Provides:

  - [[action->events]] to generate before/after events from actions
  - [[fire-bashketball-event]] to fire events with game-specific context
  - Registry management functions delegating to [[polix.triggers.core]]

  The trigger registry is passed externally (not stored in game-state) since
  it's a derived runtime structure rebuilt from cards at game initialization."
  (:require
   [polix.triggers.core :as triggers]))

(defn- event-type-with-suffix
  "Appends a suffix to an action type keyword.

  Example: `:bashketball/move-player` + `.before` → `:bashketball/move-player.before`"
  [action-type suffix]
  (keyword (namespace action-type)
           (str (name action-type) suffix)))

(defn action->events
  "Generates before and after events from an action.

  Returns `{:before event :after event}` where each event contains:
  - `:type` - action type with `.before` or `.after` suffix
  - All action fields (player-id, position, etc.)
  - `:turn-number`, `:active-player`, `:phase` from game state"
  [game-state action]
  (let [action-type (:type action)
        base-fields (-> action
                        (dissoc :type)
                        (assoc :turn-number (:turn-number game-state)
                               :active-player (:active-player game-state)
                               :phase (:phase game-state)))]
    {:before (assoc base-fields :type (event-type-with-suffix action-type ".before"))
     :after (assoc base-fields :type (event-type-with-suffix action-type ".after"))}))

(defn- enrich-event
  "Adds game state context to an event for trigger processing.

  The polix.triggers.processing module builds trigger documents from event
  fields. By enriching the event with game state, triggers can access game
  context via `:doc/state`, `:doc/turn-number`, etc."
  [game-state event]
  (assoc event
         :turn-number (:turn-number game-state)
         :active-player (:active-player game-state)
         :phase (:phase game-state)
         :state game-state))

(defn fire-bashketball-event
  "Fires an event with bashketball-specific context.

  Enriches the event with game state fields before delegating to
  [[polix.triggers.core/fire-event]]. Returns the result map containing
  `:state`, `:registry`, `:prevented?`, and `:results`."
  [{:keys [state registry]} event]
  (let [enriched-event (enrich-event state event)]
    (triggers/fire-event {:state state :registry registry} enriched-event)))

;;; ---------------------------------------------------------------------------
;;; Registry Management (delegates to polix.triggers.core)
;;; ---------------------------------------------------------------------------

(defn create-registry
  "Creates an empty trigger registry."
  []
  (triggers/create-registry))

(defn register-trigger
  "Registers a trigger definition in the registry.

  Takes a trigger definition map and binding context (source, owner, self).
  Returns the updated registry.

  The trigger definition should contain:
  - `:event-types` - set of event type keywords (e.g., `#{:bashketball/move-player.after}`)
  - `:timing` - when to fire (`:polix.triggers.timing/before`, etc.)
  - `:condition` - optional polix policy expression
  - `:effect` - effect to apply when condition is satisfied
  - `:once?` - optional, remove after firing (default false)
  - `:priority` - optional, lower values fire first (default 0)"
  [registry trigger-def source owner self]
  (triggers/register-trigger registry trigger-def source owner self))

(defn unregister-trigger
  "Removes a trigger by ID from the registry."
  [registry trigger-id]
  (triggers/unregister-trigger registry trigger-id))

(defn unregister-triggers-by-source
  "Removes all triggers registered by a source.

  Useful when a card ability is removed and all its triggers should be
  cleaned up."
  [registry source-id]
  (triggers/unregister-triggers-by-source registry source-id))

(defn get-triggers
  "Returns all registered triggers as a sequence."
  [registry]
  (triggers/get-triggers registry))

(defn get-trigger
  "Returns a trigger by ID, or nil if not found."
  [registry trigger-id]
  (triggers/get-trigger registry trigger-id))
