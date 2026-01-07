(ns bashketball-game.polix.standard-action-resolution
  "Standard action offense/defense resolution with Response asset integration.

   Handles the complete resolution sequence when a standard action is played:

   1. **Offense declares** - Player plays standard action and selects offense mode
   2. **Before event fires** - `:bashketball/standard-action.before` triggers fire
   3. **Response triggers returned** - Response triggers from registry are returned
   4. **Response prompt** - Offer Apply/Pass choice for each matching Response
   5. **Response execution** - Execute Response effects for applied responses
   6. **Offense execution** - Execute the offense mode effect
   7. **After event fires** - `:bashketball/standard-action.after` triggers fire

   Response triggers are registered via [[card-effects/register-asset-triggers]]
   with `:response? true`. When events fire through [[triggers/fire-request-event]],
   response triggers are returned (not fired) for the caller to build choice prompts.

   Usage:

   ```clojure
   (require '[bashketball-game.polix.standard-action-resolution :as sar])

   ;; Build response chain from triggers returned by fire-request-event
   (sar/build-response-chain-from-triggers response-triggers offense-cont)

   ;; Reveal an asset when Apply is chosen
   (sar/reveal-response-asset game-state team asset)
   ```"
  (:require
   [bashketball-game.polix.triggers :as triggers]))

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
;; Orchestration Functions
;; =============================================================================

(defn build-offense-continuation
  "Builds the offense continuation: skill test → result evaluation.

  Takes a params map with `:action-type`, `:attacker-id`, `:defender-id`,
  `:success-effect`, and `:failure-effect`. Returns an effect that chains
  skill test execution to result evaluation."
  [{:keys [action-type attacker-id defender-id success-effect failure-effect]}]
  {:type :bashketball/execute-skill-test-flow
   :action-type action-type
   :attacker-id attacker-id
   :defender-id defender-id
   :result-continuation {:type :bashketball/evaluate-skill-test-result
                         :success-effect success-effect
                         :failure-effect failure-effect
                         :after-event-params {:action-type action-type
                                              :attacker-id attacker-id
                                              :defender-id defender-id}}})

(defn build-response-chain
  "Builds nested continuation chain for response prompts.

  Each response becomes an offer-choice with Apply/Pass options,
  where the continuation is either the next response or the offense continuation.
  Responses are processed in reverse order so the first response in the list
  is the outermost (first prompted)."
  [responses offense-continuation]
  (reduce
   (fn [next-cont {:keys [asset prompt effect]}]
     {:type :bashketball/offer-choice
      :choice-type :response-prompt
      :options [{:id :apply :label "Apply"}
                {:id :pass :label "Pass"}]
      :waiting-for (:owner asset)
      :context {:response-asset asset
                :response-prompt prompt}
      :continuation {:type :bashketball/process-response-choice
                     :response-asset asset
                     :response-effect effect
                     :next-continuation next-cont}})
   offense-continuation
   (reverse responses)))

(defn build-response-chain-from-triggers
  "Builds nested continuation chain from response triggers.

  Takes response triggers returned by [[fire-request-event]] and builds
  the same nested Apply/Pass choice continuations. Each trigger's `:self`
  identifies the asset instance, `:owner` is the team, and `:effect` contains
  `:prompt` and `:response-effect`."
  [response-triggers next-continuation]
  (reduce
   (fn [next-cont trigger]
     (let [asset-instance-id (:self trigger)
           owner             (:owner trigger)
           prompt            (get-in trigger [:effect :prompt] "Apply response?")
           response-effect   (get-in trigger [:effect :response-effect])]
       {:type :bashketball/offer-choice
        :choice-type :response-prompt
        :options [{:id :apply :label "Apply"}
                  {:id :pass :label "Pass"}]
        :waiting-for owner
        :context {:response-asset-id asset-instance-id
                  :response-prompt prompt}
        :continuation {:type :bashketball/process-response-choice
                       :response-asset {:instance-id asset-instance-id :owner owner}
                       :response-effect response-effect
                       :next-continuation next-cont}}))
   next-continuation
   (reverse response-triggers)))

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
