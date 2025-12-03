(ns bashketball-game-ui.hooks.use-games
  "React hooks for game data operations.

  Provides hooks for fetching game data via GraphQL, including real-time
  updates via SSE subscriptions. Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.graphql.subscriptions :as subscriptions]
   [bashketball-ui.core]
   [uix.core :as uix]))

(defn use-my-games
  "Fetches the current user's games with optional status filter and pagination.

  Returns a map with:
  - `:games` - vector of game summaries (automatically decoded)
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
   (let [{:keys [status limit offset]}        (if (string? opts)
                                                {:status opts}
                                                opts)
         variables                            (cond-> {}
                                                status (assoc :status status)
                                                limit  (assoc :limit limit)
                                                offset (assoc :offset offset))
         {:keys [data loading error refetch]} (gql/use-query
                                               queries/MY_GAMES_QUERY
                                               {:variables variables})]
     {:games     (some-> data :my-games :data)
      :page-info (some-> data :my-games :page-info)
      :loading   loading
      :error     error
      :refetch   refetch})))

(defn use-available-games
  "Fetches games waiting for an opponent.

  Returns a map with `:games`, `:loading`, `:error`, and `:refetch`.
  Use with [[use-lobby-subscription]] for real-time updates, or use
  [[use-available-games-live]] which combines both."
  []
  (let [{:keys [data loading error refetch]} (gql/use-query queries/AVAILABLE_GAMES_QUERY)]
    {:games   (some-> data :available-games)
     :loading loading
     :error   error
     :refetch refetch}))

(defn use-game
  "Fetches a single game by ID.

  Returns a map with `:game`, `:loading`, `:error`, and `:refetch`."
  [game-id]
  (let [{:keys [data loading error refetch]}
        (gql/use-query queries/GAME_QUERY
                       {:variables {:id game-id}
                        :skip      (nil? game-id)})]
    {:game    (some-> data :game)
     :loading loading
     :error   error
     :refetch refetch}))

(defn use-lobby-subscription
  "Subscribes to real-time lobby updates via SSE.

  Returns a map with:
  - `:connected` - true when subscription is established
  - `:event` - the last event received (type, game-id, user-id)
  - `:loading` - true while connecting
  - `:error` - error object if subscription failed"
  []
  (let [{:keys [data loading error]} (gql/use-subscription
                                      subscriptions/LOBBY_UPDATED_SUBSCRIPTION)]
    {:connected (= "connected" (some-> data :lobby-updated :type))
     :event     (some-> data :lobby-updated)
     :loading   loading
     :error     error}))

(defn use-available-games-live
  "Fetches available games with real-time SSE updates.

  Combines [[use-available-games]] and [[use-lobby-subscription]] to provide
  automatic refetching when lobby events occur (game-created, game-started,
  game-cancelled).

  Returns a map with:
  - `:games` - vector of available games
  - `:loading` - true while fetching
  - `:error` - error object if query failed
  - `:refetch` - function to manually refetch
  - `:subscription` - subscription state map with `:connected`, `:event`, `:error`"
  []
  (let [{:keys [games loading error refetch]} (use-available-games)
        {:keys [event] :as subscription}      (use-lobby-subscription)
        event-type                            (:type event)]
    (uix/use-effect
     (fn []
       (when (contains? #{"GAME_CREATED" "GAME_STARTED" "GAME_CANCELLED"} event-type)
         (refetch))
       js/undefined)
     [event-type refetch])
    {:games        games
     :loading      loading
     :error        error
     :refetch      refetch
     :subscription subscription}))
