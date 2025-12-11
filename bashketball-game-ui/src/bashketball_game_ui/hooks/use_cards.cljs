(ns bashketball-game-ui.hooks.use-cards
  "React hooks for card catalog queries.

  Provides hooks for fetching cards and sets from the card catalog.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]))

(defn use-cards
  "Fetches cards from the catalog with optional filters.

  Takes an optional map with `:set-slug` and `:card-type` to filter cards.
  For backward compatibility, also accepts a single string argument as set-slug.
  Returns a map with `:cards`, `:loading`, `:error`, and `:refetch`."
  ([]
   (use-cards {}))
  ([filters]
   (let [filters                                (if (string? filters) {:set-slug filters} filters)
         variables                              (cond-> {}
                                                  (:set-slug filters) (assoc :set-slug (:set-slug filters))
                                                  (:card-type filters) (assoc :card-type (:card-type filters)))
         {:keys [data loading error refetch]}
         (gql/use-query queries/CARDS_QUERY
                        {:variables variables})]
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
