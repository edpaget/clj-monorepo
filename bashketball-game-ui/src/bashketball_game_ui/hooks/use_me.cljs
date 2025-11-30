(ns bashketball-game-ui.hooks.use-me
  "Hook for fetching the current authenticated user."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-schemas.user :as user-schema]))

(defn use-me
  "Fetches the current user via the me query.

  Returns a map with:
  - `:user` - The decoded user map if logged in, nil otherwise
  - `:loading?` - Whether the query is in flight
  - `:error` - Any error from the query
  - `:logged-in?` - Whether the user is logged in
  - `:refetch` - Function to refetch the query"
  []
  (let [result  (useQuery queries/ME_QUERY #js {:fetchPolicy "network-only"})
        user    (some->> result .-data .-me (decoder/decode user-schema/User))]
    {:user       user
     :loading?   (.-loading result)
     :error      (.-error result)
     :logged-in? (boolean user)
     :refetch    (.-refetch result)}))
