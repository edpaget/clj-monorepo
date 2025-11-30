(ns bashketball-game-ui.hooks.use-me
  "Hook for fetching the current authenticated user."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-game-ui.graphql.queries :as queries]))

(defn use-me
  "Fetches the current user via the me query.

  Returns a map with:
  - `:user` - The user data if logged in, nil otherwise
  - `:loading?` - Whether the query is in flight
  - `:error` - Any error from the query
  - `:logged-in?` - Whether the user is logged in
  - `:refetch` - Function to refetch the query"
  []
  (let [result  (useQuery queries/ME_QUERY #js {:fetchPolicy "network-only"})
        loading (.-loading result)
        error   (.-error result)
        data    (.-data result)
        user    (when data (.-me data))
        refetch (.-refetch result)]
    {:user (when user
             {:id (:id user)
              :email (:email user)
              :avatar-url (:avatarUrl user)
              :name (:name user)})
     :loading? loading
     :error error
     :logged-in? (boolean user)
     :refetch refetch}))
