(ns bashketball-editor-ui.graphql.client
  "Apollo GraphQL client setup.

  Provides the configured Apollo client instance for making GraphQL
  queries and mutations to the bashketball-editor-api."
  (:require
   [bashketball-editor-ui.config :as config]
   [graphql-client.client :as gql-client]))

(def apollo-provider gql-client/ApolloProvider)

(def http-link
  "HTTP link configured to connect to the API endpoint with credentials."
  (gql-client/create-http-link {:uri config/api-url}))

(def cache
  "Apollo in-memory cache for query results."
  (gql-client/create-cache))

(def client
  "Configured Apollo client instance.

  Uses credentials: include for session cookie authentication."
  (gql-client/create-client {:link http-link :cache cache}))
