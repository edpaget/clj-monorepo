(ns bashketball-game-ui.components.game.hex-grid
  "Hex grid component rendering the 5x14 game board.

  Composes hex tiles, player tokens, and ball indicator into
  a complete board visualization."
  (:require
   [bashketball-game-ui.components.game.ball-indicator :refer [ball-indicator]]
   [bashketball-game-ui.components.game.hex-tile :refer [hex-tile]]
   [bashketball-game-ui.components.game.player-token :refer [player-token]]
   [bashketball-game-ui.game.board-utils :as board]
   [bashketball-game-ui.game.selectors :as sel]
   [uix.core :refer [$ defui use-memo]]))

(defn- ball-holder-id
  "Returns the player ID holding the ball, or nil if not possessed."
  [ball]
  (when (= (:__typename ball) "BallPossessed")
    (:holder-id ball)))

(defui hex-grid
  "Renders the game board as an SVG hex grid.

  Props:
  - board: Board state with :tiles and :occupants
  - ball: Ball state map
  - home-players: Home team players map (id -> BasketballPlayer)
  - away-players: Away team players map (id -> BasketballPlayer)
  - selected-player: Currently selected player ID
  - valid-moves: Set of valid move positions [[q r] ...]
  - setup-highlights: Set of valid setup placement positions [[q r] ...]
  - pass-mode: boolean, true when in pass target selection mode
  - valid-pass-targets: Set of player IDs that are valid pass targets
  - ball-selected: boolean, true when ball is selected for movement
  - on-hex-click: fn [q r] called when hex clicked
  - on-player-click: fn [player-id] called when player clicked
  - on-ball-click: fn [] called when loose ball clicked"
  [{:keys [board ball home-players away-players
           selected-player valid-moves setup-highlights pass-mode valid-pass-targets
           ball-selected on-hex-click on-player-click on-ball-click]}]
  (let [[width height padding] (board/board-dimensions)
        all-pos                (use-memo #(board/all-positions) [])
        holder-id              (ball-holder-id ball)
        valid-set              (set valid-moves)
        setup-set              (set setup-highlights)
        home-indices           (use-memo #(sel/build-player-index-map home-players) [home-players])
        away-indices           (use-memo #(sel/build-player-index-map away-players) [away-players])]

    ($ :svg {:viewBox (str "0 0 " width " " height)
             :class   "w-full h-full"
             :style   {:max-height "100%"}}

       ;; Offset group for padding
       ($ :g {:transform (str "translate(" padding "," padding ")")}

          ;; Layer 1: Hex tiles
          (for [[q r] all-pos
                :let  [{:keys [terrain side]} (board/terrain-at [q r])
                       highlighted?           (contains? valid-set [q r])
                       setup-highlight?       (contains? setup-set [q r])]]
            ($ hex-tile {:key             (str q "-" r)
                         :q               q
                         :r               r
                         :terrain         terrain
                         :side            side
                         :highlighted     highlighted?
                         :setup-highlight setup-highlight?
                         :selected        false
                         :on-click        on-hex-click}))

          ;; Layer 2: Player tokens
          (for [[id player] (concat (seq home-players) (seq away-players))
                :let        [pos (:position player)]
                ;; Only render players with valid [q r] positions
                :when       (and (vector? pos) (= 2 (count pos)))
                :let        [is-home     (contains? home-players id)
                             team        (if is-home :HOME :AWAY)
                             player-num  (get (if is-home home-indices away-indices) id)
                             selected    (= id selected-player)
                             has-ball    (= id holder-id)
                             pass-target (and pass-mode (contains? valid-pass-targets id))]]
            ($ player-token {:key         id
                             :player      player
                             :team        team
                             :player-num  player-num
                             :selected    selected
                             :has-ball    has-ball
                             :pass-target pass-target
                             :on-click    on-player-click}))

          ;; Layer 3: Ball indicator (loose or in-air only)
          (when (and ball (not= (:__typename ball) "BallPossessed"))
            ($ ball-indicator {:ball     ball
                               :selected ball-selected
                               :on-click on-ball-click}))))))
