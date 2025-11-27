(ns bashketball-editor-ui.context.auth
  "Authentication context for providing user state throughout the app."
  (:require
   [bashketball-editor-ui.config :as config]
   [bashketball-editor-ui.hooks.use-me :refer [use-me]]
   [uix.core :refer [$ defui create-context use-context]]))

(def ^:private auth-context (create-context nil))

(defui auth-provider
  "Provides authentication state to child components.

  Queries the `me` endpoint and provides user state via context."
  [{:keys [children]}]
  (let [auth-state (use-me)]
    ($ (.-Provider auth-context) {:value auth-state}
       children)))

(defn use-auth
  "Hook to access authentication state.

  Returns a map with:
  - `:user` - The current user or nil
  - `:loading?` - Whether auth state is loading
  - `:logged-in?` - Whether user is logged in
  - `:refetch` - Function to refetch auth state"
  []
  (use-context auth-context))

(defn logout!
  "Logs out the user by calling the logout endpoint and refetching auth state."
  [refetch]
  (-> (js/fetch (str config/api-base-url "/auth/logout")
                #js {:method "POST"
                     :credentials "include"})
      (.then (fn [_] (refetch)))))
