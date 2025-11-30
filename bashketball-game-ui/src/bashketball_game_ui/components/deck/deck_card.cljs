(ns bashketball-game-ui.components.deck.deck-card
  "Deck card component for displaying a deck in a list."
  (:require
   ["lucide-react" :refer [AlertCircle CheckCircle2 Trash2 Edit]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defui deck-card
  "Displays a deck as a card with name, card count, and validation status.

  Props:
  - `:deck` - Deck map with :id, :name, :card-slugs, :is-valid, :validation-errors
  - `:on-edit` - Callback when edit is clicked (receives deck)
  - `:on-delete` - Callback when delete is clicked (receives deck)
  - `:class` - Additional CSS classes"
  [{:keys [deck on-edit on-delete class]}]
  (let [{:keys [name card-slugs is-valid validation-errors]} deck
        card-count                                           (count card-slugs)]
    ($ :div {:class (cn "bg-white rounded-lg shadow border border-gray-200 p-4 hover:shadow-md transition-shadow"
                        class)}
       ($ :div {:class "flex items-start justify-between"}
          ($ :div {:class "flex-1 min-w-0"}
             ($ :h3 {:class "text-lg font-semibold text-gray-900 truncate"}
                name)
             ($ :p {:class "text-sm text-gray-500 mt-1"}
                (str card-count " cards")))
          ($ :div {:class "flex items-center gap-2 ml-4"}
             (when on-edit
               ($ button
                  {:variant :ghost
                   :size :icon
                   :title "Edit deck"
                   :on-click #(on-edit deck)}
                  ($ Edit {:className "w-4 h-4"})))
             (when on-delete
               ($ button
                  {:variant :ghost
                   :size :icon
                   :title "Delete deck"
                   :on-click #(on-delete deck)}
                  ($ Trash2 {:className "w-4 h-4 text-red-500"})))))
       ($ :div {:class "flex items-center gap-2 mt-3 pt-3 border-t border-gray-100"}
          (if is-valid
            ($ :<>
               ($ CheckCircle2 {:className "w-4 h-4 text-green-500"})
               ($ :span {:class "text-sm text-green-600"} "Valid"))
            ($ :<>
               ($ AlertCircle {:className "w-4 h-4 text-amber-500"})
               ($ :span {:class "text-sm text-amber-600"}
                  (if (seq validation-errors)
                    (first validation-errors)
                    "Needs more cards"))))))))

(defui deck-card-skeleton
  "Loading skeleton for deck card."
  []
  ($ :div {:class "bg-white rounded-lg shadow border border-gray-200 p-4 animate-pulse"}
     ($ :div {:class "flex items-start justify-between"}
        ($ :div {:class "flex-1"}
           ($ :div {:class "h-6 bg-gray-200 rounded w-32"})
           ($ :div {:class "h-4 bg-gray-200 rounded w-20 mt-2"}))
        ($ :div {:class "flex gap-2"}
           ($ :div {:class "w-9 h-9 bg-gray-200 rounded"})
           ($ :div {:class "w-9 h-9 bg-gray-200 rounded"})))
     ($ :div {:class "flex items-center gap-2 mt-3 pt-3 border-t border-gray-100"}
        ($ :div {:class "w-4 h-4 bg-gray-200 rounded-full"})
        ($ :div {:class "h-4 bg-gray-200 rounded w-16"}))))
