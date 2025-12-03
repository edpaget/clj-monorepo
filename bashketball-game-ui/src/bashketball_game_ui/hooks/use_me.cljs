(ns bashketball-game-ui.hooks.use-me
  "Hook for fetching the current authenticated user.

  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]))

(defn use-me
  "Fetches the current user via the me query.

  Returns a map with:
  - `:user` - The decoded user map if logged in, nil otherwise
  - `:loading?` - Whether the query is in flight
  - `:error` - Any error from the query
  - `:logged-in?` - Whether the user is logged in
  - `:refetch` - Function to refetch the query"
  []
  (let [{:keys [data loading error refetch]}
        (gql/use-query queries/ME_QUERY {:fetch-policy "network-only"})
        user                                                            (some-> data :me)]
    {:user       user
     :loading?   loading
     :error      error
     :logged-in? (boolean user)
     :refetch    refetch}))
