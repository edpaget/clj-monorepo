(ns bashketball-game-ui.components.game.hex-tile
  "Individual hex tile component for the game board.

  Renders an SVG polygon with terrain-based styling and click handling."
  (:require
   [bashketball-game-ui.game.board-utils :as board]
   [uix.core :refer [$ defui use-callback]]))

(def ^:private terrain-colors
  {:hoop             {:fill "#f97316" :stroke "#ea580c"}
   :paint            {:fill "#dbeafe" :stroke "#93c5fd"}
   :three-point-line {:fill "#fef3c7" :stroke "#fcd34d"}
   :center-court     {:fill "#e9d5ff" :stroke "#c084fc"}
   :court            {:fill "#f1f5f9" :stroke "#cbd5e1"}})

(def ^:private side-modifiers
  {:team/HOME {:fill-opacity 1.0}
   :team/AWAY {:fill-opacity 0.85}})

(defui hex-tile
  "Single hex tile with terrain-based styling.

  Props:
  - q, r: Hex coordinates
  - terrain: :hoop, :paint, :three-point-line, :court
  - side: :HOME, :AWAY, or nil
  - highlighted: boolean for valid move indication
  - setup-highlight: boolean for valid setup placement indication
  - invalid-highlight: boolean for invalid move indication (muted styling)
  - selected: boolean for current selection
  - on-click: fn called when clicked"
  [{:keys [q r terrain side highlighted setup-highlight invalid-highlight selected on-click]}]
  (let [[cx cy]       (board/hex->pixel [q r])
        points-str    (board/hex-points-str [cx cy])
        base-colors   (get terrain-colors terrain (:court terrain-colors))
        fill-opacity  (cond
                        invalid-highlight 0.4
                        :else (get-in side-modifiers [side :fill-opacity] 1.0))
        any-highlight (or highlighted setup-highlight)

        handle-click  (use-callback
                       (fn [_e]
                         (when on-click
                           (on-click q r)))
                       [on-click q r])]

    ($ :<>
       ($ :polygon
          {:points   points-str
           :fill     (cond
                       selected          "#a5f3fc"
                       setup-highlight   "#99f6e4"
                       highlighted       "#bbf7d0"
                       invalid-highlight "#e5e7eb"
                       :else             (:fill base-colors))
           :fill-opacity fill-opacity
           :stroke   (cond
                       selected          "#06b6d4"
                       setup-highlight   "#14b8a6"
                       highlighted       "#22c55e"
                       invalid-highlight "#9ca3af"
                       :else             (:stroke base-colors))
           :stroke-width (cond
                           (or selected any-highlight) 2
                           invalid-highlight 1
                           :else 1)
           :class    (cond
                       invalid-highlight "cursor-not-allowed transition-colors"
                       :else "cursor-pointer transition-colors hover:brightness-95")
           :on-click handle-click})
       ($ :text
          {:x           cx
           :y           (- cy 20)
           :text-anchor "middle"
           :font-size   "10"
           :fill        "#666"}
          (str q "," r)))))
