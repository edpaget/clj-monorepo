(ns bashketball-game-ui.components.game.player-info
  "Player info panel component.

  Displays player resources, deck state, team info, and current score."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defui player-info
  "Displays player info panel.

  Props:
  - player: GamePlayer map with :actions-remaining, :deck, :team
  - team: :HOME or :AWAY
  - score: Integer score for this team
  - is-active: boolean if this is the active player
  - is-opponent: boolean if this is opponent (compact view)"
  [{:keys [player team score is-active is-opponent]}]
  (let [deck              (:deck player)
        draw-pile-count   (count (:draw-pile deck))
        hand-count        (count (:hand deck))
        discard-count     (count (:discard deck))
        actions-remaining (:actions-remaining player)
        team-color        (if (= team :HOME) "blue" "red")
        team-label        (if (= team :HOME) "Home" "Away")]

    ($ :div {:class (cn "flex items-center gap-4 p-3 rounded-lg"
                        (if is-active
                          (str "bg-" team-color "-50 ring-2 ring-" team-color "-400")
                          "bg-slate-50"))}

       ($ :div {:class (cn "flex items-center justify-center w-10 h-10 rounded-full text-white font-bold"
                           (if (= team :HOME) "bg-blue-500" "bg-red-500"))}
          (first team-label))

       ($ :div {:class "flex-1"}
          ($ :div {:class "flex items-center gap-2"}
             ($ :span {:class "font-medium text-slate-900"} team-label)
             (when is-active
               ($ :span {:class (cn "px-2 py-0.5 text-xs rounded-full"
                                    (if (= team :HOME)
                                      "bg-blue-100 text-blue-700"
                                      "bg-red-100 text-red-700"))}
                  "Active")))

          (when-not is-opponent
            ($ :div {:class "flex items-center gap-3 mt-1 text-sm text-slate-600"}
               ($ :span {:class "flex items-center gap-1"}
                  ($ :span {:class "text-slate-400"} "⚡")
                  ($ :span actions-remaining " actions"))
               ($ :span {:class "flex items-center gap-1"}
                  ($ :span {:class "text-slate-400"} "📚")
                  ($ :span draw-pile-count " deck"))
               ($ :span {:class "flex items-center gap-1"}
                  ($ :span {:class "text-slate-400"} "✋")
                  ($ :span hand-count " hand"))
               ($ :span {:class "flex items-center gap-1"}
                  ($ :span {:class "text-slate-400"} "🗑")
                  ($ :span discard-count " discard")))))

       ($ :div {:class "text-right"}
          ($ :div {:class "text-xs text-slate-500 uppercase"} "Score")
          ($ :div {:class "text-2xl font-bold text-slate-900"} (or score 0))))))
