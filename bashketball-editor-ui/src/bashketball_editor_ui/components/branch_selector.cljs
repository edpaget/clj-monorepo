(ns bashketball-editor-ui.components.branch-selector
  "Branch selector component for switching and creating Git branches."
  (:require
   ["@apollo/client" :refer [useMutation useQuery useApolloClient]]
   ["@radix-ui/react-select" :as SelectPrimitive]
   ["lucide-react" :refer [GitBranch Plus Check ChevronDown AlertCircle]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-ui.components.alert-dialog :refer [alert-dialog alert-dialog-content
                                                   alert-dialog-header alert-dialog-footer
                                                   alert-dialog-title alert-dialog-description
                                                   alert-dialog-cancel alert-dialog-action]]
   [bashketball-ui.components.input :refer [input]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui use-state]]))

(def select-trigger-classes
  "CSS classes for select trigger."
  "flex h-9 items-center justify-between whitespace-nowrap rounded-md border border-gray-200 bg-transparent px-3 py-2 text-sm shadow-sm ring-offset-white focus:outline-none focus:ring-1 focus:ring-gray-950 disabled:cursor-not-allowed disabled:opacity-50")

(def select-content-classes
  "CSS classes for select content dropdown."
  "relative z-50 max-h-96 min-w-[8rem] overflow-hidden rounded-md border border-gray-200 bg-white text-gray-950 shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2")

(def select-item-classes
  "CSS classes for select item."
  "relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none focus:bg-gray-100 focus:text-gray-900 data-[disabled]:pointer-events-none data-[disabled]:opacity-50")

(def new-branch-value "__new_branch__")

(defui branch-selector
  "Dropdown selector for Git branches with create new option."
  []
  (let [client                                  (useApolloClient)
        [new-dialog-open? set-new-dialog-open?] (use-state false)
        [new-branch-name set-new-branch-name]   (use-state "")
        [creating? set-creating?]               (use-state false)
        [error set-error]                       (use-state nil)

        branch-query                            (useQuery q/BRANCH_INFO_QUERY
                                                          #js {:fetchPolicy "cache-and-network"})
        branch-info                             (some-> branch-query :data :branchInfo)
        current-branch                          (:currentBranch branch-info)
        branches                                (js->clj (:branches branch-info))
        loading?                                (:loading branch-query)

        [switch-mutation]                       (useMutation q/SWITCH_BRANCH_MUTATION)
        [create-mutation]                       (useMutation q/CREATE_BRANCH_MUTATION)

        handle-switch                           (fn [branch]
                                                  (set-error nil)
                                                  (-> (switch-mutation #js {:variables #js {:branch branch}})
                                                      (.then (fn [^js result]
                                                               (let [data (.. result -data -switchBranch)]
                                                                 (if (= "success" (:status data))
                                                                   (.refetchQueries client #js {:include "active"})
                                                                   (set-error (:message data))))))
                                                      (.catch (fn [^js e]
                                                                (set-error (.-message e))))))

        handle-create                           (fn []
                                                  (when (seq new-branch-name)
                                                    (set-creating? true)
                                                    (set-error nil)
                                                    (-> (create-mutation #js {:variables #js {:branch new-branch-name}})
                                                        (.then (fn [^js result]
                                                                 (let [data (.. result -data -createBranch)]
                                                                   (if (= "success" (:status data))
                                                                     (do
                                                                       (set-new-dialog-open? false)
                                                                       (set-new-branch-name "")
                                                                       (.refetchQueries client #js {:include "active"}))
                                                                     (set-error (:message data))))))
                                                        (.catch (fn [^js e]
                                                                  (set-error (.-message e))))
                                                        (.finally #(set-creating? false)))))

        handle-value-change                     (fn [value]
                                                  (if (= value new-branch-value)
                                                    (set-new-dialog-open? true)
                                                    (handle-switch value)))]

    (when-not loading?
      ($ :div {:class "relative"}
         ($ SelectPrimitive/Root
            {:value current-branch
             :onValueChange handle-value-change}
            ($ SelectPrimitive/Trigger
               {:class (cn select-trigger-classes "min-w-[140px]")}
               ($ :span {:class "flex items-center gap-2"}
                  ($ GitBranch {:className "w-4 h-4 text-gray-500"})
                  ($ SelectPrimitive/Value {:placeholder "Select branch"}))
               ($ SelectPrimitive/Icon {:asChild true}
                  ($ ChevronDown {:className "h-4 w-4 opacity-50"})))
            ($ SelectPrimitive/Portal
               ($ SelectPrimitive/Content
                  {:class select-content-classes
                   :position "popper"}
                  ($ SelectPrimitive/Viewport
                     {:class "p-1"}
                     (for [branch branches]
                       ($ SelectPrimitive/Item
                          {:key branch
                           :value branch
                           :class select-item-classes}
                          ($ :span {:class "absolute left-2 flex h-3.5 w-3.5 items-center justify-center"}
                             ($ SelectPrimitive/ItemIndicator
                                ($ Check {:className "h-4 w-4"})))
                          ($ SelectPrimitive/ItemText branch)))

                     ($ SelectPrimitive/Separator {:class "-mx-1 my-1 h-px bg-gray-100"})

                     ($ SelectPrimitive/Item
                        {:value new-branch-value
                         :class (cn select-item-classes "text-gray-600")}
                        ($ :span {:class "absolute left-2 flex h-3.5 w-3.5 items-center justify-center"}
                           ($ Plus {:className "h-4 w-4"}))
                        ($ SelectPrimitive/ItemText "New branch..."))))))

         ;; Error toast
         (when error
           ($ :div {:class "absolute top-full mt-2 left-0 right-0 p-2 bg-red-50 border border-red-200 rounded text-sm text-red-700 flex items-center gap-2 whitespace-nowrap"}
              ($ AlertCircle {:className "w-4 h-4 flex-shrink-0"})
              ($ :span error)))

         ;; New branch dialog
         ($ alert-dialog {:open new-dialog-open?
                          :on-open-change (fn [open]
                                            (set-new-dialog-open? open)
                                            (when-not open
                                              (set-new-branch-name "")
                                              (set-error nil)))}
            ($ alert-dialog-content
               ($ alert-dialog-header
                  ($ alert-dialog-title "Create New Branch")
                  ($ alert-dialog-description
                     "Enter a name for the new branch. You will be switched to it immediately."))
               ($ :div {:class "py-4"}
                  ($ input {:value new-branch-name
                            :on-change #(set-new-branch-name (.. % -target -value))
                            :placeholder "feature/my-new-branch"
                            :auto-focus true}))
               (when error
                 ($ :div {:class "mb-4 p-2 bg-red-50 border border-red-200 rounded text-sm text-red-700"}
                    error))
               ($ alert-dialog-footer
                  ($ alert-dialog-cancel {:on-click #(set-new-branch-name "")})
                  ($ alert-dialog-action
                     {:on-click handle-create
                      :disabled (or creating? (empty? new-branch-name))}
                     (if creating? "Creating..." "Create Branch")))))))))
