(ns bashketball-game-ui.components.game.ball-indicator
  "Ball visualization component for the game board.

  Renders the ball based on its current state: loose (on ground) or
  in-air (with trajectory line). Possessed balls are shown via the
  player-token component instead."
  (:require
   [bashketball-game-ui.game.board-utils :as board]
   [uix.core :refer [$ defui]]))

(def ^:private ball-radius 10)
(def ^:private ball-color "#f97316")
(def ^:private ball-stroke "#ea580c")

(defui loose-ball
  "Renders a loose ball at a position on the ground.

  Props:
  - position: [q r] hex position
  - selected: boolean, true if ball is selected for movement
  - on-click: optional click handler"
  [{:keys [position selected on-click]}]
  (let [[cx cy] (board/hex->pixel position)]
    ($ :g {:style    {:cursor (when on-click "pointer")}
           :on-click (when on-click #(do (.stopPropagation %) (on-click)))}
       ;; Selection ring (behind ball)
       (when selected
         ($ :circle {:cx           cx
                     :cy           cy
                     :r            (+ ball-radius 4)
                     :fill         "none"
                     :stroke       "#06b6d4"
                     :stroke-width 2}))
       ;; Shadow
       ($ :ellipse {:cx      cx
                    :cy      (+ cy 4)
                    :rx      (- ball-radius 2)
                    :ry      4
                    :fill    "#00000020"})
       ;; Ball
       ($ :circle {:cx           cx
                   :cy           cy
                   :r            ball-radius
                   :fill         ball-color
                   :stroke       ball-stroke
                   :stroke-width 1.5})
       ;; Seam lines for basketball look
       ($ :path {:d            (str "M " (- cx ball-radius) " " cy
                                    " Q " cx " " (- cy 4) " " (+ cx ball-radius) " " cy)
                 :fill         "none"
                 :stroke       ball-stroke
                 :stroke-width 1})
       ($ :line {:x1           cx
                 :y1           (- cy ball-radius)
                 :x2           cx
                 :y2           (+ cy ball-radius)
                 :stroke       ball-stroke
                 :stroke-width 1}))))

(defui in-air-ball
  "Renders a ball in flight with trajectory line."
  [{:keys [origin target action-type]}]
  (let [[ox oy]  (board/hex->pixel origin)
        [tx ty]  (if (vector? target)
                   (board/hex->pixel target)
                     ;; Target is a player ID - we'd need position lookup
                     ;; For now, just don't render trajectory
                   [ox oy])
        ;; Ball position along trajectory (animate to middle for visual)
        mid-x    (/ (+ ox tx) 2)
        mid-y    (- (/ (+ oy ty) 2) 20) ;; Arc upward
        is-shot? (= action-type :shot)]

    ($ :g
       ;; Trajectory line
       ($ :path {:d            (str "M " ox " " oy
                                    " Q " mid-x " " (- mid-y 30) " " tx " " ty)
                 :fill         "none"
                 :stroke       (if is-shot? "#ef4444" "#3b82f6")
                 :stroke-width 2
                 :stroke-dasharray "6 4"
                 :opacity      0.7})

       ;; Origin marker
       ($ :circle {:cx      ox
                   :cy      oy
                   :r       4
                   :fill    "none"
                   :stroke  "#94a3b8"
                   :stroke-width 1})

       ;; Target marker
       ($ :circle {:cx           tx
                   :cy           ty
                   :r            8
                   :fill         "none"
                   :stroke       (if is-shot? "#ef4444" "#3b82f6")
                   :stroke-width 2})

       ;; Ball at arc midpoint
       ($ :circle {:cx           mid-x
                   :cy           mid-y
                   :r            ball-radius
                   :fill         ball-color
                   :stroke       ball-stroke
                   :stroke-width 1.5}))))

(defui ball-indicator
  "Ball visualization based on ball state.

  Props:
  - ball: Ball state map with __typename and state-specific fields
    - BallPossessed - ball held by player (rendered via player-token)
    - BallLoose - {:position [q r]}
    - BallInAir - {:origin [q r] :target [q r] or player-id :action-type :shot/:pass}
  - selected: boolean, true if ball is selected for movement
  - on-click: optional click handler for loose ball"
  [{:keys [ball selected on-click]}]
  (case (:__typename ball)
    "BallLoose"  ($ loose-ball {:position (:position ball)
                                :selected selected
                                :on-click on-click})
    "BallInAir"  ($ in-air-ball {:origin      (:origin ball)
                                 :target      (:target ball)
                                 :action-type (:action-type ball)})
    ;; BallPossessed - handled by player-token's has-ball prop
    nil))
