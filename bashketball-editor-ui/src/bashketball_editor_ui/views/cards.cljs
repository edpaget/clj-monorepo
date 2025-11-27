(ns bashketball-editor-ui.views.cards
  "Card list view.

  Displays a list of cards optionally filtered by set or search terms."
  (:require
   ["lucide-react" :refer [Plus]]
   [bashketball-editor-ui.components.cards.card-list-item :refer [card-list-item
                                                                  card-list-item-skeleton]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.hooks.use-cards :as use-cards]
   [bashketball-editor-ui.router :as router]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(defui empty-state
  "Empty state shown when no cards exist."
  [{:keys [set-slug]}]
  (let [navigate     (router/use-navigate)
        new-card-url (if set-slug
                       (str "/cards/new?set=" set-slug)
                       "/cards/new")]
    ($ :div {:class "flex flex-col items-center justify-center py-16 text-center"}
       ($ :div {:class "w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center mb-4"}
          ($ Plus {:className "w-8 h-8 text-gray-400"}))
       ($ :h3 {:class "text-lg font-medium text-gray-900 mb-1"}
          "No cards yet")
       ($ :p {:class "text-gray-500 mb-4"}
          (if set-slug
            "This set doesn't have any cards. Create your first one."
            "Get started by creating your first card."))
       ($ button {:variant :default
                  :on-click #(navigate new-card-url)}
          ($ Plus {:className "w-4 h-4 mr-2"})
          "New Card"))))

(defui loading-skeleton
  "Loading skeleton for card list."
  []
  ($ :div {:class "divide-y divide-gray-100"}
     (for [i (range 8)]
       ($ card-list-item-skeleton {:key i}))))

(defui cards-view
  "Card list view displaying cards filtered by set and/or type."
  [{:keys [set-slug card-type search-string]}]
  (let [{:keys [loading? cards]} (use-cards/use-cards {:set-slug set-slug
                                                       :card-type card-type})
        navigate                 (router/use-navigate)]
    ($ :div {:class "bg-white rounded-lg shadow overflow-hidden"}
       (cond
         loading?
         ($ loading-skeleton)

         (empty? cards)
         ($ empty-state {:set-slug set-slug})

         :else
         ($ :div {:class "divide-y divide-gray-100"}
            (for [card  cards
                  :when (or (empty? search-string)
                            (str/includes? (str/lower-case (:name card))
                                           (str/lower-case search-string)))]
              ($ card-list-item
                 {:key (:slug card)
                  :card card
                  :on-click #(navigate (str "/cards/" (:slug %)))})))))))
