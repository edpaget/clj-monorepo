(ns bashketball-game-ui.components.game.hex-grid
  "Hex grid component rendering the 5x14 game board.

  Composes hex tiles, player tokens, and ball indicator into
  a complete board visualization."
  (:require
   [bashketball-game-ui.components.game.ball-indicator :refer [ball-indicator]]
   [bashketball-game-ui.components.game.hex-tile :refer [hex-tile]]
   [bashketball-game-ui.components.game.player-token :refer [player-token]]
   [bashketball-game-ui.game.board-utils :as board]
   [uix.core :refer [$ defui use-memo]]))

(defn- player-at-position
  "Finds the player at a given position from the players map."
  [players position]
  (->> (vals players)
       (filter #(= (:position %) position))
       first))

(defn- ball-holder-id
  "Returns the player ID holding the ball, or nil if not possessed."
  [ball]
  (when (= (:status ball) :possessed)
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
  - on-hex-click: fn [q r] called when hex clicked
  - on-player-click: fn [player-id] called when player clicked"
  [{:keys [board ball home-players away-players
           selected-player valid-moves
           on-hex-click on-player-click]}]
  (let [[width height padding] (board/board-dimensions)
        all-pos        (use-memo #(board/all-positions) [])
        holder-id      (ball-holder-id ball)
        valid-set      (set valid-moves)]

    ($ :svg {:viewBox (str "0 0 " width " " height)
             :class   "w-full h-full"
             :style   {:max-height "100%"}}

       ;; Offset group for padding
       ($ :g {:transform (str "translate(" padding "," padding ")")}

          ;; Layer 1: Hex tiles
          (for [[q r] all-pos
                :let  [{:keys [terrain side]} (board/terrain-at [q r])
                       highlighted?           (contains? valid-set [q r])]]
            ($ hex-tile {:key         (str q "-" r)
                         :q           q
                         :r           r
                         :terrain     terrain
                         :side        side
                         :highlighted highlighted?
                         :selected    false
                         :on-click    on-hex-click}))

          ;; Layer 2: Player tokens
          (for [[id player] (concat (seq home-players) (seq away-players))
                :when       (:position player)
                :let        [team     (if (contains? home-players id) :home :away)
                             selected (= id selected-player)
                             has-ball (= id holder-id)]]
            ($ player-token {:key      id
                             :player   player
                             :team     team
                             :selected selected
                             :has-ball has-ball
                             :on-click on-player-click}))

          ;; Layer 3: Ball indicator (loose or in-air only)
          (when (and ball (not= (:status ball) :possessed))
            ($ ball-indicator {:ball ball}))))))
