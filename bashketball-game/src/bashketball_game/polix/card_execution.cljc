(ns bashketball-game.polix.card-execution
  "Card execution and resolution for all playable card types.

   Handles the execution flow when cards are played:

   1. **Fuel processing** - When playing a card, 1 card must be discarded as fuel
   2. **Signal detection** - Coaching cards have signals that fire when discarded as fuel
   3. **Signal ordering** - Multiple signals prompt player for execution order
   4. **Play resolution** - Play cards execute their `:play/effect`
   5. **Call resolution** - Coaching cards execute their `:call/effect`
   6. **Mode selection** - Standard actions and split plays prompt for offense/defense
   7. **Virtual actions** - Standard actions used by discarding 3 cards

   Usage:

   ```clojure
   (require '[bashketball-game.polix.card-execution :as exec])

   ;; For play cards and coaching calls
   (def prep (exec/prepare-card-execution catalog state main-card fuel-cards targets team))
   (exec/execute-without-ordering state prep team)

   ;; For standard actions and split plays
   (def action-prep (exec/prepare-action-execution catalog state card fuel-cards team))
   (exec/execute-action-with-mode catalog state action-prep :offense targets team)

   ;; For virtual standard actions (discard 3 cards)
   (def virtual-prep (exec/prepare-virtual-action catalog state \"shoot-block\" id fuel-cards team))
   (exec/execute-virtual-action catalog state virtual-prep :offense targets team)
   ```"
  (:require
   [bashketball-game.effect-catalog :as catalog]
   [bashketball-game.polix.triggers :as triggers]
   [polix.effects.core :as fx]))

;; =============================================================================
;; Effect Resolution Helpers
;; =============================================================================

