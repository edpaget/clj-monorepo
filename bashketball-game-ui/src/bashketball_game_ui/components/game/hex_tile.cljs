(ns bashketball-game-ui.components.game.hex-tile
  "Individual hex tile component for the game board.

  Renders an SVG polygon with terrain-based styling and click handling."
  (:require
   [bashketball-game-ui.game.board-utils :as board]
   [uix.core :refer [$ defui use-callback]]))

(def ^:private terrain-colors
  {:hoop            {:fill "#f97316" :stroke "#ea580c"}
   :paint           {:fill "#dbeafe" :stroke "#93c5fd"}
   :three-point-line {:fill "#fef3c7" :stroke "#fcd34d"}
   :court           {:fill "#f1f5f9" :stroke "#cbd5e1"}})

(def ^:private side-modifiers
  {:home {:fill-opacity 1.0}
   :away {:fill-opacity 0.85}})

(defui hex-tile
  "Single hex tile with terrain-based styling.

  Props:
  - q, r: Hex coordinates
  - terrain: :hoop, :paint, :three-point-line, :court
  - side: :home, :away, or nil
  - highlighted: boolean for valid move indication
  - selected: boolean for current selection
  - on-click: fn called when clicked"
  [{:keys [q r terrain side highlighted selected on-click]}]
  (let [center       (board/hex->pixel [q r])
        points-str   (board/hex-points-str center)
        base-colors  (get terrain-colors terrain (:court terrain-colors))
        fill-opacity (get-in side-modifiers [side :fill-opacity] 1.0)

        handle-click (use-callback
                      (fn [_e]
                        (when on-click
                          (on-click q r)))
                      [on-click q r])]

    ($ :polygon
       {:points   points-str
        :fill     (cond
                    selected    "#a5f3fc"
                    highlighted "#bbf7d0"
                    :else       (:fill base-colors))
        :fill-opacity fill-opacity
        :stroke   (cond
                    selected    "#06b6d4"
                    highlighted "#22c55e"
                    :else       (:stroke base-colors))
        :stroke-width (if (or selected highlighted) 2 1)
        :class    "cursor-pointer transition-colors hover:brightness-95"
        :on-click handle-click})))
