(ns bashketball-game-ui.views.starter-decks
  "View component for displaying starter decks with card previews in the rules section."
  (:require
   [bashketball-game-ui.hooks.use-cards :refer [use-cards]]
   [bashketball-game-ui.hooks.use-starter-decks :refer [use-starter-decks]]
   [bashketball-ui.cards.card-preview :refer [card-preview]]
   [uix.core :refer [$ defui use-memo use-state]]))

(defui starter-deck-section
  "Displays a single starter deck with its cards organized by type."
  [{:keys [deck cards-by-slug]}]
  (let [[expanded? set-expanded!] (use-state false)
        card-slugs                (:card-slugs deck)
        unique-slugs              (use-memo (fn [] (vec (distinct card-slugs))) [card-slugs])
        deck-cards                (use-memo (fn []
                                              (->> unique-slugs
                                                   (map (fn [slug] (get cards-by-slug slug)))
                                                   (remove nil?)
                                                   vec))
                                            [unique-slugs cards-by-slug])
        players                   (filter #(= "PLAYER_CARD" (:__typename %)) deck-cards)
        non-players               (remove #(= "PLAYER_CARD" (:__typename %)) deck-cards)]
    ($ :div {:class "mb-12 border border-slate-200 rounded-lg overflow-hidden"}
       ($ :div {:class "bg-slate-50 px-6 py-4 border-b border-slate-200"}
          ($ :h3 {:class "text-xl font-bold text-slate-900"} (:name deck))
          ($ :p {:class "mt-1 text-slate-600"} (:description deck))
          ($ :p {:class "mt-2 text-sm text-slate-500"}
             (:card-count deck) " cards"))
       ($ :div {:class "p-6"}
          (when (seq players)
            ($ :div {:class "mb-4"}
               ($ :h4 {:class "text-lg font-semibold text-slate-800 mb-2"} "Players")
               ($ :div {:class "flex flex-wrap gap-4 justify-center"}
                  (for [card players]
                    ($ card-preview {:key (:slug card) :card card})))))
          ($ :button {:class "w-full py-2 text-sm font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-50 rounded transition-colors"
                      :on-click #(set-expanded! (not expanded?))}
             (if expanded? "Hide Deck Cards ▲" "Show Deck Cards ▼"))
          (when expanded?
            ($ :div {:class "mt-4"}
               ($ :h4 {:class "text-lg font-semibold text-slate-800 mb-2"} "Deck Cards")
               ($ :div {:class "flex flex-wrap gap-4 justify-center"}
                  (for [card non-players]
                    ($ card-preview {:key (:slug card) :card card})))))))))

(defui starter-decks-view
  "Displays all starter decks with card previews for the rules section."
  []
  (let [{:keys [starter-decks loading error]}          (use-starter-decks)
        {:keys [cards loading error] :as cards-result} (use-cards)
        cards-by-slug                                  (use-memo (fn []
                                                                   (if cards
                                                                     (into {} (map (fn [c] [(:slug c) c]) cards))
                                                                     {}))
                                                                 [cards])]
    ($ :div {:class "max-w-5xl"}
       ($ :header {:class "mb-8"}
          ($ :h1 {:class "text-3xl font-bold text-slate-900 tracking-tight"}
             "Starter Decks")
          ($ :p {:class "mt-3 text-lg text-slate-600 leading-relaxed"}
             "Pre-built decks designed to help you learn the game. Each starter deck showcases a different play style."))
       (cond
         (or (:loading cards-result) loading)
         ($ :div {:class "text-center py-12"}
            ($ :p {:class "text-slate-500"} "Loading starter decks..."))

         (or (:error cards-result) error)
         ($ :div {:class "text-center py-12"}
            ($ :p {:class "text-red-600"} "Error loading starter decks"))

         (empty? starter-decks)
         ($ :div {:class "text-center py-12"}
            ($ :p {:class "text-slate-500"} "No starter decks available"))

         :else
         ($ :div
            (for [deck starter-decks]
              ($ starter-deck-section {:key (:id deck)
                                       :deck deck
                                       :cards-by-slug cards-by-slug})))))))
