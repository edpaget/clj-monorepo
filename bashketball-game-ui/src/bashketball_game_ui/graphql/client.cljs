(ns bashketball-game-ui.graphql.client
  "Apollo GraphQL client setup.

  Provides the configured Apollo client instance for making GraphQL
  queries and mutations to the bashketball-game-api."
  (:require
   ["@apollo/client" :as apollo]
   [bashketball-game-ui.config :as config]))

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

(def client
  "Configured Apollo client instance.

  Uses credentials: include for session cookie authentication."
  (apollo/ApolloClient.
   #js {:link http-link
        :cache cache}))
