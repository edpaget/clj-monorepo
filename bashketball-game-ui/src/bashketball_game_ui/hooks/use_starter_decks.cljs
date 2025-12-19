(ns bashketball-game-ui.hooks.use-starter-decks
  "React hooks for starter deck operations.

  Provides hooks for fetching available starter decks and claiming them."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.graphql.queries :as queries]))

(defn use-starter-decks
  "Fetches all starter deck definitions with card slugs.

  Returns a map with `:starter-decks`, `:loading`, and `:error`."
  []
  (let [{:keys [data loading error]}
        (gql/use-query queries/STARTER_DECKS_QUERY)]
    {:starter-decks (some-> data :starter-decks)
     :loading       loading
     :error         error}))

(defn use-available-starter-decks
  "Fetches starter decks the user hasn't claimed yet.

  Returns a map with `:starter-decks`, `:loading`, `:error`, and `:refetch`."
  []
  (let [{:keys [data loading error refetch]}
        (gql/use-query queries/AVAILABLE_STARTER_DECKS_QUERY)]
    {:starter-decks (some-> data :available-starter-decks)
     :loading       loading
     :error         error
     :refetch       refetch}))

(defn use-claimed-starter-decks
  "Fetches starter decks the user has already claimed.

  Returns a map with `:claimed-decks`, `:loading`, `:error`, and `:refetch`."
  []
  (let [{:keys [data loading error refetch]}
        (gql/use-query queries/CLAIMED_STARTER_DECKS_QUERY)]
    {:claimed-decks (some-> data :claimed-starter-decks)
     :loading       loading
     :error         error
     :refetch       refetch}))

(defn use-claim-starter-deck
  "Provides a mutation to claim a starter deck.

  Returns `[claim-fn {:keys [loading error data]}]` where `claim-fn`
  takes a starter deck ID string."
  []
  (let [[mutate-fn {:keys [loading error data]}]
        (gql/use-mutation mutations/CLAIM_STARTER_DECK_MUTATION
                          {:refetch-queries ["MyDecks" "AvailableStarterDecks" "ClaimedStarterDecks"]})]
    [(fn [starter-deck-id]
       (mutate-fn {:variables {:starterDeckId starter-deck-id}}))
     {:loading loading
      :error   error
      :data    (some-> data :claim-starter-deck)}]))
