(ns bashketball-game-ui.components.game.player-token
  "Basketball player token component for the game board.

  Renders a player as a colored circle with jersey number."
  (:require
   [bashketball-game-ui.game.board-utils :as board]
   [uix.core :refer [$ defui use-callback]]))

(def ^:private team-colors
  {:HOME {:fill "#3b82f6" :stroke "#1d4ed8" :text "#ffffff"}
   :AWAY {:fill "#ef4444" :stroke "#b91c1c" :text "#ffffff"}})

(def ^:private token-radius 22)

(defn- valid-position?
  "Returns true if position is a valid [q r] vector."
  [pos]
  (and (vector? pos)
       (= 2 (count pos))
       (number? (first pos))
       (number? (second pos))))

(defui player-token
  "Basketball player token on the board.

  Props:
  - player: BasketballPlayer map with :id, :name, :position, :exhausted?
  - team: :HOME or :AWAY
  - selected: boolean
  - has-ball: boolean
  - pass-target: boolean, true when this player can receive a pass
  - on-click: fn [player-id]"
  [{:keys [player team selected has-ball pass-target on-click]}]
  (let [position     (:position player)
        ;; Defensive: ensure position is a valid [q r] vector
        [cx cy]      (if (valid-position? position)
                       (board/hex->pixel position)
                       [0 0])
        colors       (get team-colors team (:HOME team-colors))
        exhausted?   (:exhausted? player)
        jersey-num   (or (some-> (:name player) first str) "?")

        handle-click (use-callback
                      (fn [e]
                        (.stopPropagation e)
                        (when on-click
                          (on-click (:id player))))
                      [on-click player])]

    ($ :g {:class    "cursor-pointer"
           :on-click handle-click}

       ;; Selection ring
       (when selected
         ($ :circle {:cx           cx
                     :cy           cy
                     :r            (+ token-radius 4)
                     :fill         "none"
                     :stroke       "#06b6d4"
                     :stroke-width 3}))

       ;; Ball indicator ring
       (when has-ball
         ($ :circle {:cx           cx
                     :cy           cy
                     :r            (+ token-radius 6)
                     :fill         "none"
                     :stroke       "#f97316"
                     :stroke-width 2
                     :stroke-dasharray "4 2"}))

       ;; Pass target indicator ring
       (when pass-target
         ($ :circle {:cx           cx
                     :cy           cy
                     :r            (+ token-radius 8)
                     :fill         "none"
                     :stroke       "#22c55e"
                     :stroke-width 3
                     :class        "animate-pulse"}))

       ;; Main player circle
       ($ :circle {:cx           cx
                   :cy           cy
                   :r            token-radius
                   :fill         (:fill colors)
                   :stroke       (:stroke colors)
                   :stroke-width 2
                   :opacity      (if exhausted? 0.5 1.0)})

       ;; Jersey number/letter
       ($ :text {:x           cx
                 :y           (+ cy 6)
                 :text-anchor "middle"
                 :fill        (:text colors)
                 :font-size   "16"
                 :font-weight "bold"
                 :style       {:user-select "none"}}
          jersey-num)

       ;; Exhausted overlay
       (when exhausted?
         ($ :text {:x           cx
                   :y           (- cy 28)
                   :text-anchor "middle"
                   :font-size   "12"
                   :fill        "#64748b"}
            "zzz")))))
