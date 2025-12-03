(ns bashketball-game-ui.hooks.use-decks
  "React hooks for deck CRUD operations.

  Provides hooks for fetching and mutating deck data via GraphQL.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.graphql.queries :as queries]))

(defn use-my-decks
  "Fetches the current user's decks.

  Returns a map with `:decks`, `:loading`, `:error`, and `:refetch`."
  []
  (let [{:keys [data loading error refetch]} (gql/use-query queries/MY_DECKS_QUERY)]
    {:decks   (some-> data :my-decks)
     :loading loading
     :error   error
     :refetch refetch}))

(defn use-deck
  "Fetches a single deck by ID.

  Returns a map with `:deck`, `:loading`, `:error`, and `:refetch`."
  [deck-id]
  (let [{:keys [data loading error refetch]}
        (gql/use-query queries/DECK_QUERY
                       {:variables {:id deck-id}
                        :skip      (nil? deck-id)})]
    {:deck    (some-> data :deck)
     :loading loading
     :error   error
     :refetch refetch}))

(defn use-create-deck
  "Provides a mutation to create a new deck.

  Returns `[create-fn {:keys [loading error data]}]` where `create-fn`
  takes a deck name string."
  []
  (let [[mutate-fn {:keys [loading error data]}]
        (gql/use-mutation mutations/CREATE_DECK_MUTATION
                          {:refetch-queries ["MyDecks"]})]
    [(fn [name]
       (mutate-fn {:variables {:name name}}))
     {:loading loading
      :error   error
      :data    (some-> data :create-deck)}]))

(defn use-update-deck
  "Provides a mutation to update an existing deck.

  Returns `[update-fn {:keys [loading error data]}]` where `update-fn`
  takes `{:keys [id name card-slugs]}`."
  []
  (let [[mutate-fn {:keys [loading error data]}]
        (gql/use-mutation mutations/UPDATE_DECK_MUTATION)]
    [(fn [{:keys [id name card-slugs]}]
       (mutate-fn {:variables {:id         id
                               :name       name
                               :card-slugs card-slugs}}))
     {:loading loading
      :error   error
      :data    (some-> data :update-deck)}]))

(defn use-delete-deck
  "Provides a mutation to delete a deck.

  Returns `[delete-fn {:keys [loading error]}]` where `delete-fn`
  takes a deck ID string."
  []
  (let [[mutate-fn {:keys [loading error]}]
        (gql/use-mutation mutations/DELETE_DECK_MUTATION
                          {:refetch-queries ["MyDecks"]})]
    [(fn [id]
       (mutate-fn {:variables {:id id}}))
     {:loading loading
      :error   error}]))

(defn use-validate-deck
  "Provides a mutation to validate a deck server-side.

  Returns `[validate-fn {:keys [loading error data]}]` where `validate-fn`
  takes a deck ID string."
  []
  (let [[mutate-fn {:keys [loading error data]}]
        (gql/use-mutation mutations/VALIDATE_DECK_MUTATION)]
    [(fn [id]
       (mutate-fn {:variables {:id id}}))
     {:loading loading
      :error   error
      :data    (some-> data :validate-deck)}]))
