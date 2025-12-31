(ns bashketball-game-ui.components.game.player-token
  "Basketball player token component for the game board.

  Renders a player as a colored circle with jersey number."
  (:require
   [bashketball-game-ui.game.board-utils :as board]
   [uix.core :refer [$ defui use-callback]]))

(def ^:private team-colors
  {:team/HOME {:fill "#3b82f6" :stroke "#1d4ed8" :text "#ffffff"}
   :team/AWAY {:fill "#ef4444" :stroke "#b91c1c" :text "#ffffff"}})

(def ^:private token-radius 22)

(def ^:private size-labels
  {:size/SM "S"
   :size/MD "M"
   :size/LG "L"})

(def ^:private size-colors
  {:size/SM {:fill "#f59e0b" :stroke "#d97706"}  ;; amber
   :size/MD {:fill "#10b981" :stroke "#059669"}  ;; emerald
   :size/LG {:fill "#6366f1" :stroke "#4f46e5"}}) ;; indigo

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
  - player: BasketballPlayer map with :id, :name, :position, :exhausted, :attachments
  - team: :HOME or :AWAY
  - player-num: 1-based index of player on their team
  - selected: boolean
  - has-ball: boolean
  - pass-target: boolean, true when this player can receive a pass
  - invalid-pass-target: boolean, true when in pass mode but this player cannot be passed to
  - on-click: fn [player-id]
  - on-toggle-exhausted: fn [player-id] to toggle exhaust status"
  [{:keys [player team player-num selected has-ball pass-target invalid-pass-target on-click on-toggle-exhausted]}]
  (let [position            (:position player)
        ;; Defensive: ensure position is a valid [q r] vector
        [cx cy]             (if (valid-position? position)
                              (board/hex->pixel position)
                              [0 0])
        colors              (get team-colors team (:team/HOME team-colors))
        exhausted?          (:exhausted player)
        attachment-count    (count (:attachments player))
        player-size         (get-in player [:stats :size])
        size-label          (get size-labels (keyword player-size))
        size-color          (get size-colors (keyword player-size) {:fill "#94a3b8" :stroke "#64748b"})
        first-letter        (or (some-> (:name player) first str) "?")
        jersey-num          (str first-letter player-num)

        handle-click        (use-callback
                             (fn [e]
                               (.stopPropagation e)
                               (when on-click
                                 (on-click (:id player))))
                             [on-click player])

        handle-context-menu (use-callback
                             (fn [e]
                               (.preventDefault e)
                               (.stopPropagation e)
                               (when on-toggle-exhausted
                                 (on-toggle-exhausted (:id player))))
                             [on-toggle-exhausted player])]

    ($ :g {:class           "cursor-pointer"
           :on-click        handle-click
           :on-context-menu handle-context-menu}

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

       ;; Pass target indicator ring (valid - green pulsing)
       (when pass-target
         ($ :circle {:cx           cx
                     :cy           cy
                     :r            (+ token-radius 8)
                     :fill         "none"
                     :stroke       "#22c55e"
                     :stroke-width 3
                     :class        "animate-pulse"}))

       ;; Invalid pass target indicator ring (invalid - gray static)
       (when invalid-pass-target
         ($ :circle {:cx           cx
                     :cy           cy
                     :r            (+ token-radius 8)
                     :fill         "none"
                     :stroke       "#9ca3af"
                     :stroke-width 2
                     :opacity      0.5}))

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

       ;; Exhausted badge (bottom-left of token)
       (when exhausted?
         ($ :g
            ($ :circle {:cx           (- cx 16)
                        :cy           (+ cy 16)
                        :r            10
                        :fill         "#64748b"
                        :stroke       "#475569"
                        :stroke-width 1})
            ($ :text {:x           (- cx 16)
                      :y           (+ cy 20)
                      :text-anchor "middle"
                      :fill        "#ffffff"
                      :font-size   "10"
                      :font-weight "bold"
                      :style       {:user-select "none"}}
               "E")))

       ;; Attachment count badge (bottom-right of token)
       (when (pos? attachment-count)
         ($ :g
            ($ :circle {:cx           (+ cx 16)
                        :cy           (+ cy 16)
                        :r            10
                        :fill         "#8b5cf6"
                        :stroke       "#6d28d9"
                        :stroke-width 1})
            ($ :text {:x           (+ cx 16)
                      :y           (+ cy 20)
                      :text-anchor "middle"
                      :fill        "#ffffff"
                      :font-size   "10"
                      :font-weight "bold"
                      :style       {:user-select "none"}}
               (str attachment-count))))

       ;; Size badge (top-left of token)
       (when size-label
         ($ :g
            ($ :circle {:cx           (- cx 16)
                        :cy           (- cy 16)
                        :r            10
                        :fill         (:fill size-color)
                        :stroke       (:stroke size-color)
                        :stroke-width 1})
            ($ :text {:x           (- cx 16)
                      :y           (- cy 12)
                      :text-anchor "middle"
                      :fill        "#ffffff"
                      :font-size   "10"
                      :font-weight "bold"
                      :style       {:user-select "none"}}
               size-label))))))
