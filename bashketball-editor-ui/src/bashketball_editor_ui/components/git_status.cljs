(ns bashketball-editor-ui.components.git-status
  "Git repository status indicators.

  Shows dirty state, sync status, and provides commit/push functionality."
  (:require
   ["@apollo/client" :refer [useMutation useQuery]]
   ["lucide-react" :refer [ArrowDown ArrowUp Circle CloudUpload GitCommit]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui commit-button
  "Button that navigates to the commit page."
  []
  (let [navigate (router/use-navigate)]
    ($ button
       {:variant :ghost
        :size :sm
        :title "Review and commit changes"
        :on-click #(navigate "/commit")}
       ($ GitCommit {:className "w-4 h-4"}))))

(defui push-button
  "Button to push commits to remote."
  [{:keys [on-success]}]
  (let [[status set-status]   (use-state :idle) ; :idle, :pushing, :success, :error
        [error-msg set-error] (use-state nil)
        [push-mutation]       (useMutation q/PUSH_TO_REMOTE_MUTATION)]
    ($ button
       {:variant :ghost
        :size :sm
        :disabled (= status :pushing)
        :title (case status
                 :error (or error-msg "Push failed")
                 :success "Pushed successfully"
                 "Push to remote")
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
       ($ CloudUpload {:className (str "w-4 h-4"
                                       (case status
                                         :pushing " animate-pulse"
                                         :success " text-green-600"
                                         :error " text-red-600"
                                         ""))}))))

(defui status-badge
  "Small badge showing a count with icon."
  [{:keys [count icon class title]}]
  (when (and count (pos? count))
    ($ :div {:class (str "flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium " class)
             :title title}
       ($ icon {:className "w-3 h-3"})
       ($ :span count))))

(defui git-status
  "Shows repository sync status and dirty state.

  Displays:
  - Green dot when clean, yellow dot when dirty
  - Commits ahead (to push) count
  - Commits behind (to pull) count
  - Commit button when there are uncommitted changes"
  []
  (let [sync-query    (useQuery q/SYNC_STATUS_QUERY
                                #js {:pollInterval 30000
                                     :fetchPolicy "network-only"})
        sync-status   (some-> sync-query :data :syncStatus)
        loading?      (:loading sync-query)

        ahead         (:ahead sync-status)
        behind        (:behind sync-status)
        uncommitted   (:uncommittedChanges sync-status)
        has-changes?  (and (some? uncommitted) (pos? uncommitted))]

    (when-not loading?
      ($ :div {:class "flex items-center gap-2"}
         ;; Dirty/clean indicator
         ($ :div {:class "flex items-center gap-1"
                  :title (if has-changes?
                           (str uncommitted " uncommitted change"
                                (when (not= uncommitted 1) "s"))
                           "Working tree is clean")}
            ($ Circle {:className (str "w-3 h-3 fill-current "
                                       (if has-changes?
                                         "text-yellow-500"
                                         "text-green-500"))})
            (when has-changes?
              ($ :span {:class "text-xs text-gray-600"} uncommitted)))

         ;; Ahead count (commits to push)
         ($ status-badge {:count ahead
                          :icon ArrowUp
                          :class "bg-blue-100 text-blue-700"
                          :title (str ahead " commit" (when (not= ahead 1) "s") " ahead of remote")})

         ;; Behind count (commits to pull)
         ($ status-badge {:count behind
                          :icon ArrowDown
                          :class "bg-orange-100 text-orange-700"
                          :title (str behind " commit" (when (not= behind 1) "s") " behind remote")})

         ;; Commit button when dirty
         (when has-changes?
           ($ commit-button))

         ;; Push button when ahead of remote
         (when (and ahead (pos? ahead))
           ($ push-button {:on-success #(.refetch sync-query)}))))))
