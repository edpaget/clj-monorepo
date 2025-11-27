(ns bashketball-editor-ui.hooks.use-cards
  "Hook for fetching lists of cards."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-editor-ui.graphql.queries :as queries]
   [camel-snake-kebab.core :as csk]))

(defn use-cards
  "Fetches a list of cards optional filtered by set

  Returns a map with:
  - `:cards` - The user data if logged in, nil otherwise
  - `:loading?` - Whether the query is in flight
  - `:error` - Any error from the query
  - `:refetch` - Function to refetch the query"
  [{:keys [set-slug]}]
  (let [{:keys [loading error data refetch]} (useQuery queries/CARDS_QUERY
                                                       #js {:fetchPolicy "network-only"
                                                            :variables #js {:setSlug set-slug}})
        cards (when data (:data (:cards data)))]
    {:cards (when (seq cards)
              (mapv (fn [card]
                      {:slug (:slug card)
                       :name (:name card)
                       :card-type (->> (:__typename card)
                                       csk/->SCREAMING_SNAKE_CASE
                                       (keyword "card-type"))
                       :set-slug (:setSlug card)
                       :updated-at (:updatedAt card)})
                    cards))
     :loading? loading
     :error error
     :refetch refetch}))
