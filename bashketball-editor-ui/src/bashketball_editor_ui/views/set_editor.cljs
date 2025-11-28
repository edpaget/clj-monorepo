(ns bashketball-editor-ui.views.set-editor
  "Set editor view for creating new card sets."
  (:require
   ["@apollo/client" :refer [useApolloClient]]
   ["lucide-react" :refer [ArrowLeft Save]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.components.ui.input :refer [input]]
   [bashketball-editor-ui.components.ui.label :refer [label]]
   [bashketball-editor-ui.components.ui.textarea :refer [textarea]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.hooks.form :as form]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui form-field
  "Wrapper for form fields with label."
  [{:keys [id label-text children]}]
  ($ :div {:class "space-y-2"}
     ($ label {:for id} label-text)
     children))

(defui set-editor-view
  "View for creating a new card set."
  []
  (let [navigate              (router/use-navigate)
        client                (useApolloClient)
        {:keys [data update]} (form/use-form {:name "" :description ""})
        [saving? set-saving?] (use-state false)
        [error set-error]     (use-state nil)

        handle-submit         (fn []
                                (when (seq (:name data))
                                  (set-saving? true)
                                  (set-error nil)
                                  (-> (.mutate client
                                               (clj->js {:mutation q/CREATE_CARD_SET_MUTATION
                                                         :variables {:input {:name (:name data)
                                                                             :description (when (seq (:description data))
                                                                                            (:description data))}}
                                                         :refetchQueries #js ["CardSets" "SyncStatus"]}))
                                      (.then (fn [result]
                                               (let [slug (-> result :data :createCardSet :slug)]
                                                 (navigate (str "/?set=" slug)))))
                                      (.catch (fn [e]
                                                (set-error (:message e))))
                                      (.finally #(set-saving? false)))))]

    ($ :div {:class "max-w-2xl mx-auto"}
       ($ :div {:class "flex items-center gap-4 mb-6"}
          ($ button {:variant :ghost :on-click #(navigate "/")}
             ($ ArrowLeft {:className "w-4 h-4 mr-2"})
             "Back")
          ($ :h1 {:class "text-2xl font-bold"} "Create New Set"))

       ($ :div {:class "bg-white rounded-lg shadow p-6"}
          (when error
            ($ :div {:class "mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-red-700 text-sm"}
               error))

          ($ :form {:on-submit (fn [e]
                                 (.preventDefault e)
                                 (handle-submit))
                    :class "space-y-6"}
             ($ form-field {:id "name" :label-text "Set Name"}
                ($ input {:id "name"
                          :value (:name data)
                          :on-change (form/field-handler update :name)
                          :placeholder "Enter set name..."
                          :required true
                          :auto-focus true}))

             ($ form-field {:id "description" :label-text "Description (optional)"}
                ($ textarea {:id "description"
                             :value (:description data)
                             :on-change (form/field-handler update :description)
                             :placeholder "Describe this card set..."
                             :rows 3}))

             ($ :div {:class "flex gap-4 pt-4"}
                ($ button {:type "submit"
                           :disabled (or saving? (empty? (:name data)))}
                   ($ Save {:className "w-4 h-4 mr-2"})
                   (if saving? "Creating..." "Create Set"))))))))
