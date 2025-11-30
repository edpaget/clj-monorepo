(ns bashketball-ui.context.auth
  "Configurable authentication context for providing user state throughout the app.

  This module provides a context and provider component that can be customized
  for different backends by passing a user hook as a prop."
  (:require
   [uix.core :refer [$ defui create-context use-context]]))

(def auth-context
  "Context for authentication state. Use [[auth-provider]] to provide state
  and [[use-auth]] to consume it."
  (create-context nil))

(defui auth-provider
  "Auth provider component that provides auth state to children via context.

  Props:
  - `:use-user-hook` - A React hook function that returns auth state map with keys:
    - `:user` - The current user or nil
    - `:loading?` - Whether auth state is loading
    - `:logged-in?` - Whether user is logged in
    - `:refetch` - Function to refetch auth state
  - `:children` - Child components to render

  Example:
  ```clojure
  ($ auth-provider {:use-user-hook my-app.hooks/use-me}
     ($ child-components))
  ```"
  [{:keys [use-user-hook children]}]
  (let [auth-state (use-user-hook)]
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

(defn create-logout-fn
  "Creates a logout function for a specific backend.

  Takes a config map with:
  - `:logout-url` - The URL to POST to for logout

  Returns a function that takes a refetch callback and logs out the user."
  [{:keys [logout-url]}]
  (fn logout! [refetch]
    (-> (js/fetch logout-url
                  #js {:method "POST"
                       :credentials "include"})
        (.then (fn [_] (refetch))))))
