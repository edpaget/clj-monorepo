(ns bashketball-editor-ui.views.home
  "Home view component.

  Displays the main landing page with navigation to card editing features."
  (:require
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.components.ui.input :refer [input]]
   [uix.core :refer [$ defui use-state]]))

(defui home-view
  "Main home view displaying the application landing page."
  []
  (let [[search-term set-search-term] (use-state "")]
    ($ :div
       ($ :div {:class "mb-6"}
          ($ :p {:class "text-gray-600 mb-4"}
             "Create and edit trading cards for the Bashketball card game.")
          ($ :div {:class "flex gap-4 items-center"}
             ($ input
                {:placeholder "Search cards..."
                 :value search-term
                 :on-change #(set-search-term (.. % -target -value))
                 :class "max-w-xs"})
             ($ button {:variant :default} "Search")
             ($ button {:variant :outline} "New Card")))
       ($ :div {:class "mt-8 p-8 bg-white rounded-lg shadow text-center text-gray-500"}
          "Card list will appear here once the API is connected."))))
