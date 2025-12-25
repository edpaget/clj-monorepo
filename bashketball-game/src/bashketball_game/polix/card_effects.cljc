(ns bashketball-game.polix.card-effects
  "Manages registration and lifecycle of card-based triggers.

   Converts structured ability definitions from cards into trigger registrations
   in the polix trigger system. Handles:

   - Player abilities (registered at game start, managed on substitution)
   - Ability card attachments (registered on attach, unregistered on detach)

   Usage:

   ```clojure
   ;; At game initialization
   (def registry (initialize-game-triggers state catalog))

   ;; When ability card is attached
   (def registry' (register-attached-abilities registry catalog attachment player-id team))

   ;; When ability card is detached
   (def registry'' (unregister-attached-abilities registry' instance-id))
   ```"
  (:require
   [bashketball-game.effect-catalog :as catalog]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]))

;; =============================================================================
;; Ability to Trigger Conversion
;; =============================================================================

(defn- ability->trigger-def
  "Converts an ability definition to a trigger registration map.

   Takes an ability from a card and creates the trigger definition structure
   expected by [[triggers/register-trigger]]. Returns nil if the ability
   has no trigger (passive abilities)."
  [ability]
  (when-let [trigger (:ability/trigger ability)]
    {:event-types #{(:trigger/event trigger)}
     :timing (or (:trigger/timing trigger) :after)
     :priority (or (:trigger/priority trigger) 0)
     :condition (:trigger/condition trigger)
     :effect (:ability/effect ability)
     :once? (:trigger/once? trigger)}))

(defn- make-source-id
  "Creates a source ID for tracking triggers from an entity.

   Uses the owner ID directly (player-id or instance-id). All abilities
   from the same owner share the same source, allowing bulk unregistration
   when the player leaves court or the attachment is removed."
  [owner-id _ability-id]
  owner-id)

;; =============================================================================
;; Player Ability Registration
;; =============================================================================

(defn register-player-abilities
  "Registers triggers for a player's innate abilities.

   Takes a registry, catalog, player map, and team. Registers all triggered
   abilities from the player's card. Returns the updated registry.

   The player must have a `:card-slug` to look up abilities from the catalog.
   If the catalog returns nil or the player has no triggered abilities,
   returns the registry unchanged."
  [registry effect-catalog player team]
  (let [card-slug (:card-slug player)
        abilities (when card-slug
                    (catalog/get-abilities effect-catalog card-slug))]
    (reduce
     (fn [reg ability]
       (if-let [trigger-def (ability->trigger-def ability)]
         (triggers/register-trigger
          reg
          trigger-def
          (make-source-id (:id player) (:ability/id ability))
          team
          (:id player))
         reg))
     registry
     (or abilities []))))

(defn unregister-player-abilities
  "Unregisters all triggers for a player's innate abilities.

   Removes all triggers that were registered with the player's ID as the
   source. Returns the updated registry."
  [registry player-id]
  (triggers/unregister-triggers-by-source registry player-id))

;; =============================================================================
;; Ability Card Attachment Registration
;; =============================================================================

(defn register-attached-abilities
  "Registers triggers when an ability card is attached to a player.

   Takes a registry, catalog, attachment (card instance), target player ID,
   and team. Registers triggers for all abilities on the card with the
   target player as `:self`.

   For token cards, uses the inline card definition instead of catalog lookup."
  [registry effect-catalog attachment target-player-id team]
  (let [instance-id (:instance-id attachment)
        abilities   (if (state/token? attachment)
                      (catalog/get-abilities-from-card (state/get-token-card attachment))
                      (catalog/get-abilities effect-catalog (:card-slug attachment)))]
    (reduce
     (fn [reg ability]
       (if-let [trigger-def (ability->trigger-def ability)]
         (triggers/register-trigger
          reg
          trigger-def
          (make-source-id instance-id (:ability/id ability))
          team
          target-player-id)
         reg))
     registry
     (or abilities []))))

(defn unregister-attached-abilities
  "Unregisters triggers when an ability card is detached.

   Removes all triggers registered with the attachment's instance-id as
   the source. Returns the updated registry."
  [registry instance-id]
  (triggers/unregister-triggers-by-source registry instance-id))

;; =============================================================================
;; Substitution Management
;; =============================================================================

(defn handle-player-leaving-court
  "Handles trigger lifecycle when a player leaves the court.

   Unregisters the player's innate ability triggers. Returns updated registry."
  [registry player-id]
  (unregister-player-abilities registry player-id))

(defn handle-player-entering-court
  "Handles trigger lifecycle when a player enters the court.

   Registers the player's innate ability triggers. Returns updated registry."
  [registry effect-catalog player team]
  (register-player-abilities registry effect-catalog player team))

;; =============================================================================
;; Game Initialization
;; =============================================================================

(defn- register-team-players
  "Registers ability triggers for all on-court players of a team."
  [registry effect-catalog game-state team]
  (let [on-court-ids (state/get-on-court-players game-state team)]
    (reduce
     (fn [reg player-id]
       (let [player (state/get-basketball-player game-state player-id)]
         (register-player-abilities reg effect-catalog player team)))
     registry
     on-court-ids)))

(defn initialize-game-triggers
  "Registers triggers for all players currently on court.

   Call this at game start after players have been placed on court.
   Returns a registry with all player ability triggers registered.

   Note: Does not register triggers for bench players. Those are registered
   when they substitute onto the court."
  [game-state effect-catalog]
  (-> (triggers/create-registry)
      (register-team-players effect-catalog game-state :team/HOME)
      (register-team-players effect-catalog game-state :team/AWAY)))

;; =============================================================================
;; Action-Based Registry Updates
;; =============================================================================

(defn- find-attachment
  "Finds an attachment by instance-id on a player."
  [state player-id instance-id]
  (let [player (state/get-basketball-player state player-id)]
    (some #(when (= (:instance-id %) instance-id) %)
          (:attachments player))))

(defn update-registry-for-action
  "Updates the trigger registry based on an action that was just applied.

   Certain actions affect trigger registration:
   - `:bashketball/substitute` - unregister leaving player, register entering player
   - `:bashketball/attach-ability` - register ability triggers for attachment
   - `:bashketball/detach-ability` - unregister ability triggers

   Gets required data from the action map and states directly.

   Returns the updated registry."
  [registry effect-catalog old-state new-state action]
  (case (:type action)
    :bashketball/substitute
    (let [{:keys [on-court-id off-court-id]} action
          team                               (state/get-basketball-player-team old-state on-court-id)
          entering-player                    (state/get-basketball-player new-state off-court-id)]
      (-> registry
          (handle-player-leaving-court on-court-id)
          (handle-player-entering-court effect-catalog entering-player team)))

    :bashketball/attach-ability
    (let [{:keys [instance-id target-player-id]} action
          team                                   (state/get-basketball-player-team new-state target-player-id)
          attachment                             (find-attachment new-state target-player-id instance-id)]
      (register-attached-abilities registry effect-catalog attachment target-player-id team))

    :bashketball/detach-ability
    (let [{:keys [instance-id]} action]
      (unregister-attached-abilities registry instance-id))

    ;; No registry changes for other actions
    registry))
