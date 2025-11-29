(ns bashketball-ui.context.auth
  "Configurable authentication context for providing user state throughout the app.

  This module provides a factory function to create auth context and provider
  components that can be customized for different backends."
  (:require
   [uix.core :refer [$ defui create-context use-context]]))

(def auth-context
  "Context for authentication state. Use [[create-auth-provider]] to create
  a provider component and [[use-auth]] to consume state."
  (create-context nil))

(defn create-auth-provider
  "Creates an auth provider component configured for a specific backend.

  Takes a config map with:
  - `:use-user-hook` - A React hook that returns auth state map with keys:
    - `:user` - The current user or nil
    - `:loading?` - Whether auth state is loading
    - `:logged-in?` - Whether user is logged in
    - `:refetch` - Function to refetch auth state

  Returns a UIx component that provides auth state via context.

  Example:
  ```clojure
  (def my-auth-provider
    (create-auth-provider {:use-user-hook my-app.hooks/use-me}))

  ;; In your app root:
  ($ my-auth-provider
     ($ child-components))
  ```"
  [{:keys [use-user-hook]}]
  (defui auth-provider-impl [{:keys [children]}]
    (let [auth-state (use-user-hook)]
      ($ (.-Provider auth-context) {:value auth-state}
         children))))

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
