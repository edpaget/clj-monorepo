(ns bashketball-game-ui.views.content
  "View component for displaying static content pages."
  (:require
   [bashketball-game-ui.content.registry :as registry]
   [bashketball-game-ui.content.renderer :refer [content-page]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui content-view
  "Displays a content page based on route params.

  Reads `:category` and `:slug` from route params to look up content."
  [{:keys [category]}]
  (let [params  (router/use-params)
        slug    (:slug params)
        content (registry/get-content category slug)]
    (if content
      ($ content-page {:content content})
      ($ :div {:class "text-center py-16"}
         ($ :h2 {:class "text-2xl font-bold text-gray-900"} "Content Not Found")
         ($ :p {:class "text-gray-600 mt-2"}
            "The page you're looking for doesn't exist.")))))
