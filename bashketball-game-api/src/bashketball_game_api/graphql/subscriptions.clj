(ns bashketball-game-api.graphql.subscriptions
  "GraphQL subscription streamers for real-time game updates.

  Provides subscription streamers for game state changes and lobby events.
  Uses graphql-server's subscription infrastructure with SSE transport."
  (:require
   [clojure.core.async :as async]
   [graphql-server.core :refer [defstreamer def-resolver-map]]
   [graphql-server.subscriptions :as subs]))

(def SubscriptionEventType
  "GraphQL enum for subscription event types."
  [:enum {:graphql/type :SubscriptionEventType}
   :connected
   :state-changed
   :player-joined
   :game-ended
   :game-created
   :game-started
   :game-cancelled])

(def GameEvent
  "GraphQL schema for game subscription events.

  Sent when game state changes occur (player joins, state changes, game ends)."
  [:map {:graphql/type :GameEvent}
   [:type SubscriptionEventType]
   [:game-id {:optional true} [:maybe :string]]
   [:player-id {:optional true} [:maybe :string]]
   [:winner-id {:optional true} [:maybe :string]]
   [:reason {:optional true} [:maybe :string]]
   [:user-id {:optional true} [:maybe :string]]])

(def LobbyEvent
  "GraphQL schema for lobby subscription events.

  Sent when lobby state changes (new game created, game started, game cancelled)."
  [:map {:graphql/type :LobbyEvent}
   [:type SubscriptionEventType]
   [:game-id {:optional true} [:maybe :string]]
   [:user-id {:optional true} [:maybe :string]]])

(defn- wrap-channel-with-transform
  "Wraps a channel to transform raw {:type :data} events into flat events."
  [raw-ch]
  (let [out-ch (async/chan 10)]
    (async/go-loop []
      (if-let [msg (async/<! raw-ch)]
        (do
          (async/>! out-ch (merge {:type (:type msg)} (:data msg)))
          (recur))
        (async/close! out-ch)))
    out-ch))

(defstreamer :Subscription :gameUpdated
  "Subscribe to game state updates.

  Streams events when game state changes, players join, or game ends.
  Requires a game-id argument to specify which game to subscribe to."
  [:=> [:cat :any [:map [:game-id :uuid]] :any] GameEvent]
  [ctx {:keys [game-id]}]
  (let [sub-mgr (:subscription-manager ctx)
        user-id (get-in ctx [:request :authn/user-id])
        raw-ch  (subs/subscribe! sub-mgr [:game game-id])
        out-ch  (wrap-channel-with-transform raw-ch)]
    (async/put! out-ch {:type    :connected
                        :game-id (str game-id)
                        :user-id user-id})
    out-ch))

(defstreamer :Subscription :lobbyUpdated
  "Subscribe to lobby updates.

  Streams events when games are created, started, or cancelled in the lobby."
  [:=> [:cat :any :any :any] LobbyEvent]
  [ctx _args]
  (let [sub-mgr (:subscription-manager ctx)
        user-id (get-in ctx [:request :authn/user-id])
        raw-ch  (subs/subscribe! sub-mgr [:lobby])
        out-ch  (wrap-channel-with-transform raw-ch)]
    (async/put! out-ch {:type    :connected
                        :user-id user-id})
    out-ch))

(def-resolver-map)
