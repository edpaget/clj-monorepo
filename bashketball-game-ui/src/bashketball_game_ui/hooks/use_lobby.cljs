(ns bashketball-game-ui.hooks.use-lobby
  "React hooks for game lobby and matchmaking operations.

  Provides hooks for creating, joining, and leaving games via GraphQL mutations.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-ui.core]))

(defn use-create-game
  "Provides a mutation to create a new game.

  Returns `[create-fn {:keys [loading error data]}]` where `create-fn`
  takes a deck-id string."
  []
  (let [[mutate-fn {:keys [loading error data]}]
        (gql/use-mutation mutations/CREATE_GAME_MUTATION
                          {:refetch-queries ["MyGames" "AvailableGames"]})]
    [(fn [deck-id]
       (mutate-fn {:variables {:deck-id deck-id}}))
     {:loading loading
      :error   error
      :data    (some-> data :create-game)}]))

(defn use-join-game
  "Provides a mutation to join an existing game.

  Returns `[join-fn {:keys [loading error data]}]` where `join-fn`
  takes `{:keys [game-id deck-id]}`."
  []
  (let [[mutate-fn {:keys [loading error data]}]
        (gql/use-mutation mutations/JOIN_GAME_MUTATION
                          {:refetch-queries ["MyGames" "AvailableGames"]})]
    [(fn [{:keys [game-id deck-id]}]
       (mutate-fn {:variables {:game-id game-id :deck-id deck-id}}))
     {:loading loading
      :error   error
      :data    (some-> data :join-game)}]))

(defn use-leave-game
  "Provides a mutation to leave a waiting game.

  Returns `[leave-fn {:keys [loading error]}]` where `leave-fn`
  takes a game-id string."
  []
  (let [[mutate-fn {:keys [loading error]}]
        (gql/use-mutation mutations/LEAVE_GAME_MUTATION
                          {:refetch-queries ["MyGames" "AvailableGames"]})]
    [(fn [game-id]
       (mutate-fn {:variables {:game-id game-id}}))
     {:loading loading
      :error   error}]))

(defn use-forfeit-game
  "Provides a mutation to forfeit an active game.

  Returns `[forfeit-fn {:keys [loading error data]}]` where `forfeit-fn`
  takes a game-id string."
  []
  (let [[mutate-fn {:keys [loading error data]}]
        (gql/use-mutation mutations/FORFEIT_GAME_MUTATION
                          {:refetch-queries ["MyGames"]})]
    [(fn [game-id]
       (mutate-fn {:variables {:game-id game-id}}))
     {:loading loading
      :error   error
      :data    (some-> data :forfeit-game)}]))
