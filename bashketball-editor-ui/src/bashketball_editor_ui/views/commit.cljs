(ns bashketball-editor-ui.views.commit
  "Commit page for reviewing changes and writing commit messages."
  (:require
   ["@apollo/client" :refer [useMutation useQuery]]
   ["lucide-react" :refer [ArrowLeft FilePlus FileX FilePenLine FileQuestion GitCommit]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.components.ui.loading :refer [spinner]]
   [bashketball-editor-ui.components.ui.textarea :refer [textarea]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui file-list
  "Displays a list of files with an icon and styling."
  [{:keys [files icon icon-class label]}]
  (when (seq files)
    ($ :div {:class "space-y-1"}
       ($ :div {:class "text-sm font-medium text-gray-500 flex items-center gap-2"}
          ($ icon {:className (str "w-4 h-4 " icon-class)})
          ($ :span label)
          ($ :span {:class "text-gray-400"} (str "(" (count files) ")")))
       ($ :ul {:class "ml-6 space-y-0.5"}
          (for [file files]
            ($ :li {:key file :class "text-sm text-gray-700 font-mono"} file))))))

(defui commit-view
  "Page for reviewing and committing changes."
  []
  (let [navigate                        (router/use-navigate)
        [message set-message]           (use-state "")
        [committing? set-committing?]   (use-state false)
        [error set-error]               (use-state nil)

        ;; Query working tree status
        status-query (useQuery q/WORKING_TREE_STATUS_QUERY
                               #js {:fetchPolicy "network-only"})
        status       (some-> status-query :data :workingTreeStatus)
        loading?     (:loading status-query)

        added        (js->clj (:added status))
        modified     (js->clj (:modified status))
        deleted      (js->clj (:deleted status))
        untracked    (js->clj (:untracked status))
        is-dirty?    (:isDirty status)

        total-changes (+ (count added) (count modified) (count deleted))

        [commit-mutation] (useMutation q/COMMIT_CHANGES_MUTATION)

        handle-commit (fn []
                        (set-committing? true)
                        (set-error nil)
                        (-> (commit-mutation #js {:variables #js {:message (when (seq message) message)}})
                            (.then (fn [^js result]
                                     (let [data (.. result -data -commitChanges)]
                                       (if (.-success data)
                                         (navigate "/")
                                         (set-error (.-message data))))))
                            (.catch (fn [^js e]
                                      (set-error (.-message e))))
                            (.finally #(set-committing? false))))]

    ($ :div {:class "max-w-3xl mx-auto"}
       ;; Header
       ($ :div {:class "flex items-center gap-4 mb-6"}
          ($ button {:variant :ghost :on-click #(navigate "/")}
             ($ ArrowLeft {:className "w-4 h-4 mr-2"})
             "Back")
          ($ :h1 {:class "text-2xl font-bold"} "Commit Changes"))

       (cond
         loading?
         ($ :div {:class "flex justify-center py-12"}
            ($ spinner {:size :lg}))

         (not is-dirty?)
         ($ :div {:class "bg-white rounded-lg shadow p-8 text-center"}
            ($ :p {:class "text-gray-500 text-lg"} "No changes to commit.")
            ($ :p {:class "text-gray-400 text-sm mt-2"} "Your working tree is clean."))

         :else
         ($ :div {:class "space-y-6"}
            ;; Changes summary
            ($ :div {:class "bg-white rounded-lg shadow p-6"}
               ($ :h2 {:class "text-lg font-semibold mb-4"}
                  (str total-changes " change" (when (not= total-changes 1) "s") " to commit"))

               ($ :div {:class "space-y-4"}
                  ($ file-list {:files added
                                :icon FilePlus
                                :icon-class "text-green-600"
                                :label "Added"})
                  ($ file-list {:files modified
                                :icon FilePenLine
                                :icon-class "text-yellow-600"
                                :label "Modified"})
                  ($ file-list {:files deleted
                                :icon FileX
                                :icon-class "text-red-600"
                                :label "Deleted"}))

               (when (seq untracked)
                 ($ :div {:class "mt-4 pt-4 border-t"}
                    ($ file-list {:files untracked
                                  :icon FileQuestion
                                  :icon-class "text-gray-400"
                                  :label "Untracked (not staged)"}))))

            ;; Commit message
            ($ :div {:class "bg-white rounded-lg shadow p-6"}
               ($ :label {:for "commit-message" :class "block text-sm font-medium text-gray-700 mb-2"}
                  "Commit Message")
               ($ textarea {:id "commit-message"
                            :value message
                            :on-change #(set-message (.. % -target -value))
                            :placeholder "Describe your changes... (optional)"
                            :rows 4})
               ($ :p {:class "text-xs text-gray-400 mt-1"}
                  "Leave blank for default message: \"Update cards and sets\""))

            ;; Error message
            (when error
              ($ :div {:class "p-4 bg-red-50 border border-red-200 rounded-lg text-red-700"}
                 error))

            ;; Actions
            ($ :div {:class "flex justify-end gap-4"}
               ($ button {:variant :outline
                          :on-click #(navigate "/")}
                  "Cancel")
               ($ button {:disabled committing?
                          :on-click handle-commit}
                  ($ GitCommit {:className "w-4 h-4 mr-2"})
                  (if committing? "Committing..." "Commit Changes"))))))))
