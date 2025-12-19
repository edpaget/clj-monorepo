(ns bashketball-game-ui.views.content
  "View component for displaying static content pages."
  (:require
   [bashketball-game-ui.content.registry :as registry]
   [bashketball-game-ui.content.renderer :refer [content-page]]
   [bashketball-game-ui.views.starter-decks :refer [starter-decks-view]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]))

(def dynamic-views
  "Map of dynamic content slugs to their view components."
  {"starter-decks" starter-decks-view})

(defui content-view
  "Displays a content page based on route params.

  Reads `:category` and `:slug` from route params to look up content.
  For dynamic content (like starter-decks), renders the appropriate component."
  [{:keys [category]}]
  (let [params       (router/use-params)
        slug         (:slug params)
        content      (registry/get-content category slug)
        dynamic-view (get dynamic-views slug)]
    (cond
      (:dynamic? content)
      (if dynamic-view
        ($ dynamic-view)
        ($ :div {:class "text-center py-16"}
           ($ :p {:class "text-red-600"} "Dynamic view not implemented")))

      content
      ($ content-page {:content content})

      :else
      ($ :div {:class "text-center py-16"}
         ($ :h2 {:class "text-2xl font-bold text-gray-900"} "Content Not Found")
         ($ :p {:class "text-gray-600 mt-2"}
            "The page you're looking for doesn't exist.")))))
