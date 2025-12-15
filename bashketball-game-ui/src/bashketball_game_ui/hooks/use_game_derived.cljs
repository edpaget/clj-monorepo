(ns bashketball-game-ui.hooks.use-game-derived
  "Hook for computing derived game data.

  Centralizes all memoized game state computations in one place,
  making them available to any component via [[use-game-derived]]."
  (:require
   [bashketball-game-ui.context.game :refer [use-game-context]]
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game-ui.game.selectors :as sel]
   [uix.core :refer [use-memo]]))

(defn use-game-derived
  "Returns all derived game data, properly memoized.

  Consumes [[use-game-context]] internally and computes commonly needed
  values like valid moves, player data, and setup state.

  Returns a map with:
  - `:opponent-team` - The opposing team keyword
  - `:phase` - Current game phase
  - `:setup-mode` - Boolean, true if in setup phase
  - `:my-player` - Current player's full player record
  - `:my-players` - Map of current player's team players
  - `:my-hand` - Vector of cards in hand
  - `:opponent` - Opponent's player record
  - `:home-players` - Map of home team players
  - `:away-players` - Map of away team players
  - `:ball-holder-id` - ID of player holding the ball, or nil
  - `:score` - Score map by team
  - `:active-player` - Team keyword of active player
  - `:events` - Game event log
  - `:play-area` - Vector of cards in the shared play area
  - `:selected-player-id` - Currently selected player ID
  - `:pass-active` - Boolean, true if pass mode active
  - `:ball-active` - Boolean, true if ball mode active
  - `:valid-moves` - Set of valid move positions
  - `:valid-pass-targets` - Set of valid pass target player IDs
  - `:valid-setup-positions` - Set of valid setup positions
  - `:setup-placed-count` - Number of players placed during setup
  - `:my-setup-complete` - Boolean, true if current team setup complete (3 placed)
  - `:both-teams-ready` - Boolean, true if both teams ready
  - `:my-on-court-players` - Vector of players currently on court
  - `:my-off-court-players` - Vector of players currently off court
  - `:discard-active` - Boolean, true if discard mode active
  - `:discard-cards` - Set of cards selected for discard
  - `:selected-card` - Currently selected card slug
  - `:standard-action-active` - Boolean, true if standard action mode active
  - `:standard-action-step` - Keyword, :select-cards or :select-action
  - `:standard-action-cards` - Set of cards selected for standard action discard"
  []
  (let [{:keys [game-state my-team is-my-turn selection pass discard ball-mode standard-action-mode]}
        (use-game-context)

        ;; Extract UI state values
        selected-player-id                                                                            (:selected-player selection)
        pass-active                                                                                   (:active pass)
        ball-active                                                                                   (:active ball-mode)
        discard-active                                                                                (:active discard)
        discard-cards                                                                                 (:cards discard)
        selected-card                                                                                 (:selected-card selection)
        standard-action-active                                                                        (:active standard-action-mode)
        standard-action-step                                                                          (:step standard-action-mode)
        standard-action-cards                                                                         (:cards standard-action-mode)

        ;; Basic derived values (no memoization needed)
        opponent-team                                                                                 (sel/opponent-team my-team)
        phase                                                                                         (:phase game-state)
        setup-mode                                                                                    (sel/setup-mode? phase)
        ball-holder-id                                                                                (get-in game-state [:ball :holder-id])
        score                                                                                         (:score game-state)
        active-player                                                                                 (:active-player game-state)
        events                                                                                        (:events game-state)
        play-area                                                                                     (or (:play-area game-state) [])

        ;; Memoized player data
        {:keys [my-player my-players my-hand]}
        (use-memo
         #(sel/my-player-data game-state my-team)
         [game-state my-team])

        opponent
        (use-memo
         #(sel/opponent-data game-state opponent-team)
         [game-state opponent-team])

        {:keys [home-players away-players]}
        (use-memo
         #(sel/all-players game-state)
         [game-state])

        ;; Memoized valid positions
        valid-moves
        (use-memo
         #(when (and is-my-turn selected-player-id game-state)
            (actions/valid-move-positions game-state selected-player-id))
         [is-my-turn selected-player-id game-state])

        valid-pass-targets
        (use-memo
         #(when pass-active
            (sel/valid-pass-targets my-players ball-holder-id))
         [pass-active my-players ball-holder-id])

        valid-setup-positions
        (use-memo
         #(when (and setup-mode selected-player-id)
            (actions/valid-setup-positions game-state my-team))
         [setup-mode selected-player-id game-state my-team])

        ;; Memoized setup state
        setup-placed-count
        (use-memo
         #(when setup-mode
            (sel/setup-placed-count my-players))
         [setup-mode my-players])

        my-setup-complete
        (use-memo
         #(when setup-mode
            (sel/team-setup-complete? game-state my-team))
         [setup-mode game-state my-team])

        both-teams-ready
        (use-memo
         #(when setup-mode
            (sel/both-teams-setup-complete? game-state))
         [setup-mode game-state])

        ;; On-court and off-court players for substitution
        my-on-court-players
        (use-memo
         #(when my-players
            (->> (vals my-players)
                 (filter :position)
                 vec))
         [my-players])

        my-off-court-players
        (use-memo
         #(when my-players
            (->> (vals my-players)
                 (remove :position)
                 vec))
         [my-players])]

    {:opponent-team         opponent-team
     :phase                 phase
     :setup-mode            setup-mode
     :my-player             my-player
     :my-players            my-players
     :my-hand               my-hand
     :opponent              opponent
     :home-players          home-players
     :away-players          away-players
     :ball-holder-id        ball-holder-id
     :score                 score
     :active-player         active-player
     :events                events
     :play-area             play-area
     :selected-player-id    selected-player-id
     :pass-active           pass-active
     :ball-active           ball-active
     :valid-moves           valid-moves
     :valid-pass-targets    valid-pass-targets
     :valid-setup-positions valid-setup-positions
     :setup-placed-count    setup-placed-count
     :my-setup-complete     my-setup-complete
     :both-teams-ready      both-teams-ready
     :my-on-court-players   my-on-court-players
     :my-off-court-players  my-off-court-players
     :discard-active         discard-active
     :discard-cards          discard-cards
     :selected-card          selected-card
     :standard-action-active standard-action-active
     :standard-action-step   standard-action-step
     :standard-action-cards  standard-action-cards}))
