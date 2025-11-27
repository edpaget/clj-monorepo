(ns bashketball-editor-ui.views.home
  "Home view component.

  Displays the main landing page with navigation to card editing features."
  (:require
   [bashketball-editor-ui.components.cards.set-selector :refer [set-selector]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.components.ui.input :refer [input]]
   [bashketball-editor-ui.router :as router]
   [bashketball-editor-ui.views.cards :refer [cards-view]]
   [uix.core :refer [$ defui use-state]]))

(defui home-view
  "Main home view displaying the application landing page."
  []
  (let [{:keys [set-slug]} (router/use-params)
        [search-term set-search-term] (use-state "")]
    ($ :div
       ($ :div {:class "mb-6"}
          ($ :div {:class "flex gap-4 items-center flex-wrap"}
             ($ set-selector {:current-set-slug set-slug
                              :class "w-48"})
             ($ input
                {:placeholder "Search cards..."
                 :value search-term
                 :on-change #(set-search-term (.. % -target -value))
                 :class "max-w-xs"})
             ($ button {:variant :default} "Search")
             ($ button {:variant :outline} "New Card")))
       ($ :div {:class "mt-8"}
          ($ cards-view {:set-slug set-slug})))))
