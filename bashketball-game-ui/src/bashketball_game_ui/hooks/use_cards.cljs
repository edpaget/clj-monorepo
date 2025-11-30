(ns bashketball-game-ui.hooks.use-cards
  "React hooks for card catalog queries.

  Provides hooks for fetching cards and sets from the card catalog."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-schemas.card :as card-schema]))

(defn use-cards
  "Fetches cards from the catalog with optional set filter.

  Takes an optional `set-slug` to filter cards by set.
  Returns a map with `:cards`, `:loading`, `:error`, and `:refetch`."
  ([]
   (use-cards nil))
  ([set-slug]
   (let [result (useQuery queries/CARDS_QUERY
                          #js {:variables (when set-slug
                                            #js {:setSlug set-slug})})]
     {:cards   (some->> result .-data .-cards (decoder/decode-seq card-schema/Card))
      :loading (.-loading result)
      :error   (.-error result)
      :refetch (.-refetch result)})))

(defn use-sets
  "Fetches all available card sets.

  Returns a map with `:sets`, `:loading`, and `:error`."
  []
  (let [result (useQuery queries/SETS_QUERY)]
    {:sets    (some->> result .-data .-sets (decoder/decode-seq card-schema/CardSet))
     :loading (.-loading result)
     :error   (.-error result)}))

(defn use-card
  "Fetches a single card by slug.

  Returns a map with `:card`, `:loading`, and `:error`."
  [slug]
  (let [result (useQuery queries/CARD_QUERY
                         #js {:variables #js {:slug slug}
                              :skip (nil? slug)})]
    {:card    (some->> result .-data .-card (decoder/decode card-schema/Card))
     :loading (.-loading result)
     :error   (.-error result)}))
