(ns bashketball-editor-ui.components.sync-button
  "Git sync button component for pulling from remote."
  (:require
   ["@apollo/client" :refer [useMutation useApolloClient]]
   ["lucide-react" :refer [RefreshCw Check AlertCircle]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.graphql.queries :as q]
   [uix.core :refer [$ defui use-state use-effect]]))

(defui sync-button
  "Button to pull changes from remote Git repository.

  Shows different states:
  - Idle: Shows refresh icon
  - Loading: Shows spinning refresh icon
  - Success: Shows checkmark briefly
  - Error: Shows alert icon with tooltip

  On success, invalidates Apollo cache and refetches all active queries."
  []
  (let [[status set-status]               (use-state :idle) ; :idle, :loading, :success, :error
        [error-msg set-error-msg]         (use-state nil)
        [pull-mutation {:keys [loading]}] (useMutation q/PULL_FROM_REMOTE_MUTATION)
        client                            (useApolloClient)
        reset-needed?                     (or (= status :success) (= status :error))]

    ;; Reset success/error state after delay
    (use-effect
     (fn []
       (when reset-needed?
         (let [timer (js/setTimeout #(set-status :idle) 3000)]
           #(js/clearTimeout timer))))
     [reset-needed?])

    ($ button
       {:variant :ghost
        :size :sm
        :disabled loading
        :title (case status
                 :error (or error-msg "Pull failed")
                 :success "Pull successful"
                 "Pull from remote")
        :on-click (fn []
                    (set-status :loading)
                    (-> (pull-mutation)
                        (.then (fn [^js result]
                                 (let [data       (.. result -data -pullFromRemote)
                                       res-status (.-status data)]
                                   (if (= res-status "success")
                                     (-> (.refetchQueries client #js {:include "active"})
                                         (.then (fn []
                                                  (set-status :success)
                                                  (set-error-msg nil))))
                                     (do
                                       (set-status :error)
                                       (set-error-msg (or (.-error data)
                                                          (.-message data))))))))
                        (.catch (fn [^js e]
                                  (set-status :error)
                                  (set-error-msg (.-message e))))))}
       (case status
         :loading ($ RefreshCw {:className "w-4 h-4 animate-spin"})
         :success ($ Check {:className "w-4 h-4 text-green-600"})
         :error ($ AlertCircle {:className "w-4 h-4 text-red-600"})
         ($ RefreshCw {:className "w-4 h-4"})))))
