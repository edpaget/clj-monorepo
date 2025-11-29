(ns bashketball-editor-ui.views.home
  "Home view component.

  Displays the main landing page with navigation to card editing features."
  (:require
   [bashketball-editor-ui.components.cards.card-type-selector :refer [card-type-selector]]
   [bashketball-editor-ui.components.cards.set-selector :refer [set-selector]]
   [bashketball-editor-ui.context.auth :refer [use-auth]]
   [bashketball-editor-ui.views.cards :refer [cards-view]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.components.input :refer [input]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui home-view
  "Main home view displaying the application landing page."
  []
  (let [{:keys [logged-in?]}          (use-auth)
        [search-params]               (router/use-search-params)
        set-slug                      (.get search-params "set")
        card-type                     (.get search-params "type")
        [search-term set-search-term] (use-state "")
        navigate                      (router/use-navigate)
        new-card-url                  (if set-slug
                                        (str "/cards/new?set=" set-slug)
                                        "/cards/new")]
    ($ :div
       ($ :div {:class "mb-6"}
          ($ :div {:class "flex gap-4 items-center flex-wrap justify-between"}
             ($ :div {:class "flex gap-4 items-center"}
                ($ set-selector {:current-set-slug set-slug
                                 :class "w-48"})
                ($ card-type-selector {:current-card-type card-type
                                       :class "w-48"}))
             ($ :div {:class "flex gap-4 items-center"}
                ($ input
                   {:placeholder "Search cards..."
                    :value search-term
                    :on-change #(set-search-term (.. % -target -value))
                    :class "max-w-xs"})
                (when logged-in?
                  ($ button {:variant :outline
                             :on-click #(navigate new-card-url)}
                     "New Card")))))
       ($ :div {:class "mt-8"}
          ($ cards-view {:set-slug set-slug
                         :card-type card-type
                         :search-string search-term})))))
