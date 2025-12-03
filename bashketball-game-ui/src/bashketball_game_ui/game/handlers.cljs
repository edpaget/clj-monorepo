(ns bashketball-game-ui.game.handlers
  "Pure handler functions for game UI interactions.

  Each function takes a context map describing the current state and returns
  an action descriptor map. The calling code is responsible for executing
  the action. This separation allows handlers to be tested independently.")

(defn hex-click-action
  "Determines what action to take when a hex is clicked.

  Takes a context map with:
  - :setup-mode - boolean, true if in setup phase
  - :is-my-turn - boolean, true if it's the player's turn
  - :selected-player - string or nil, currently selected player ID
  - :valid-setup-positions - set of [q r] positions valid for setup placement
  - :valid-moves - set of [q r] positions valid for movement

  Returns an action map or nil:
  - {:action :setup-place :player-id ... :position [q r]}
  - {:action :move :player-id ... :position [q r]}
  - nil if no valid action"
  [{:keys [setup-mode is-my-turn selected-player
           valid-setup-positions valid-moves]} q r]
  (cond
    (and setup-mode
         selected-player
         (contains? valid-setup-positions [q r]))
    {:action :setup-place :player-id selected-player :position [q r]}

    (and is-my-turn
         selected-player
         (contains? valid-moves [q r]))
    {:action :move :player-id selected-player :position [q r]}

    :else nil))

(defn player-click-action
  "Determines what action to take when a player token is clicked.

  Takes a context map with:
  - :pass-mode - boolean, true if in pass mode
  - :valid-pass-targets - set of player IDs that can receive a pass
  - :ball-holder-position - [q r] position of ball holder, or nil
  - :selected-player - string or nil, currently selected player ID

  Returns an action map:
  - {:action :pass :origin [q r] :target-player-id ...}
  - {:action :toggle-selection :player-id ... :currently-selected ...}"
  [{:keys [pass-mode valid-pass-targets ball-holder-position selected-player]} player-id]
  (if (and pass-mode (contains? valid-pass-targets player-id) ball-holder-position)
    {:action :pass
     :origin ball-holder-position
     :target-player-id player-id}
    {:action :toggle-selection
     :player-id player-id
     :currently-selected selected-player}))

(defn card-click-action
  "Determines card click behavior based on current mode.

  Takes a context map with:
  - :discard-mode - boolean, true if selecting cards for discard
  - :discard-cards - set of currently selected card slugs
  - :selected-card - string or nil, currently selected card slug

  Returns an action map:
  - {:action :toggle-discard :card-slug ... :new-set ...}
  - {:action :toggle-card-selection :card-slug ... :currently-selected ...}"
  [{:keys [discard-mode discard-cards selected-card]} card-slug]
  (if discard-mode
    {:action :toggle-discard
     :card-slug card-slug
     :new-set (if (contains? discard-cards card-slug)
                (disj discard-cards card-slug)
                (conj discard-cards card-slug))}
    {:action :toggle-card-selection
     :card-slug card-slug
     :currently-selected selected-card}))

(defn toggle-selection
  "Returns the new selection value when toggling.

  If current equals new-value, returns nil (deselect).
  Otherwise returns new-value (select)."
  [current new-value]
  (if (= current new-value) nil new-value))
