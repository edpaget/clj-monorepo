(ns bashketball-game.polix.card-effects
  "Manages registration and lifecycle of card-based triggers.

   Converts structured ability definitions from cards into trigger registrations
   in the polix trigger system. Handles:

   - Player abilities (registered at game start, managed on substitution)
   - Ability card attachments (registered on attach, unregistered on detach)
   - Team assets (registered on play, unregistered on removal)
   - Response assets (registered with prompt effect)

   Usage:

   ```clojure
   ;; At game initialization
   (def registry (initialize-game-triggers state catalog))

   ;; When ability card is attached
   (def registry' (register-attached-abilities registry catalog attachment player-id team))

   ;; When team asset is played
   (def registry' (register-asset-triggers registry catalog asset team))

   ;; When asset is removed
   (def registry'' (unregister-asset-triggers registry' instance-id))
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
;; Team Asset Registration
;; =============================================================================

(defn- asset-trigger->trigger-def
  "Converts an asset trigger definition to a trigger registration map.

   Asset triggers have the structure `{:trigger TriggerDef :effect EffectDef}`."
  [{:keys [trigger effect]}]
  {:event-types #{(:trigger/event trigger)}
   :timing (or (:trigger/timing trigger) :after)
   :priority (or (:trigger/priority trigger) 0)
   :condition (:trigger/condition trigger)
   :effect effect
   :once? (:trigger/once? trigger)})

(defn- response->trigger-def
  "Converts a response definition to a trigger marked as a response.

   Response triggers are marked with `:response? true` so that
   [[fire-request-event]] returns them for the caller to create choice prompts
   rather than firing automatically. The effect contains the response metadata
   needed to reveal the asset and apply the response effect."
  [response asset-instance-id]
  (let [trigger (:response/trigger response)]
    {:event-types #{(:trigger/event trigger)}
     :timing (or (:trigger/timing trigger) :before)
     :priority (or (:trigger/priority trigger) 0)
     :condition (:trigger/condition trigger)
     :effect {:effect/type :bashketball/apply-response
              :asset-instance-id asset-instance-id
              :prompt (:response/prompt response)
              :response-effect (:response/effect response)}
     :once? false
     :response? true}))

(defn register-asset-triggers
  "Registers triggers for a team asset in play.

   Takes a registry, catalog, asset instance, and team. Registers all
   triggers defined in the asset's power definition.

   For response assets, registers a trigger that prompts Apply/Pass.
   For regular assets, registers all `:asset/triggers`.

   For token assets, uses the inline card definition instead of catalog lookup."
  [registry effect-catalog asset team]
  (let [instance-id (:instance-id asset)
        asset-power (if (state/token? asset)
                      (catalog/get-asset-power-from-card (state/get-token-card asset))
                      (catalog/get-asset-power effect-catalog (:card-slug asset)))]
    (if-not asset-power
      registry
      (let [;; Register response trigger if present
            reg-with-response
            (if-let [response (:asset/response asset-power)]
              (triggers/register-trigger
               registry
               (response->trigger-def response instance-id)
               instance-id
               team
               instance-id)
              registry)
            ;; Register all regular triggers
            asset-triggers                                   (or (:asset/triggers asset-power) [])]
        (reduce
         (fn [reg asset-trigger]
           (triggers/register-trigger
            reg
            (asset-trigger->trigger-def asset-trigger)
            instance-id
            team
            instance-id))
         reg-with-response
         asset-triggers)))))

(defn unregister-asset-triggers
  "Unregisters triggers when a team asset is removed from play.

   Removes all triggers registered with the asset's instance-id as
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

(defn- register-team-assets
  "Registers triggers for all assets in play for a team."
  [registry effect-catalog game-state team]
  (let [assets (get-in game-state [:players team :assets] [])]
    (reduce
     (fn [reg asset]
       (register-asset-triggers reg effect-catalog asset team))
     registry
     assets)))

(defn initialize-game-triggers
  "Registers triggers for all players on court and assets in play.

   Call this at game start after players have been placed on court.
   Returns a registry with all player ability and asset triggers registered.

   Note: Does not register triggers for bench players. Those are registered
   when they substitute onto the court."
  [game-state effect-catalog]
  (-> (triggers/create-registry)
      (register-team-players effect-catalog game-state :team/HOME)
      (register-team-players effect-catalog game-state :team/AWAY)
      (register-team-assets effect-catalog game-state :team/HOME)
      (register-team-assets effect-catalog game-state :team/AWAY)))

;; =============================================================================
;; Action-Based Registry Updates
;; =============================================================================

(defn- find-attachment
  "Finds an attachment by instance-id on a player."
  [state player-id instance-id]
  (let [player (state/get-basketball-player state player-id)]
    (some #(when (= (:instance-id %) instance-id) %)
          (:attachments player))))

(defn- find-asset
  "Finds an asset by instance-id in a team's assets."
  [state team instance-id]
  (some #(when (= (:instance-id %) instance-id) %)
        (get-in state [:players team :assets] [])))

(defn- played-card-is-asset?
  "Checks if the played card ended up in assets (not discard)."
  [old-state new-state team instance-id]
  (let [old-assets (get-in old-state [:players team :assets] [])
        new-assets (get-in new-state [:players team :assets] [])]
    (and (not (some #(= (:instance-id %) instance-id) old-assets))
         (some #(= (:instance-id %) instance-id) new-assets))))

(defn update-registry-for-action
  "Updates the trigger registry based on an action that was just applied.

   Certain actions affect trigger registration:
   - `:bashketball/substitute` - unregister leaving player, register entering player
   - `:bashketball/attach-ability` - register ability triggers for attachment
   - `:bashketball/detach-ability` - unregister ability triggers
   - `:bashketball/play-card` - register asset triggers if card is a team asset
   - `:bashketball/move-asset` - unregister asset triggers when removed
   - `:bashketball/create-token` - register triggers for token abilities or assets

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

    :bashketball/play-card
    (let [{:keys [player instance-id]} action]
      (if (played-card-is-asset? old-state new-state player instance-id)
        (let [asset (find-asset new-state player instance-id)]
          (register-asset-triggers registry effect-catalog asset player))
        registry))

    :bashketball/move-asset
    (let [{:keys [instance-id]} action]
      (unregister-asset-triggers registry instance-id))

    :bashketball/create-token
    (let [{:keys [player placement target-player-id]} action
          ;; Get the created token from event-data in new-state
          token-instance                              (get-in new-state [:events (dec (count (:events new-state))) :created-token])]
      (case placement
        :placement/ASSET
        (register-asset-triggers registry effect-catalog token-instance player)

        :placement/ATTACH
        (let [team (state/get-basketball-player-team new-state target-player-id)]
          (register-attached-abilities registry effect-catalog token-instance target-player-id team))

        ;; Unknown placement, no registration
        registry))

    :bashketball/resolve-card
    (let [{:keys [instance-id target-player-id]} action
          ;; Get the original card info from the play-area in old-state
          play-area-card                         (state/find-card-in-play-area old-state instance-id)
          owner                                  (:played-by play-area-card)]
      (cond
        ;; Check if it became an attachment
        (and target-player-id
             (state/find-attachment new-state target-player-id instance-id))
        (let [attachment (state/find-attachment new-state target-player-id instance-id)
              team       (state/get-basketball-player-team new-state target-player-id)]
          (register-attached-abilities registry effect-catalog attachment target-player-id team))

        ;; Check if it became an asset
        (find-asset new-state owner instance-id)
        (let [asset (find-asset new-state owner instance-id)]
          (register-asset-triggers registry effect-catalog asset owner))

        ;; Otherwise went to discard, no registry update needed
        :else registry))

    ;; No registry changes for other actions
    registry))
