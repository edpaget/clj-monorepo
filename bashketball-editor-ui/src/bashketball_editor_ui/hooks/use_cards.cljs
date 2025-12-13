(ns bashketball-editor-ui.hooks.use-cards
  "Hook for fetching lists of cards."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-editor-ui.graphql.queries :as queries]
   [camel-snake-kebab.core :as csk]))

(defn use-cards
  "Fetches a list of cards optionally filtered by set and/or card type.

  Returns a map with:
  - `:cards` - The user data if logged in, nil otherwise
  - `:loading?` - Whether the query is in flight
  - `:error` - Any error from the query
  - `:refetch` - Function to refetch the query"
  [{:keys [set-slug card-type]}]
  (let [variables                            (cond-> {}
                                               set-slug (assoc :setSlug set-slug)
                                               card-type (assoc :cardType card-type))
        {:keys [loading error data refetch]} (useQuery queries/CARDS_QUERY
                                                       (clj->js {:fetchPolicy "network-only"
                                                                 :variables variables}))
        cards                                (when data (:data (:cards data)))]
    {:cards (when (seq cards)
              (mapv (fn [card]
                      (cond-> {:slug (:slug card)
                               :name (:name card)
                               :card-type (->> (:__typename card)
                                               csk/->SCREAMING_SNAKE_CASE
                                               (keyword "card-type"))
                               :set-slug (:setSlug card)
                               :updated-at (:updatedAt card)}
                        (:fate card) (assoc :fate (:fate card))))
                    cards))
     :loading? loading
     :error error
     :refetch refetch}))
