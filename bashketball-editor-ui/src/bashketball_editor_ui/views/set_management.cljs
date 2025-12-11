(ns bashketball-editor-ui.views.set-management
  "Set management view for viewing and deleting card sets."
  (:require
   ["@apollo/client" :refer [useApolloClient useQuery]]
   ["lucide-react" :refer [ArrowLeft Trash2 Folder]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-ui.components.alert-dialog :as alert]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui set-row
  "A single set row with delete functionality."
  [{:keys [set-data on-delete]}]
  (let [[delete-open? set-delete-open?] (use-state false)
        [deleting? set-deleting?]       (use-state false)
        {:keys [slug name]}             set-data]
    ($ :div {:class "flex items-center justify-between p-4 border-b border-gray-100 last:border-b-0 hover:bg-gray-50"}
       ($ :div {:class "flex items-center gap-3"}
          ($ Folder {:className "w-5 h-5 text-gray-400"})
          ($ :div
             ($ :div {:class "font-medium"} name)
             ($ :div {:class "text-sm text-gray-500"} slug)))
       ($ alert/alert-dialog {:open delete-open? :on-open-change set-delete-open?}
          ($ alert/alert-dialog-trigger
             ($ button {:variant :ghost :size :icon :title "Delete set"}
                ($ Trash2 {:className "w-4 h-4 text-red-500"})))
          ($ alert/alert-dialog-content
             ($ alert/alert-dialog-header
                ($ alert/alert-dialog-title "Delete Card Set")
                ($ alert/alert-dialog-description
                   (str "Are you sure you want to delete the set \"" name "\"? "
                        "This will also delete ALL CARDS in this set. "
                        "This action will stage the deletions. You must commit to make it permanent.")))
             ($ alert/alert-dialog-footer
                ($ alert/alert-dialog-cancel)
                ($ alert/alert-dialog-action
                   {:on-click (fn []
                                (set-deleting? true)
                                (on-delete slug
                                           #(do (set-delete-open? false)
                                                (set-deleting? false))
                                           #(set-deleting? false)))
                    :disabled deleting?}
                   (if deleting? "Deleting..." "Delete Set and All Cards"))))))))

(defui set-management-view
  "View for managing card sets with delete functionality."
  []
  (let [navigate               (router/use-navigate)
        client                 (useApolloClient)
        {:keys [loading data]} (js->clj (useQuery q/CARD_SETS_QUERY) :keywordize-keys true)
        sets                   (some-> data :cardSets :data)
        [error set-error]      (use-state nil)

        handle-delete          (fn [slug on-success on-error]
                                 (set-error nil)
                                 (-> (.mutate client
                                              (clj->js {:mutation q/DELETE_CARD_SET_MUTATION
                                                        :variables {:slug slug}
                                                        :refetchQueries #js ["CardSets" "Cards" "SyncStatus"]}))
                                     (.then (fn [_] (on-success)))
                                     (.catch (fn [e]
                                               (set-error (str "Delete failed: " (:message (js->clj e :keywordize-keys true))))
                                               (on-error)))))]

    ($ :div {:class "max-w-2xl mx-auto"}
       ($ :div {:class "flex items-center gap-4 mb-6"}
          ($ button {:variant :ghost :on-click #(navigate "/")}
             ($ ArrowLeft {:className "w-4 h-4 mr-2"})
             "Back")
          ($ :h1 {:class "text-2xl font-bold"} "Manage Sets"))

       (when error
         ($ :div {:class "mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-red-700 text-sm"}
            error))

       ($ :div {:class "bg-white rounded-lg shadow"}
          (cond
            loading
            ($ :div {:class "flex justify-center py-12"}
               ($ spinner {:size :lg}))

            (empty? sets)
            ($ :div {:class "text-center py-12 text-gray-500"}
               "No sets found. Create a set to get started.")

            :else
            (for [s sets]
              ($ set-row {:key (:slug s)
                          :set-data s
                          :on-delete handle-delete})))))))
