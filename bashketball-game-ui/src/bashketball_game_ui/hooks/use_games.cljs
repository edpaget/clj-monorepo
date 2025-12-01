(ns bashketball-game-ui.hooks.use-games
  "React hooks for game data operations.

  Provides hooks for fetching game data via GraphQL."
  (:require
   ["@apollo/client" :refer [useQuery]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.schemas.game :as game-schema]
   [bashketball-ui.core]))

(defn use-my-games
  "Fetches the current user's games with optional status filter and pagination.

  Returns a map with:
  - `:games` - vector of game summaries
  - `:page-info` - {:total-count :has-next-page :has-previous-page}
  - `:loading` - boolean
  - `:error` - error object if any
  - `:refetch` - function to refetch

  Options:
  - `status` - filter by game status (\"WAITING\", \"ACTIVE\", \"COMPLETED\", \"ABANDONED\")
  - `limit` - number of results (default 20, max 100)
  - `offset` - pagination offset (default 0)"
  ([] (use-my-games {}))
  ([opts]
   (let [{:keys [status limit offset]} (if (string? opts)
                                         {:status opts}
                                         opts)
         variables                     (cond-> #js {}
                                         status (doto (aset "status" status))
                                         limit  (doto (aset "limit" limit))
                                         offset (doto (aset "offset" offset)))
         result                        (useQuery queries/MY_GAMES_QUERY
                                                 #js {:variables variables})]
     {:games     (some->> result :data :myGames :data
                          (decoder/decode-seq game-schema/GameSummary))
      :page-info (when-let [pi (some-> result :data :myGames :pageInfo)]
                   {:total-count       (:totalCount pi)
                    :has-next-page     (:hasNextPage pi)
                    :has-previous-page (:hasPreviousPage pi)})
      :loading   (:loading result)
      :error     (:error result)
      :refetch   (:refetch result)})))

(defn use-available-games
  "Fetches games waiting for an opponent.

  Returns a map with `:games`, `:loading`, `:error`, and `:refetch`.
  Polls every 10 seconds for new games."
  []
  (let [result (useQuery queries/AVAILABLE_GAMES_QUERY
                         #js {:pollInterval 10000})]
    {:games   (some->> result :data :availableGames
                       (decoder/decode-seq game-schema/GameSummary))
     :loading (:loading result)
     :error   (:error result)
     :refetch (:refetch result)}))

(defn use-game
  "Fetches a single game by ID.

  Returns a map with `:game`, `:loading`, `:error`, and `:refetch`."
  [game-id]
  (let [result (useQuery queries/GAME_QUERY
                         #js {:variables #js {:id game-id}
                              :skip (nil? game-id)})]
    {:game    (some->> result :data :game
                       (decoder/decode game-schema/Game))
     :loading (:loading result)
     :error   (:error result)
     :refetch (:refetch result)}))
