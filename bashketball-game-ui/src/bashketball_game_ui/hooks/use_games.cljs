(ns bashketball-game-ui.hooks.use-games
  "React hooks for game data operations.

  Provides hooks for fetching game data via GraphQL."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.schemas.game :as game-schema]))

(defn use-my-games
  "Fetches the current user's games with optional status filter.

  Returns a map with `:games`, `:loading`, `:error`, and `:refetch`.
  Status should be a string like \"ACTIVE\", \"WAITING\", \"COMPLETED\"."
  ([] (use-my-games nil))
  ([status]
   (let [result (useQuery queries/MY_GAMES_QUERY
                          #js {:variables (when status
                                            #js {:status status})})]
     {:games   (some->> result .-data .-myGames
                        (decoder/decode-seq game-schema/GameSummary))
      :loading (.-loading result)
      :error   (.-error result)
      :refetch (.-refetch result)})))

(defn use-available-games
  "Fetches games waiting for an opponent.

  Returns a map with `:games`, `:loading`, `:error`, and `:refetch`.
  Polls every 10 seconds for new games."
  []
  (let [result (useQuery queries/AVAILABLE_GAMES_QUERY
                         #js {:pollInterval 10000})]
    {:games   (some->> result .-data .-availableGames
                       (decoder/decode-seq game-schema/GameSummary))
     :loading (.-loading result)
     :error   (.-error result)
     :refetch (.-refetch result)}))

(defn use-game
  "Fetches a single game by ID.

  Returns a map with `:game`, `:loading`, `:error`, and `:refetch`."
  [game-id]
  (let [result (useQuery queries/GAME_QUERY
                         #js {:variables #js {:id game-id}
                              :skip (nil? game-id)})]
    {:game    (some->> result .-data .-game
                       (decoder/decode game-schema/Game))
     :loading (.-loading result)
     :error   (.-error result)
     :refetch (.-refetch result)}))
