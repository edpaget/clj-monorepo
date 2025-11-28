(ns bashketball-editor-ui.views.card-view
  "Card detail view for displaying a single card."
  (:require
   ["@apollo/client" :refer [useQuery]]
   ["lucide-react" :refer [ArrowLeft Edit]]
   [bashketball-editor-ui.components.cards.card-preview :refer [card-preview]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.components.ui.loading :refer [spinner]]
   [bashketball-editor-ui.context.auth :refer [use-auth]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui card-view
  "Main card detail view."
  []
  (let [{:keys [logged-in?]} (use-auth)
        params     (router/use-params)
        navigate   (router/use-navigate)
        slug       (:slug params)
        set-slug   (:setSlug params)

        card-query (useQuery q/CARD_QUERY
                             (clj->js {:variables {:slug (or slug "")
                                                   :setSlug (or set-slug "")}
                                       :skip (or (nil? slug) (nil? set-slug))}))
        loading?   (:loading card-query)
        error      (:error card-query)
        card       (some-> card-query :data :card)]

    ($ :div {:class "max-w-4xl mx-auto"}
       ;; Header
       ($ :div {:class "flex items-center justify-between mb-6"}
          ($ :div {:class "flex items-center gap-4"}
             ($ button {:variant :ghost
                        :on-click #(navigate (if set-slug
                                               (str "/?set=" set-slug)
                                               "/"))}
                ($ ArrowLeft {:className "w-4 h-4 mr-2"})
                "Back")
             (when card
               ($ :h1 {:class "text-2xl font-bold"} (:name card))))
          (when (and card logged-in?)
            ($ button {:variant :outline
                       :on-click #(navigate (str "/cards/" set-slug "/" slug "/edit"))}
               ($ Edit {:className "w-4 h-4 mr-2"})
               "Edit")))

       ;; Content
       (cond
         loading?
         ($ :div {:class "flex justify-center py-12"}
            ($ spinner {:size :lg}))

         error
         ($ :div {:class "p-6 bg-red-50 border border-red-200 rounded-lg text-red-700"}
            "Error loading card: " (:message error))

         (nil? card)
         ($ :div {:class "p-6 bg-yellow-50 border border-yellow-200 rounded-lg text-yellow-700"}
            "Card not found. Make sure you have the correct set selected.")

         :else
         ($ :div {:class "flex justify-center"}
            ($ card-preview {:card card}))))))
