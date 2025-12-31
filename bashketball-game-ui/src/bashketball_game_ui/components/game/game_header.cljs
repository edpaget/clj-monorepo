(ns bashketball-game-ui.components.game.game-header
  "Compact game header component.

  Displays game ID, turn number, phase badge, and score in a
  minimal horizontal layout optimized for small screens."
  (:require
   [bashketball-game-ui.components.ui.dropdown-menu :as dropdown]
   [bashketball-ui.router :as router]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def ^:private phase-labels
  {:phase/SETUP       "Setup"
   :phase/TIP_OFF     "Tip-Off"
   :phase/UPKEEP      "Upkeep"
   :phase/DRAW        "Draw"
   :phase/ACTIONS     "Actions"
   :phase/RESOLUTION  "Resolution"
   :phase/END_OF_TURN "End"
   :phase/GAME_OVER   "Over"})

(defn- phase-label
  "Returns human-readable label for a phase."
  [phase]
  (get phase-labels (keyword phase) (str phase)))

(def ^:private phase-colors
  {:phase/SETUP       "bg-purple-100 text-purple-700"
   :phase/TIP_OFF     "bg-amber-100 text-amber-700"
   :phase/UPKEEP      "bg-yellow-100 text-yellow-700"
   :phase/DRAW        "bg-cyan-100 text-cyan-700"
   :phase/ACTIONS     "bg-blue-100 text-blue-700"
   :phase/RESOLUTION  "bg-orange-100 text-orange-700"
   :phase/END_OF_TURN "bg-slate-100 text-slate-700"
   :phase/GAME_OVER   "bg-red-100 text-red-700"})

(defn- phase-color
  "Returns Tailwind classes for phase badge color."
  [phase]
  (get phase-colors (keyword phase) "bg-slate-100 text-slate-700"))

(defn- calculate-quarter
  "Calculates the current quarter from turn number.
  Each quarter has 12 turns (6 per player)."
  [turn-number]
  (when (and turn-number (pos? turn-number))
    (inc (quot (dec turn-number) 12))))

(defui nav-menu
  "Hamburger menu with navigation links."
  []
  (let [navigate (router/use-navigate)]
    ($ dropdown/dropdown-menu
       ($ dropdown/dropdown-menu-trigger {:asChild true}
          ($ :button {:class "p-1.5 hover:bg-slate-100 rounded min-h-[44px] min-w-[44px] flex items-center justify-center"}
             ($ :span {:class "text-xl"} "\u2630")))
       ($ dropdown/dropdown-menu-content {:align "start" :side "bottom"}
          ($ dropdown/dropdown-menu-item
             {:on-select #(navigate "/lobby")}
             "Lobby")
          ($ dropdown/dropdown-menu-item
             {:on-select #(navigate "/games")}
             "My Games")
          ($ dropdown/dropdown-menu-item
             {:on-select #(navigate "/decks")}
             "My Decks")
          ($ dropdown/dropdown-menu-item
             {:on-select #(navigate "/rules/introduction")}
             "Rules")
          ($ dropdown/dropdown-menu-separator)
          ($ dropdown/dropdown-menu-item
             {:on-select #(navigate "/")}
             "Home")))))

(defui header-text-button
  "Text button for header actions."
  [{:keys [label on-click]}]
  ($ :button {:class    "px-2 py-1 hover:bg-slate-100 rounded text-xs text-slate-600 hover:text-slate-900"
              :on-click on-click}
     label))

(defui game-header
  "Compact header with game info, navigation menu, and phase badge.

  Props:
  - game-id: UUID string for the game
  - turn-number: Current turn number
  - phase: Current phase keyword
  - home-score: Home team score
  - away-score: Away team score
  - is-my-turn: boolean
  - on-log-click: fn [] to open game log modal
  - on-roster-click: fn [] to open roster modal"
  [{:keys [game-id turn-number phase home-score away-score is-my-turn
           on-log-click on-roster-click]}]
  ($ :div {:class "flex items-center justify-between px-3 py-2 border-b bg-white"}
     ;; Left: nav menu + game ID
     ($ :div {:class "flex items-center gap-2"}
        ($ nav-menu)
        ($ :span {:class "text-xs text-slate-500 font-mono"}
           (when game-id (subs (str game-id) 0 8))))

     ;; Right: quarter/turn, phase badge, score, action buttons
     ($ :div {:class "flex items-center gap-3"}
        ;; Quarter and turn number
        (let [quarter (calculate-quarter turn-number)]
          ($ :span {:class "text-xs text-slate-500"}
             (if quarter
               (str "Q" quarter " T" turn-number)
               (str "T" (or turn-number "—")))))

        ;; Phase badge
        ($ :span {:class (cn "px-2 py-0.5 text-xs font-medium rounded"
                             (phase-color phase))}
           (phase-label phase))

        ;; Turn indicator dot
        ($ :span {:class (cn "w-2 h-2 rounded-full"
                             (if is-my-turn
                               "bg-green-500"
                               "bg-slate-300"))})

        ;; Score
        ($ :span {:class "text-sm font-bold text-slate-900"}
           (str (or away-score 0) "-" (or home-score 0)))

        ;; Divider
        ($ :span {:class "w-px h-4 bg-slate-200"})

        ;; Action buttons
        ($ header-text-button {:label "Log" :on-click on-log-click})
        ($ header-text-button {:label "Roster" :on-click on-roster-click}))))
