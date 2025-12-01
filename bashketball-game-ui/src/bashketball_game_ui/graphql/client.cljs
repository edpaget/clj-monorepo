(ns bashketball-game-ui.graphql.client
  "Apollo GraphQL client setup.

  Provides the configured Apollo client instance for making GraphQL
  queries, mutations, and subscriptions to the bashketball-game-api.
  Subscriptions use SSE (Server-Sent Events) via a custom link."
  (:require
   ["@apollo/client" :as apollo]
   [bashketball-game-ui.config :as config]
   [bashketball-game-ui.graphql.sse-link :as sse]))

(def apollo-provider apollo/ApolloProvider)

(def http-link
  "HTTP link configured to connect to the API endpoint with credentials."
  (apollo/createHttpLink
   #js {:uri config/api-url
        :credentials "include"}))

(def cache
  "Apollo in-memory cache for query results.

  Includes `possibleTypes` for union types so Apollo can correctly match
  inline fragments to concrete types."
  (apollo/InMemoryCache.
   #js {:possibleTypes
        #js {:GameCard #js ["PlayerCard"
                            "AbilityCard"
                            "PlayCard"
                            "StandardActionCard"
                            "SplitPlayCard"
                            "CoachingCard"
                            "TeamAssetCard"]}}))

(def sse-link
  "SSE link for handling subscriptions via Server-Sent Events."
  (sse/create-sse-link))

(defn- is-subscription-operation?
  "Returns true if the operation is a subscription."
  [op]
  (let [definition (-> op :query :definitions (aget 0))]
    (and (= "OperationDefinition" (:kind definition))
         (= "subscription" (:operation definition)))))

(def split-link
  "Split link that routes subscriptions to SSE and other operations to HTTP."
  (apollo/split
   is-subscription-operation?
   sse-link
   http-link))

(def client
  "Configured Apollo client instance.

  Uses credentials: include for session cookie authentication.
  Subscriptions are handled via SSE, queries/mutations via HTTP."
  (apollo/ApolloClient.
   #js {:link split-link
        :cache cache}))
