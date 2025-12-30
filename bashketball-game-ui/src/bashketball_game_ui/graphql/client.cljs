(ns bashketball-game-ui.graphql.client
  "Apollo GraphQL client setup.

  Provides the configured Apollo client instance for making GraphQL
  queries, mutations, and subscriptions to the bashketball-game-api.
  Subscriptions use SSE (Server-Sent Events) via a custom link."
  (:require
   [bashketball-game-ui.config :as config]
   [bashketball-game-ui.graphql.sse-link :as sse]
   [graphql-client.client :as gql-client]))

(def apollo-provider gql-client/ApolloProvider)

(def http-link
  "HTTP link configured to connect to the API endpoint with credentials."
  (gql-client/create-http-link {:uri config/api-url}))

(def cache
  "Apollo in-memory cache for query results.

  Includes `possibleTypes` for union types so Apollo can correctly match
  inline fragments to concrete types."
  (gql-client/create-cache
   {:possible-types
    {:GameCard ["PlayerCard"
                "AbilityCard"
                "PlayCard"
                "StandardActionCard"
                "SplitPlayCard"
                "CoachingCard"
                "TeamAssetCard"]}}))

(def sse-link
  "SSE link for handling subscriptions via Server-Sent Events."
  (sse/create-sse-link))

(def split-link
  "Split link that routes subscriptions to SSE and other operations to HTTP."
  (gql-client/split-link
   gql-client/is-subscription-operation?
   sse-link
   http-link))

(def client
  "Configured Apollo client instance.

  Uses credentials: include for session cookie authentication.
  Subscriptions are handled via SSE, queries/mutations via HTTP."
  (gql-client/create-client {:link split-link :cache cache}))
