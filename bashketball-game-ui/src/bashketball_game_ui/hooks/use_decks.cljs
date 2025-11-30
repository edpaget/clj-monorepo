(ns bashketball-game-ui.hooks.use-decks
  "React hooks for deck CRUD operations.

  Provides hooks for fetching and mutating deck data via GraphQL."
  (:require
   ["@apollo/client" :refer [useQuery useMutation]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.schemas.deck :as deck-schema]))

(defn use-my-decks
  "Fetches the current user's decks.

  Returns a map with `:decks`, `:loading`, `:error`, and `:refetch`."
  []
  (let [result (useQuery queries/MY_DECKS_QUERY)]
    {:decks   (some->> result .-data .-myDecks (decoder/decode-seq deck-schema/Deck))
     :loading (.-loading result)
     :error   (.-error result)
     :refetch (.-refetch result)}))

(defn use-deck
  "Fetches a single deck by ID.

  Returns a map with `:deck`, `:loading`, `:error`, and `:refetch`."
  [deck-id]
  (let [result (useQuery queries/DECK_QUERY
                         #js {:variables #js {:id deck-id}
                              :skip (nil? deck-id)})]
    {:deck    (some->> result .-data .-deck (decoder/decode deck-schema/Deck))
     :loading (.-loading result)
     :error   (.-error result)
     :refetch (.-refetch result)}))

(defn use-create-deck
  "Provides a mutation to create a new deck.

  Returns `[create-fn {:keys [loading error data]}]` where `create-fn`
  takes a deck name string."
  []
  (let [[create-fn ^js result] (useMutation
                                mutations/CREATE_DECK_MUTATION
                                #js {:refetchQueries #js ["MyDecks"]})]
    [(fn [name]
       (create-fn #js {:variables #js {:name name}}))
     {:loading (.-loading result)
      :error   (.-error result)
      :data    (some->> ^js result .-data .-createDeck (decoder/decode deck-schema/Deck))}]))

(defn use-update-deck
  "Provides a mutation to update an existing deck.

  Returns `[update-fn {:keys [loading error data]}]` where `update-fn`
  takes `{:keys [id name card-slugs]}`."
  []
  (let [[update-fn ^js result] (useMutation mutations/UPDATE_DECK_MUTATION)]
    [(fn [{:keys [id name card-slugs]}]
       (update-fn #js {:variables #js {:id id
                                       :name name
                                       :cardSlugs (when card-slugs
                                                    (clj->js card-slugs))}}))
     {:loading (.-loading result)
      :error   (.-error result)
      :data    (some->> ^js result .-data .-updateDeck (decoder/decode deck-schema/Deck))}]))

(defn use-delete-deck
  "Provides a mutation to delete a deck.

  Returns `[delete-fn {:keys [loading error]}]` where `delete-fn`
  takes a deck ID string."
  []
  (let [[delete-fn ^js result] (useMutation
                                mutations/DELETE_DECK_MUTATION
                                #js {:refetchQueries #js ["MyDecks"]})]
    [(fn [id]
       (delete-fn #js {:variables #js {:id id}}))
     {:loading (.-loading result)
      :error   (.-error result)}]))

(defn use-validate-deck
  "Provides a mutation to validate a deck server-side.

  Returns `[validate-fn {:keys [loading error data]}]` where `validate-fn`
  takes a deck ID string."
  []
  (let [[validate-fn ^js result] (useMutation mutations/VALIDATE_DECK_MUTATION)]
    [(fn [id]
       (validate-fn #js {:variables #js {:id id}}))
     {:loading (.-loading result)
      :error   (.-error result)
      :data    (some->> ^js result .-data .-validateDeck (decoder/decode deck-schema/Deck))}]))
