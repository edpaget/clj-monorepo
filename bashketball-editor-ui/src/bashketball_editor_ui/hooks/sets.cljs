(ns bashketball-editor-ui.hooks.sets
  "Hook for fetching lists of cards."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-editor-ui.graphql.queries :as queries]))

(defn use-sets
  "Fetches a list of sets.

  Returns a map with:
  - `:sets` - Vector of set maps with :slug, :name, :created-at, :updated-at
  - `:loading?` - Whether the query is in flight
  - `:error` - Any error from the query
  - `:refetch` - Function to refetch the query"
  []
  (let [{:keys [loading error data refetch]} (useQuery queries/CARD_SETS_QUERY
                                                       #js {:fetchPolicy "network-only"})
        sets                                 (when data (:data (:cardSets data)))]
    {:sets (when (seq sets)
             (mapv (fn [s]
                     {:slug (:slug s)
                      :name (:name s)
                      :created-at (:createdAt s)
                      :updated-at (:updatedAt s)})
                   sets))
     :loading? loading
     :error error
     :refetch refetch}))
