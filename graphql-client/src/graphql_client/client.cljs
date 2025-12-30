(ns graphql-client.client
  "Apollo GraphQL client setup utilities.

  Provides functions for creating and configuring Apollo Client instances
  with common options like HTTP links, caching, and credentials."
  (:require
   ["@apollo/client" :as apollo]))

(def ApolloProvider
  "Apollo Provider component for React context.

  Wrap your app with this provider to make the client available to hooks.

  Example:
    ($ ApolloProvider {:client client}
       ($ App))"
  apollo/ApolloProvider)

(def gql
  "GraphQL template tag for defining queries.

  Example:
    (def ME_QUERY (gql \"query Me { me { id name } }\"))"
  apollo/gql)

(defn create-http-link
  "Creates an HTTP link for Apollo Client.

  Options:
  - `:uri` - GraphQL endpoint URL (required)
  - `:credentials` - Credentials mode, e.g., \"include\" for cookies (default: \"include\")

  Example:
    (create-http-link {:uri \"http://localhost:3000/graphql\"})"
  [{:keys [uri credentials] :or {credentials "include"}}]
  (apollo/createHttpLink
   #js {:uri uri
        :credentials credentials}))

(defn create-cache
  "Creates an InMemoryCache for Apollo Client.

  Options:
  - `:possible-types` - Map of interface/union types to concrete types
                        for proper fragment matching

  Example:
    (create-cache {:possible-types {:GameCard [\"PlayerCard\" \"AbilityCard\"]}})"
  ([]
   (create-cache {}))
  ([{:keys [possible-types]}]
   (if possible-types
     (apollo/InMemoryCache.
      #js {:possibleTypes (clj->js possible-types)})
     (apollo/InMemoryCache.))))

(defn create-client
  "Creates an Apollo Client instance.

  Options:
  - `:link` - Apollo link (required, use [[create-http-link]] or custom link)
  - `:cache` - Apollo cache (default: new InMemoryCache)

  Example:
    (def client
      (create-client
        {:link (create-http-link {:uri api-url})
         :cache (create-cache {:possible-types {...}})}))"
  [{:keys [link cache] :or {cache (create-cache)}}]
  (apollo/ApolloClient.
   #js {:link link
        :cache cache}))

(defn split-link
  "Creates a split link that routes operations based on a predicate.

  The predicate receives an operation and returns true to use the first link,
  false to use the second link.

  Example:
    (split-link
      is-subscription?
      sse-link
      http-link)"
  [predicate true-link false-link]
  (apollo/split predicate true-link false-link))

(defn is-subscription-operation?
  "Returns true if the operation is a GraphQL subscription.

  Useful as a predicate for [[split-link]] to route subscriptions
  to a different link (e.g., WebSocket or SSE)."
  [op]
  (let [definition (-> op :query :definitions (aget 0))]
    (and (= "OperationDefinition" (:kind definition))
         (= "subscription" (:operation definition)))))
