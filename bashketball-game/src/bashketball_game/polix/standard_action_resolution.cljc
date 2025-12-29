(ns bashketball-game.polix.standard-action-resolution
  "Standard action offense/defense resolution with Response asset integration.

   Handles the complete resolution sequence when a standard action is played:

   1. **Offense declares** - Player plays standard action and selects offense mode
   2. **Before event fires** - `:bashketball/standard-action.before` triggers fire
   3. **Response check** - Check defender's face-down Response assets for matches
   4. **Response prompt** - Offer Apply/Pass choice for each matching Response
   5. **Response execution** - Execute Response effects for applied responses
   6. **Offense execution** - Execute the offense mode effect
   7. **After event fires** - `:bashketball/standard-action.after` triggers fire

   Usage:

   ```clojure
   (require '[bashketball-game.polix.standard-action-resolution :as sar])

   ;; Check for matching Response assets
   (sar/find-matching-responses game-state registry event team)

   ;; Create response prompts
   (sar/create-response-prompts matching-responses)

   ;; Apply a response
   (sar/apply-response game-state registry response-asset)
   ```"
  (:require
   [bashketball-game.effect-catalog :as catalog]
   [bashketball-game.polix.triggers :as triggers]))

;; =============================================================================
;; Response Asset Discovery
;; =============================================================================

(defn get-face-down-response-assets
  "Returns all face-down Response assets for a team.

   Response assets are Team Asset cards with `:card-subtype/RESPONSE` that are
   played face-down and can be revealed when their trigger condition matches.

   Note: Team asset storage will be added when the full asset system is
   implemented. For now, returns assets from `:team-assets` in player state."
  [game-state team]
  (let [assets (get-in game-state [:players team :team-assets] [])]
    (filter
     (fn [asset]
       (and (:face-down asset)
            (some #(= % :card-subtype/RESPONSE)
                  (:card-subtypes asset))))
     assets)))

(defn get-response-trigger
  "Returns the response trigger definition from an asset's power.

   Extracts `:response/trigger` from the asset's `:asset-power` definition."
  [effect-catalog asset]
  (when-let [power (catalog/get-asset-power effect-catalog (:card-slug asset))]
    (get-in power [:asset/response :response/trigger])))

(defn get-response-prompt
  "Returns the response prompt text from an asset's power."
  [effect-catalog asset]
  (when-let [power (catalog/get-asset-power effect-catalog (:card-slug asset))]
    (get-in power [:asset/response :response/prompt])))

(defn get-response-effect
  "Returns the response effect definition from an asset's power."
  [effect-catalog asset]
  (when-let [power (catalog/get-asset-power effect-catalog (:card-slug asset))]
    (get-in power [:asset/response :response/effect])))

;; =============================================================================
;; Response Matching
;; =============================================================================

(defn matches-event?
  "Returns true if a trigger's event type matches the fired event."
  [trigger event]
  (let [trigger-event (:trigger/event trigger)
        event-type    (:type event)]
    (= trigger-event event-type)))

(defn evaluate-response-condition
  "Evaluates a response trigger's condition against the current context.

   Returns true if:
   - No condition defined (always matches)
   - Condition evaluates to satisfied

   Returns false if condition has conflicts or residuals.

   Note: Full condition evaluation using polix.unify will be implemented
   when the condition evaluation system is integrated."
  [_game-state trigger _event _registry]
  (let [condition (:trigger/condition trigger)]
    ;; For now, assume condition matches if no condition or any condition
    ;; Full condition evaluation would use polix.unify here
    (or (nil? condition) true)))

(defn find-matching-responses
  "Finds Response assets that match the current event.

   Takes the game state, effect catalog, trigger registry, event, and
   defending team. Returns a sequence of matching response assets with
   their trigger and effect definitions attached:

   ```clojure
   [{:asset {...}
     :trigger {...}
     :prompt \"Reveal Defensive Timeout?\"
     :effect {...}}]
   ```"
  [game-state effect-catalog registry event defending-team]
  (let [face-down-responses (get-face-down-response-assets game-state defending-team)]
    (reduce
     (fn [matches asset]
       (let [trigger (get-response-trigger effect-catalog asset)]
         (if (and trigger
                  (matches-event? trigger event)
                  (evaluate-response-condition game-state trigger event registry))
           (conj matches
                 {:asset   asset
                  :trigger trigger
                  :prompt  (get-response-prompt effect-catalog asset)
                  :effect  (get-response-effect effect-catalog asset)})
           matches)))
     []
     face-down-responses)))

