(ns basketball-editor-ui.graphql.client
  "Apollo GraphQL client setup.

  Provides the configured Apollo client instance for making GraphQL
  queries and mutations to the bashketball-editor-api."
  (:require
   ["@apollo/client" :as apollo]
   [basketball-editor-ui.config :as config]))

(def http-link
  "HTTP link configured to connect to the API endpoint with credentials."
  (apollo/createHttpLink
   #js {:uri config/api-url
        :credentials "include"}))

(def cache
  "Apollo in-memory cache for query results."
  (apollo/InMemoryCache.))

(def client
  "Configured Apollo client instance.

  Uses credentials: include for session cookie authentication."
  (apollo/ApolloClient.
   #js {:link http-link
        :cache cache}))
