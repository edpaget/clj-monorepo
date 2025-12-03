(ns bashketball-game-ui.hooks.use-cards
  "React hooks for card catalog queries.

  Provides hooks for fetching cards and sets from the card catalog.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]))

(defn use-cards
  "Fetches cards from the catalog with optional set filter.

  Takes an optional `set-slug` to filter cards by set.
  Returns a map with `:cards`, `:loading`, `:error`, and `:refetch`."
  ([]
   (use-cards nil))
  ([set-slug]
   (let [{:keys [data loading error refetch]}
         (gql/use-query queries/CARDS_QUERY
                        {:variables (when set-slug {:set-slug set-slug})})]
     {:cards   (some-> data :cards)
      :loading loading
      :error   error
      :refetch refetch})))

(defn use-sets
  "Fetches all available card sets.

  Returns a map with `:sets`, `:loading`, and `:error`."
  []
  (let [{:keys [data loading error]} (gql/use-query queries/SETS_QUERY)]
    {:sets    (some-> data :sets)
     :loading loading
     :error   error}))

(defn use-card
  "Fetches a single card by slug.

  Returns a map with `:card`, `:loading`, and `:error`."
  [slug]
  (let [{:keys [data loading error]}
        (gql/use-query queries/CARD_QUERY
                       {:variables {:slug slug}
                        :skip      (nil? slug)})]
    {:card    (some-> data :card)
     :loading loading
     :error   error}))
