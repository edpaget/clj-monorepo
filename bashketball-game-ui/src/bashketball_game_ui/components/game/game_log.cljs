(ns bashketball-game-ui.components.game.game-log
  "Game log component displaying event history.

  Shows a scrollable list of game events with formatted descriptions."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui use-effect use-ref]]))

(defn format-event-type
  "Returns a human-readable label for an event type."
  [event-type]
  (case (keyword event-type)
    :bashketball/move-player      "Move"
    :bashketball/set-ball-in-air  "Ball"
    :bashketball/add-score        "Score"
    :bashketball/advance-turn     "Turn"
    :bashketball/set-phase        "Phase"
    :bashketball/draw-cards       "Draw"
    :bashketball/discard-cards    "Discard"
    :bashketball/exhaust-player   "Exhaust"
    :bashketball/refresh-player   "Refresh"
    :bashketball/ball-caught      "Catch"
    :bashketball/ball-loose       "Loose"
    :bashketball/shot-result      "Shot"
    :bashketball/set-ball-loose   "Manual Ball Move"
    :bashketball/reveal-fate      "Fate"
    :bashketball/shuffle-deck     "Shuffle"
    :bashketball/return-discard   "Return"
    (name event-type)))

(defn format-event-description
  "Returns a description string for an event."
  [{:keys [type data] :as _event}]
  (case (keyword type)
    :bashketball/move-player
    (let [{:keys [position]} data]
      (str "Player moved to " (pr-str position)))

    :bashketball/set-ball-in-air
    (let [{:keys [action-type origin]} data]
      (if (= action-type "shot")
        (str "Shot from " (pr-str origin))
        (str "Pass from " (pr-str origin))))

    :bashketball/add-score
    (let [{:keys [team points]} data]
      (str (name team) " scored " points " points"))

    :bashketball/advance-turn
    "Turn ended"

    :bashketball/set-phase
    (let [{:keys [phase]} data]
      (some->> phase name (str "Phase: ")))

    :bashketball/shot-result
    (let [{:keys [made]} data]
      (if made "Shot made!" "Shot missed"))

    :bashketball/ball-caught
    "Ball caught"

    (:bashketball/ball-loose
     :bashketball/set-ball-loose)
    (let [{:keys [position]} data]
      (str "Ball loose at " (pr-str position)))

    :bashketball/reveal-fate
    (str "Revealed " (get-in data [:revealed-card "card-slug"]))

    :bashketball/shuffle-deck
    (let [{:keys [player]} data]
      (str (name player) " shuffled their deck"))

    :bashketball/return-discard
    (let [{:keys [player]} data]
      (str (name player) " returned discard to deck"))

    ;; Default
    (str (name type))))

(defn event-type-color
  "Returns a color class for an event type."
  [event-type]
  (case (keyword event-type)
    :bashketball/add-score       "text-green-600"
    :bashketball/shot-result     "text-orange-600"
    :bashketball/advance-turn    "text-blue-600"
    :bashketball/set-ball-in-air "text-purple-600"
    :bashketball/shuffle-deck    "text-emerald-600"
    :bashketball/return-discard  "text-emerald-600"
    "text-slate-600"))

(defui event-item
  "Single event in the log."
  [{:keys [event]}]
  (let [event-type (:type event)]
    ($ :div {:class "flex items-start gap-2 py-1.5 border-b border-slate-100 last:border-0"}
       ($ :span {:class (cn "text-xs font-medium w-14 flex-shrink-0"
                            (event-type-color event-type))}
          (format-event-type event-type))
       ($ :span {:class "text-xs text-slate-700 flex-1"}
          (format-event-description event)))))

(defui game-log
  "Displays chronological game events.

  Props:
  - events: Vector of event maps with :type, :timestamp, :data"
  [{:keys [events]}]
  (let [scroll-ref (use-ref nil)]

    ;; Auto-scroll to bottom when new events added
    (use-effect
     (fn []
       (when-let [el @scroll-ref]
         (set! (.-scrollTop el) (.-scrollHeight el)))
       js/undefined)
     [(count events)])

    ($ :div {:class "flex flex-col h-full"}
       ($ :div {:class "text-xs font-medium text-slate-500 px-2 py-1 border-b bg-slate-50 flex-shrink-0"}
          "Game Log")
       ($ :div {:ref   scroll-ref
                :class "overflow-y-auto px-2"
                :style {:max-height "calc(100vh - 300px)"}}
          (if (empty? events)
            ($ :div {:class "text-xs text-slate-400 italic py-4 text-center"}
               "No events yet")
            (for [[idx event] (map-indexed vector events)]
              ($ event-item {:key   idx
                             :event event})))))))