(defn normalize-effect
  "Normalizes an effect definition from schema format to runtime format.

   Converts `:effect/type` to `:type` recursively through nested effects.
   This allows effects defined with the schema key to be applied via polix."
  [effect]
  (when effect
    (cond-> effect
      (:effect/type effect) (-> (assoc :type (:effect/type effect))
                                (dissoc :effect/type))
      (:effects effect)     (update :effects #(mapv normalize-effect %)))))

(defn get-play-effect
  "Returns the play effect definition for a play card.

   Looks up the card's `:play` definition from the catalog and returns
   the `:play/effect`. Returns nil if not a play card or no effect defined."
  [effect-catalog card-slug]
  (when-let [play-def (catalog/get-play effect-catalog card-slug)]
    (:play/effect play-def)))

(defn get-call-effect
  "Returns the call effect definition for a coaching card.

   Looks up the card's `:call` definition from the catalog and returns
   the `:call/effect`. Returns nil if not a coaching card or no call defined."
  [effect-catalog card-slug]
  (when-let [call-def (catalog/get-call effect-catalog card-slug)]
    (:call/effect call-def)))

(defn get-signal
  "Returns the signal definition for a coaching card.

   Returns the full signal definition map including `:signal/effect` and
   `:signal/trigger`. Returns nil if not a coaching card or no signal defined."
  [effect-catalog card-slug]
  (catalog/get-signal effect-catalog card-slug))

(defn get-offense-effect
  "Returns the offense action effect for a standard action or split play.

   Looks up the card's `:offense` definition and returns `:action/effect`.
   Returns nil if not an action card or no offense mode defined."
  [effect-catalog card-slug]
  (when-let [offense (catalog/get-offense effect-catalog card-slug)]
    (:action/effect offense)))

(defn get-defense-effect
  "Returns the defense action effect for a standard action or split play.

   Looks up the card's `:defense` definition and returns `:action/effect`.
   Returns nil if not an action card or no defense mode defined."
  [effect-catalog card-slug]
  (when-let [defense (catalog/get-defense effect-catalog card-slug)]
    (:action/effect defense)))

(defn get-available-modes
  "Returns the available modes for a standard action or split play.

   Returns a map with:
   - `:offense` - the offense ActionModeDef (or nil)
   - `:defense` - the defense ActionModeDef (or nil)
   - `:has-offense?` - true if offense mode is available
   - `:has-defense?` - true if defense mode is available

   Returns nil if the card has neither offense nor defense mode."
  [effect-catalog card-slug]
  (let [offense (catalog/get-offense effect-catalog card-slug)
        defense (catalog/get-defense effect-catalog card-slug)]
    (when (or offense defense)
      {:offense offense
       :defense defense
       :has-offense? (some? offense)
       :has-defense? (some? defense)})))

;; =============================================================================
;; Context Building
;; =============================================================================

(defn build-effect-context
  "Builds an execution context for effect resolution.

   The context provides bindings that effect definitions can reference:
   - `:self/id` - the player ID associated with this effect
   - `:self/team` - the team that played the card
   - `:target/*` - any target IDs from the targets map
   - `:card/instance-id` - the card instance being resolved
   - `:card/slug` - the card's slug

   Takes the game state, card instance, team, and targets map."
  [_state card-instance team targets]
  (merge
   {:self/team team
    :card/instance-id (:instance-id card-instance)
    :card/slug (:card-slug card-instance)}
   (when-let [self-id (:self-id targets)]
     {:self/id self-id})
   ;; Include all target bindings
   (reduce-kv
    (fn [m k v]
      (if (and (keyword? k) (= "target" (namespace k)))
        (assoc m k v)
        m))
    {}
    targets)))

(defn build-signal-context
  "Builds an execution context for a signal effect.

   Signals fire when a coaching card is discarded as fuel for another card.
   The context includes:
   - `:event/card-instance-id` - the fuel card's instance ID
   - `:event/main-card` - the main card being fueled
   - `:event/main-card-targets` - targets for the main card

   The signal can reference the main card's targets to apply bonuses to the
   same target the main card is affecting."
  [_state fuel-card main-card main-targets team]
  {:self/team team
   :card/instance-id (:instance-id fuel-card)
   :card/slug (:card-slug fuel-card)
   :event/card-instance-id (:instance-id fuel-card)
   :event/main-card main-card
   :event/main-card-targets main-targets})

;; =============================================================================
;; Signal Collection and Processing
;; =============================================================================

(defn collect-signals
  "Collects signal definitions from fuel cards.

   Returns a sequence of maps, each containing:
   - `:fuel-card` - the card instance that was discarded
   - `:signal` - the signal definition from the catalog

   Only includes cards that have signals defined."
  [effect-catalog fuel-cards]
  (reduce
   (fn [signals fuel-card]
     (if-let [signal (get-signal effect-catalog (:card-slug fuel-card))]
       (conj signals {:fuel-card fuel-card
                      :signal signal})
       signals))
   []
   fuel-cards))

(defn fire-fuel-discarded-event
  "Fires the `:bashketball/card-discarded-as-fuel` event.

   This event allows triggers to respond when a card is discarded as fuel.
   The event includes:
   - `:type` - `:bashketball/card-discarded-as-fuel.at`
   - `:card-instance-id` - the fuel card's instance ID
   - `:card-slug` - the fuel card's slug
   - `:main-card` - the main card being fueled
   - `:main-card-targets` - targets for the main card"
  [{:keys [state registry]} fuel-card main-card main-targets team]
  (let [event {:type :bashketball/card-discarded-as-fuel.at
               :card-instance-id (:instance-id fuel-card)
               :card-slug (:card-slug fuel-card)
               :main-card main-card
               :main-card-targets main-targets
               :team team}]
    (triggers/fire-bashketball-event {:state state :registry registry} event)))

;; =============================================================================
;; Signal Resolution
;; =============================================================================

(defn needs-signal-ordering?
  "Returns true if there are multiple signals that need ordering.

   When multiple coaching cards are discarded as fuel, the player chooses
   the order in which their signals resolve."
  [signals]
  (> (count signals) 1))

(defn create-signal-ordering-prompt
  "Creates a prompt for the player to choose signal execution order.

   Returns a map with:
   - `:prompt-type` - `:signal-ordering`
   - `:signals` - the signals that need ordering
   - `:team` - the team that needs to choose"
  [signals team]
  {:prompt-type :signal-ordering
   :signals signals
   :team team})

(defn resolve-signal
  "Resolves a single signal effect.

   Takes the execution context, signal info (from [[collect-signals]]),
   main card info, and team. Returns a map containing:
   - `:effect` - the signal's effect definition to be applied
   - `:context` - the execution context for the effect
   - `:signal` - the original signal definition"
  [state signal-info main-card main-targets team]
  (let [{:keys [fuel-card signal]} signal-info
        context                    (build-signal-context state fuel-card main-card main-targets team)]
    {:effect (:signal/effect signal)
     :context context
     :signal signal
     :fuel-card fuel-card}))

(defn resolve-signals-in-order
  "Resolves signals in the given order.

   Takes ordered signal infos (from [[collect-signals]]) and returns a
   sequence of resolution results, each containing the effect and context.

   Signals are resolved in order, with each signal's context including
   the main card targets so they can affect the same targets."
  [state ordered-signals main-card main-targets team]
  (mapv
   (fn [signal-info]
     (resolve-signal state signal-info main-card main-targets team))
   ordered-signals))

;; =============================================================================
;; Card Resolution
;; =============================================================================

(defn resolve-play-card
  "Resolves a play card, returning its effect and execution context.

   Returns a map with:
   - `:effect` - the play effect definition
   - `:context` - the execution context
   - `:card` - the card instance
   - `:card-type` - `:play`

   Returns nil if the card has no play effect."
  [effect-catalog state card-instance targets team]
  (when-let [effect (get-play-effect effect-catalog (:card-slug card-instance))]
    {:effect effect
     :context (build-effect-context state card-instance team targets)
     :card card-instance
     :card-type :play}))

(defn resolve-coaching-call
  "Resolves a coaching card's call effect, returning its effect and context.

   Returns a map with:
   - `:effect` - the call effect definition
   - `:context` - the execution context
   - `:card` - the card instance
   - `:card-type` - `:call`

   Returns nil if the card has no call effect."
  [effect-catalog state card-instance targets team]
  (when-let [effect (get-call-effect effect-catalog (:card-slug card-instance))]
    {:effect effect
     :context (build-effect-context state card-instance team targets)
     :card card-instance
     :card-type :call}))

;; =============================================================================
;; Complete Card Execution Flow
;; =============================================================================

(defn prepare-card-execution
  "Prepares a complete card execution with fuel processing.

   This is the main entry point for card execution. Takes:
   - `effect-catalog` - the effect catalog for lookups
   - `state` - current game state
   - `main-card` - the card instance being played
   - `fuel-cards` - cards discarded as fuel (usually 1, or 3 for virtual actions)
   - `targets` - target bindings for the card
   - `team` - the team playing the card

   Returns a map with:
   - `:signals` - collected signals from fuel cards (may be empty)
   - `:needs-ordering?` - true if multiple signals need player ordering
   - `:ordering-prompt` - if ordering needed, the prompt to show
   - `:main-resolution` - the main card's effect resolution (or nil)

   If `:needs-ordering?` is true, the caller should present the ordering
   prompt to the player, then call [[execute-with-signal-order]] with the
   chosen order."
  [effect-catalog state main-card fuel-cards targets team]
  (let [signals         (collect-signals effect-catalog fuel-cards)
        main-resolution (or (resolve-play-card effect-catalog state main-card targets team)
                            (resolve-coaching-call effect-catalog state main-card targets team))]
    {:signals signals
     :needs-ordering? (needs-signal-ordering? signals)
     :ordering-prompt (when (needs-signal-ordering? signals)
                        (create-signal-ordering-prompt signals team))
     :main-resolution main-resolution
     :fuel-cards fuel-cards}))

(defn execute-with-signal-order
  "Executes card effects with a specified signal order.

   Takes the preparation result from [[prepare-card-execution]] and the
   ordered signals (as returned from the ordering prompt). Returns a
   sequence of all effects to apply in order:
   1. Signal effects (in the specified order)
   2. Main card effect

   Each effect in the sequence is a map with `:effect` and `:context`."
  [state preparation ordered-signals team]
  (let [{:keys [main-resolution]} preparation
        main-card                 (:card main-resolution)
        main-targets              (:context main-resolution)
        signal-resolutions        (resolve-signals-in-order
                                   state ordered-signals main-card main-targets team)]
    (cond-> signal-resolutions
      main-resolution (conj main-resolution))))

(defn execute-without-ordering
  "Executes card effects when no signal ordering is needed.

   Takes the preparation result from [[prepare-card-execution]]. Returns
   a sequence of all effects to apply in order:
   1. Signal effect (if any, single signal case)
   2. Main card effect"
  [state preparation team]
  (let [{:keys [signals main-resolution]} preparation
        main-card                         (:card main-resolution)
        main-targets                      (:context main-resolution)
        signal-resolutions                (when (seq signals)
                                            (resolve-signals-in-order
                                             state signals main-card main-targets team))]
    (cond-> (vec (or signal-resolutions []))
      main-resolution (conj main-resolution))))

;; =============================================================================
;; Standard Action & Split Play Execution
;; =============================================================================

(defn create-mode-choice-prompt
  "Creates a prompt for the player to choose offense or defense mode.

   Returns a map with:
   - `:prompt-type` - `:mode-choice`
   - `:card` - the card instance requiring mode choice
   - `:modes` - available modes info from [[get-available-modes]]
   - `:team` - the team that needs to choose"
  [card-instance modes team]
  {:prompt-type :mode-choice
   :card card-instance
   :modes modes
   :team team})

(defn resolve-action-mode
  "Resolves the chosen mode (offense or defense) of a standard action or split play.

   Takes the effect catalog, game state, card instance, chosen mode (`:offense`
   or `:defense`), targets, and team. Returns a map with:
   - `:effect` - the action mode's effect definition
   - `:context` - the execution context
   - `:card` - the card instance
   - `:card-type` - `:action`
   - `:mode` - the chosen mode

   Returns nil if the card has no effect for the chosen mode."
  [effect-catalog state card-instance mode targets team]
  (let [card-slug (:card-slug card-instance)
        effect    (case mode
                    :offense (get-offense-effect effect-catalog card-slug)
                    :defense (get-defense-effect effect-catalog card-slug)
                    nil)]
    (when effect
      {:effect effect
       :context (build-effect-context state card-instance team targets)
       :card card-instance
       :card-type :action
       :mode mode})))

(defn prepare-action-execution
  "Prepares a standard action or split play for execution.

   Like [[prepare-card-execution]] but for cards requiring mode choice.
   Takes:
   - `effect-catalog` - the effect catalog for lookups
   - `state` - current game state
   - `card-instance` - the card instance being played
   - `fuel-cards` - cards discarded as fuel
   - `team` - the team playing the card

   Returns a map with:
   - `:signals` - collected signals from fuel cards (may be empty)
   - `:needs-ordering?` - true if multiple signals need player ordering
   - `:ordering-prompt` - if ordering needed, the prompt to show
   - `:needs-mode-choice?` - true (action cards always need mode choice)
   - `:mode-prompt` - the mode choice prompt
   - `:modes` - available modes info

   The caller should:
   1. Handle signal ordering if needed
   2. Present mode choice prompt
   3. Call [[execute-action-with-mode]] with the chosen mode"
  [effect-catalog _state card-instance fuel-cards team]
  (let [signals (collect-signals effect-catalog fuel-cards)
        modes   (get-available-modes effect-catalog (:card-slug card-instance))]
    {:signals signals
     :needs-ordering? (needs-signal-ordering? signals)
     :ordering-prompt (when (needs-signal-ordering? signals)
                        (create-signal-ordering-prompt signals team))
     :needs-mode-choice? true
     :mode-prompt (create-mode-choice-prompt card-instance modes team)
     :modes modes
     :card card-instance
     :fuel-cards fuel-cards}))

(defn execute-action-with-mode
  "Executes an action card with the chosen mode.

   Takes the preparation result from [[prepare-action-execution]], the
   chosen mode (`:offense` or `:defense`), targets, and optionally
   ordered signals (if ordering was needed). Returns a sequence of all
   effects to apply in order:
   1. Signal effects (in order)
   2. Action mode effect

   Each effect in the sequence is a map with `:effect` and `:context`."
  [effect-catalog state preparation mode targets team & {:keys [ordered-signals]}]
  (let [{:keys [signals card]} preparation
        signal-list            (or ordered-signals signals)
        action-resolution      (resolve-action-mode effect-catalog state card mode targets team)
        signal-resolutions     (when (seq signal-list)
                                 (resolve-signals-in-order
                                  state signal-list card (:context action-resolution) team))]
    (cond-> (vec (or signal-resolutions []))
      action-resolution (conj action-resolution))))

;; =============================================================================
;; Virtual Standard Action Flow
;; =============================================================================

(defn create-virtual-card-instance
  "Creates a virtual card instance for a standard action used without the card.

   Virtual cards are created when a player uses a standard action by discarding
   3 cards instead of having the actual card. Returns a card instance map with:
   - `:instance-id` - a generated unique ID
   - `:card-slug` - the standard action's slug
   - `:virtual` - true, indicating this is not a real card"
  [card-slug instance-id]
  {:instance-id instance-id
   :card-slug card-slug
   :virtual true})

(defn prepare-virtual-action
  "Prepares a virtual standard action for execution.

   Virtual standard actions are used when a player wants to use a standard
   action (like Shoot/Block) without having the card, by discarding 3 cards.

   Takes:
   - `effect-catalog` - the effect catalog for lookups
   - `state` - current game state
   - `card-slug` - the slug of the standard action to use
   - `instance-id` - the generated instance ID for the virtual card
   - `fuel-cards` - the 3 cards discarded as fuel
   - `team` - the team playing the action

   Returns a preparation map similar to [[prepare-action-execution]] but
   with `:virtual` set to true. The virtual card should be staged in the
   play area before calling this, and removed after resolution."
  [effect-catalog _state card-slug instance-id fuel-cards team]
  (let [virtual-card (create-virtual-card-instance card-slug instance-id)
        signals      (collect-signals effect-catalog fuel-cards)
        modes        (get-available-modes effect-catalog card-slug)]
    {:signals signals
     :needs-ordering? (needs-signal-ordering? signals)
     :ordering-prompt (when (needs-signal-ordering? signals)
                        (create-signal-ordering-prompt signals team))
     :needs-mode-choice? true
     :mode-prompt (create-mode-choice-prompt virtual-card modes team)
     :modes modes
     :card virtual-card
     :fuel-cards fuel-cards
     :virtual true}))

(defn execute-virtual-action
  "Executes a virtual standard action with the chosen mode.

   Same as [[execute-action-with-mode]] but for virtual cards.
   The virtual card is not sent to discard after resolution - it simply
   disappears from the play area."
  [effect-catalog state preparation mode targets team & {:keys [ordered-signals]}]
  (execute-action-with-mode effect-catalog state preparation mode targets team
                            :ordered-signals ordered-signals))

;; =============================================================================
;; Event-Driven Play Card Execution
;; =============================================================================

(defn execute-play-card
  "Executes a play card through the event-driven system.

   Fires events allowing card abilities and Response assets to intercept.
   Processes fuel card signals before the main play effect.

   Takes:
   - `effect-catalog` - for looking up effects and signals
   - `state` - current game state
   - `registry` - trigger registry
   - `main-card` - the play card instance being played
   - `fuel-cards` - cards discarded as fuel (usually 1)
   - `targets` - target bindings map (e.g., `{:target/player-id \"p1\"}`)
   - `team` - team playing the card

   Returns an effect result map with `:state`, `:registry`, `:event-counters`,
   and possibly `:pending` if waiting for a choice."
  [effect-catalog state registry main-card fuel-cards targets team]
  (let [play-def          (catalog/get-play effect-catalog (:card-slug main-card))
        play-effect       (normalize-effect (:play/effect play-def))
        effect-context    (build-effect-context state main-card team targets)
        ;; Collect signal info for fuel cards - normalize signal effects too
        fuel-with-signals (mapv (fn [fuel-card]
                                  (let [signal (get-signal effect-catalog (:card-slug fuel-card))]
                                    (cond-> fuel-card
                                      signal (assoc :signal-effect (normalize-effect (:signal/effect signal))
                                                    :signal-context (build-signal-context
                                                                     state fuel-card main-card targets team)))))
                                fuel-cards)]
    (fx/apply-effect state
                     {:type           :bashketball/play-card
                      :main-card      main-card
                      :fuel-cards     fuel-with-signals
                      :targets        targets
                      :play-effect    play-effect
                      :effect-context effect-context
                      :effect-catalog effect-catalog}
                     {:bindings {:self/team team}}
                     {:registry       registry
                      :validate?      false
                      :effect-catalog effect-catalog})))

(defn execute-play-card-with-ordering
  "Executes a play card, handling signal ordering when needed.

   If multiple coaching cards with signals are discarded as fuel, prompts
   the player to choose the signal execution order before proceeding.

   Returns either:
   - An effect result if no ordering needed or after ordering is resolved
   - A map with `:pending` choice if signal ordering is required"
  [effect-catalog state registry main-card fuel-cards targets team]
  (let [signals (collect-signals effect-catalog fuel-cards)]
    (if (needs-signal-ordering? signals)
      ;; Return pending choice for signal order
      {:state   (assoc state :pending-choice
                       {:id          (str #?(:clj (java.util.UUID/randomUUID)
                                             :cljs (random-uuid)))
                        :type        :signal-ordering
                        :options     (mapv (fn [{:keys [fuel-card]}]
                                             {:id    (keyword (:instance-id fuel-card))
                                              :label (:card-slug fuel-card)})
                                           signals)
                        :waiting-for team
                        :context     {:signals        signals
                                      :main-card      main-card
                                      :fuel-cards     fuel-cards
                                      :targets        targets
                                      :effect-catalog effect-catalog}
                        :continuation {:type           :bashketball/play-card
                                       :main-card      main-card
                                       :targets        targets
                                       :effect-catalog effect-catalog}})
       :pending {:type :choice}}
      ;; No ordering needed
      (execute-play-card effect-catalog state registry main-card fuel-cards targets team))))
