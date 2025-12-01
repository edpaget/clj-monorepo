(ns bashketball-game-ui.hooks.use-lobby
  "React hooks for game lobby and matchmaking operations.

  Provides hooks for creating, joining, and leaving games via GraphQL mutations."
  (:require
   ["@apollo/client" :refer [useMutation]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.schemas.game :as game-schema]
   [bashketball-ui.core]))

(defn use-create-game
  "Provides a mutation to create a new game.

  Returns `[create-fn {:keys [loading error data]}]` where `create-fn`
  takes a deck-id string."
  []
  (let [[create-fn result] (useMutation
                            mutations/CREATE_GAME_MUTATION
                            #js {:refetchQueries #js ["MyGames" "AvailableGames"]})]
    [(fn [deck-id]
       (create-fn #js {:variables #js {:deckId deck-id}}))
     {:loading (:loading result)
      :error   (:error result)
      :data    (some->> result :data :createGame
                        (decoder/decode game-schema/GameSummary))}]))

(defn use-join-game
  "Provides a mutation to join an existing game.

  Returns `[join-fn {:keys [loading error data]}]` where `join-fn`
  takes `{:keys [game-id deck-id]}`."
  []
  (let [[join-fn result] (useMutation
                          mutations/JOIN_GAME_MUTATION
                          #js {:refetchQueries #js ["MyGames" "AvailableGames"]})]
    [(fn [{:keys [game-id deck-id]}]
       (join-fn #js {:variables #js {:gameId game-id :deckId deck-id}}))
     {:loading (:loading result)
      :error   (:error result)
      :data    (some->> result :data :joinGame
                        (decoder/decode game-schema/GameSummary))}]))

(defn use-leave-game
  "Provides a mutation to leave a waiting game.

  Returns `[leave-fn {:keys [loading error]}]` where `leave-fn`
  takes a game-id string."
  []
  (let [[leave-fn result] (useMutation
                           mutations/LEAVE_GAME_MUTATION
                           #js {:refetchQueries #js ["MyGames" "AvailableGames"]})]
    [(fn [game-id]
       (leave-fn #js {:variables #js {:gameId game-id}}))
     {:loading (:loading result)
      :error   (:error result)}]))

(defn use-forfeit-game
  "Provides a mutation to forfeit an active game.

  Returns `[forfeit-fn {:keys [loading error data]}]` where `forfeit-fn`
  takes a game-id string."
  []
  (let [[forfeit-fn result] (useMutation
                             mutations/FORFEIT_GAME_MUTATION
                             #js {:refetchQueries #js ["MyGames"]})]
    [(fn [game-id]
       (forfeit-fn #js {:variables #js {:gameId game-id}}))
     {:loading (:loading result)
      :error   (:error result)
      :data    (some->> result :data :forfeitGame
                        (decoder/decode game-schema/Game))}]))
