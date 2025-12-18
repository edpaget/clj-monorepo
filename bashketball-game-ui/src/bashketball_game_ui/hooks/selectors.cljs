(ns bashketball-game-ui.hooks.selectors
  "Focused selector hooks for game context.

  Provides granular access to game context values, enabling components
  to subscribe to only the data they need. Use these instead of
  destructuring the full context to reduce unnecessary re-renders."
  (:require
   [bashketball-game-ui.context.game :refer [game-context]]
   [uix.core :refer [use-context]]))

(defn use-game
  "Returns the current game record."
  []
  (:game (use-context game-context)))

(defn use-game-state
  "Returns the inner game state from bashketball-game."
  []
  (:game-state (use-context game-context)))

(defn use-catalog
  "Returns the card catalog map `{slug -> card}`."
  []
  (:catalog (use-context game-context)))

(defn use-my-team
  "Returns the current user's team keyword (:team/HOME or :team/AWAY)."
  []
  (:my-team (use-context game-context)))

(defn use-is-my-turn
  "Returns true if it's the current user's turn to act."
  []
  (:is-my-turn (use-context game-context)))

(defn use-actions
  "Returns the actions dispatch functions from use-game-actions."
  []
  (:actions (use-context game-context)))

(defn use-connection-status
  "Returns connection status map with :loading, :error, and :connected."
  []
  (let [ctx (use-context game-context)]
    {:loading   (:loading ctx)
     :error     (:error ctx)
     :connected (:connected ctx)}))

(defn use-selection
  "Returns selection machine state and functions.

  Returns map with:
  - :mode - Current selection state keyword
  - :data - Selection data (selected player, cards, etc.)
  - :send - Function to send events
  - :can-send? - Predicate to check if event type is valid"
  []
  (let [ctx (use-context game-context)]
    {:mode      (:selection-mode ctx)
     :data      (:selection-data ctx)
     :send      (:send ctx)
     :can-send? (:can-send? ctx)}))

(defn use-discard-machine
  "Returns discard machine state and functions.

  Returns map with:
  - :machine - Full machine state {:state :data}
  - :send - Function to send events
  - :can-send? - Predicate to check if event type is valid"
  []
  (let [ctx (use-context game-context)]
    {:machine   (:discard-machine ctx)
     :send      (:send-discard ctx)
     :can-send? (:can-send-discard? ctx)}))

(defn use-substitute-machine
  "Returns substitute machine state and functions.

  Returns map with:
  - :machine - Full machine state {:state :data}
  - :send - Function to send events
  - :can-send? - Predicate to check if event type is valid"
  []
  (let [ctx (use-context game-context)]
    {:machine   (:substitute-machine ctx)
     :send      (:send-substitute ctx)
     :can-send? (:can-send-substitute? ctx)}))

(defn use-peek-machine
  "Returns peek machine state and functions.

  Returns map with:
  - :machine - Full machine state {:state :data}
  - :send - Function to send events
  - :can-send? - Predicate to check if event type is valid"
  []
  (let [ctx (use-context game-context)]
    {:machine   (:peek-machine ctx)
     :send      (:send-peek ctx)
     :can-send? (:can-send-peek? ctx)}))

(defn use-detail-modal
  "Returns detail modal state with :open?, :card-slug, :show, :close."
  []
  (:detail-modal (use-context game-context)))

(defn use-fate-reveal
  "Returns fate reveal modal state with :open?, :fate, :show, :close."
  []
  (:fate-reveal (use-context game-context)))

(defn use-create-token-modal
  "Returns create token modal state with :open?, :show, :close."
  []
  (:create-token-modal (use-context game-context)))

(defn use-attach-ability-modal
  "Returns attach ability modal state with :open?, :instance-id, :card-slug, :played-by, :show, :close."
  []
  (:attach-ability-modal (use-context game-context)))
