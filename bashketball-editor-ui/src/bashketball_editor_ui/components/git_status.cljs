(ns bashketball-editor-ui.components.git-status
  "Git repository status indicators.

  Shows dirty state, sync status, and provides commit/push/pull functionality
  in a clear workflow-oriented layout."
  (:require
   ["@apollo/client" :refer [useMutation useQuery useApolloClient]]
   ["lucide-react" :refer [ArrowDown ArrowUp Circle Check AlertCircle]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui use-state use-effect]]))

(defui commit-button
  "Button that navigates to the commit page. Shows text label for clarity."
  [{:keys [count]}]
  (let [navigate (router/use-navigate)]
    ($ button
       {:variant :outline
        :size :sm
        :title "Review and commit your changes"
        :on-click #(navigate "/commit")}
       ($ Circle {:className "w-3 h-3 fill-current text-yellow-500 mr-1.5"})
       (str count " to commit"))))

(defui push-button
  "Button to push commits to remote. Shows count and text label."
  [{:keys [count on-success]}]
  (let [[status set-status]   (use-state :idle) ; :idle, :pushing, :success, :error
        [error-msg set-error] (use-state nil)
        [push-mutation]       (useMutation q/PUSH_TO_REMOTE_MUTATION)]
    ($ button
       {:variant :outline
        :size :sm
        :disabled (= status :pushing)
        :title (case status
                 :error (or error-msg "Push failed")
                 :success "Pushed successfully"
                 "Share your commits with the remote repository")
        :on-click (fn []
                    (set-status :pushing)
                    (-> (push-mutation)
                        (.then (fn [^js result]
                                 (let [data (.. result -data -pushToRemote)]
                                   (if (= (.-status data) "success")
                                     (do
                                       (set-status :success)
                                       (when on-success (on-success))
                                       (js/setTimeout #(set-status :idle) 2000))
                                     (do
                                       (set-status :error)
                                       (set-error (or (.-error data) (.-message data)))
                                       (js/setTimeout #(set-status :idle) 3000))))))
                        (.catch (fn [^js e]
                                  (set-status :error)
                                  (set-error (.-message e))
                                  (js/setTimeout #(set-status :idle) 3000)))))}
       ($ ArrowUp {:className (str "w-3 h-3 mr-1.5"
                                   (case status
                                     :pushing "animate-pulse"
                                     :success "text-green-600"
                                     :error "text-red-600"
                                     ""))})
       (case status
         :pushing "Pushing..."
         :success "Pushed!"
         :error "Failed"
         (str count " to push")))))

(defui pull-button
  "Button to pull commits from remote. Shows count and text label."
  [{:keys [count on-success]}]
  (let [[status set-status]   (use-state :idle) ; :idle, :pulling, :success, :error
        [error-msg set-error] (use-state nil)
        [pull-mutation]       (useMutation q/PULL_FROM_REMOTE_MUTATION)
        client                (useApolloClient)
        reset-needed?         (or (= status :success) (= status :error))]

    (use-effect
     (fn []
       (when reset-needed?
         (let [timer (js/setTimeout #(set-status :idle) 3000)]
           #(js/clearTimeout timer))))
     [reset-needed?])

    ($ button
       {:variant :outline
        :size :sm
        :disabled (= status :pulling)
        :title (case status
                 :error (or error-msg "Pull failed")
                 :success "Pull successful"
                 "Get updates from the remote repository")
        :on-click (fn []
                    (set-status :pulling)
                    (-> (pull-mutation)
                        (.then (fn [^js result]
                                 (let [data (.. result -data -pullFromRemote)]
                                   (if (= (.-status data) "success")
                                     (-> (.refetchQueries client #js {:include "active"})
                                         (.then (fn []
                                                  (set-status :success)
                                                  (set-error nil)
                                                  (when on-success (on-success)))))
                                     (do
                                       (set-status :error)
                                       (set-error (or (.-error data) (.-message data))))))))
                        (.catch (fn [^js e]
                                  (set-status :error)
                                  (set-error (.-message e))))))}
       (case status
         :pulling ($ :span {:class "flex items-center"}
                     ($ ArrowDown {:className "w-3 h-3 mr-1.5 animate-pulse"})
                     "Pulling...")
         :success ($ :span {:class "flex items-center"}
                     ($ Check {:className "w-3 h-3 mr-1.5 text-green-600"})
                     "Pulled!")
         :error ($ :span {:class "flex items-center"}
                   ($ AlertCircle {:className "w-3 h-3 mr-1.5 text-red-600"})
                   "Failed")
         ($ :span {:class "flex items-center"}
            ($ ArrowDown {:className "w-3 h-3 mr-1.5"})
            (str count " to pull"))))))

(defui clean-indicator
  "Shows a green checkmark when everything is synced."
  []
  ($ :div {:class "flex items-center gap-1.5 text-green-600 text-sm"
           :title "All changes saved and synced"}
     ($ Check {:className "w-4 h-4"})
     ($ :span "All synced")))

(defui git-status
  "Shows repository sync status and provides commit/push/pull actions.

  Groups related actions into a clear workflow:
  - Local changes: Shows uncommitted count with Commit button
  - Remote sync: Shows Push button (when ahead) and Pull button (when behind)
  - Clean state: Shows green checkmark when fully synced"
  []
  (let [sync-query   (useQuery q/SYNC_STATUS_QUERY
                               #js {:pollInterval 30000
                                    :fetchPolicy "network-only"})
        sync-status  (some-> sync-query :data :syncStatus)
        loading?     (:loading sync-query)

        ahead        (:ahead sync-status)
        behind       (:behind sync-status)
        uncommitted  (:uncommittedChanges sync-status)
        has-changes? (and (some? uncommitted) (pos? uncommitted))
        has-ahead?   (and ahead (pos? ahead))
        has-behind?  (and behind (pos? behind))
        is-clean?    (and (not has-changes?) (not has-ahead?) (not has-behind?))]

    (when-not loading?
      ($ :div {:class "flex items-center gap-2"}
         (if is-clean?
           ;; Everything is synced
           ($ clean-indicator)

           ;; Show workflow buttons
           ($ :fragment
              ;; Step 1: Local changes need committing
              (when has-changes?
                ($ commit-button {:count uncommitted}))

              ;; Step 2: Commits ready to push
              (when has-ahead?
                ($ push-button {:count ahead
                                :on-success #(.refetch sync-query)}))

              ;; Step 3: Updates available to pull
              (when has-behind?
                ($ pull-button {:count behind
                                :on-success #(.refetch sync-query)}))))))))