;; =============================================================================
;; Response Prompts
;; =============================================================================

(defn create-response-choice
  "Creates a choice option for a single Response asset.

   Returns a ChoiceOption map for the pending choice system."
  [response-info]
  {:id       (keyword (str "response-" (get-in response-info [:asset :instance-id])))
   :label    (:prompt response-info)
   :response response-info})

(defn create-response-prompt
  "Creates a prompt offering the defender to apply or pass on a Response.

   Returns a pending choice action map that can be applied to game state."
  [response-info defending-team]
  {:type        :bashketball/offer-choice
   :choice-type :response-apply
   :options     [{:id :apply :label "Apply"}
                 {:id :pass :label "Pass"}]
   :waiting-for defending-team
   :context     {:response-asset (:asset response-info)
                 :response-effect (:effect response-info)}})

;; =============================================================================
;; Response Execution
;; =============================================================================

(defn reveal-response-asset
  "Marks a Response asset as revealed (face-up).

   Returns the updated game state with the asset no longer face-down.
   The `team` parameter specifies which team owns the asset."
  [game-state team asset]
  (let [update-fn (fn [a]
                    (if (= (:instance-id a) (:instance-id asset))
                      (assoc a :face-down false :revealed true)
                      a))]
    (update-in game-state [:players team :team-assets] #(mapv update-fn %))))

(defn build-response-context
  "Builds an execution context for a Response effect.

   The context includes:
   - `:self/team` - the defending team
   - `:self/asset-id` - the Response asset's instance ID
   - Event fields from the triggering event"
  [asset defending-team event]
  (merge
   {:self/team    defending-team
    :self/asset-id (:instance-id asset)}
   (dissoc event :type)))

;; =============================================================================
;; Standard Action Resolution Flow
;; =============================================================================

(defn create-standard-action-event
  "Creates a standard action event for trigger firing.

   Takes the event suffix (\"before\" or \"after\"), the action preparation,
   mode, targets, and acting team."
  [suffix card mode targets acting-team]
  {:type        (keyword "bashketball" (str "standard-action." suffix))
   :card-slug   (:card-slug card)
   :card-id     (:instance-id card)
   :mode        mode
   :targets     targets
   :acting-team acting-team})

(defn fire-standard-action-event
  "Fires a standard action event through the trigger system.

   Returns the result from [[triggers/fire-bashketball-event]] with
   `:state`, `:registry`, and `:prevented?` keys."
  [context event]
  (triggers/fire-bashketball-event context event))

(defn get-defending-team
  "Returns the team that can respond to a standard action.

   For offense actions, this is the opposing team.
   For defense actions, this is the acting team."
  [acting-team mode]
  (if (= mode :offense)
    (if (= acting-team :team/HOME) :team/AWAY :team/HOME)
    acting-team))

(defn check-for-responses
  "Checks if the defending team has any matching Response assets.

   Returns a map with:
   - `:has-responses?` - true if matching responses found
   - `:responses` - sequence of matching response info maps
   - `:defending-team` - the team that can respond"
  [game-state effect-catalog registry event defending-team]
  (let [responses (find-matching-responses game-state effect-catalog registry event defending-team)]
    {:has-responses? (seq responses)
     :responses      responses
     :defending-team defending-team}))

(defn resolution-step-before-event
  "Fires the before event for a standard action.

   Returns:
   - `:state` - possibly modified state
   - `:registry` - possibly modified registry
   - `:prevented?` - true if a before trigger prevented the action"
  [context card mode targets acting-team]
  (let [event (create-standard-action-event "before" card mode targets acting-team)]
    (fire-standard-action-event context event)))

(defn resolution-step-after-event
  "Fires the after event for a standard action.

   Returns the result from firing the after event."
  [context card mode targets acting-team]
  (let [event (create-standard-action-event "after" card mode targets acting-team)]
    (fire-standard-action-event context event)))
