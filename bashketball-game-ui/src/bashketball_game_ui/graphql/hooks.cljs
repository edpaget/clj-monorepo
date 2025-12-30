(ns bashketball-game-ui.graphql.hooks
  "React hooks wrapping Apollo Client with automatic response decoding.

  Provides [[use-query]], [[use-mutation]], and [[use-subscription]] hooks
  that automatically decode GraphQL responses to Clojure data using
  __typename-based schema dispatch.

  These hooks handle:
  - Converting kebab-case variable keys to camelCase for the request
  - Decoding responses using [[decoder/decode-response]]
  - Returning consistent result maps with :data, :loading, :error, etc."
  (:require
   [bashketball-game-ui.graphql.decoder :as decoder]
   [graphql-client.hooks :as gql-hooks]))

(defn use-query
  "Executes a GraphQL query with automatic response decoding.

  Returns a map with:
  - `:data` - Decoded response data (kebab-case keys, namespaced enum keywords)
  - `:loading` - Boolean indicating if query is in flight
  - `:error` - Error object if query failed
  - `:refetch` - Function to refetch the query

  Options are the same as Apollo's useQuery, with automatic conversion of
  kebab-case variable keys to camelCase.

  Example:
    (let [{:keys [data loading error]} (use-query GAME_QUERY {:variables {:id game-id}})]
      (when-not loading
        (:game data)))"
  ([query]
   (use-query query nil))
  ([query options]
   (gql-hooks/use-query query (assoc options :decoder decoder/decode-js-response))))

(defn use-lazy-query
  "Returns a query function and result for on-demand execution.

  Unlike [[use-query]], the query is not executed until the returned
  function is called. Useful for queries triggered by user actions.

  Returns [execute-fn result-map] where:
  - `execute-fn` - Function to execute the query, accepts options
  - `result-map` - Map with :data, :loading, :error, :called

  Example:
    (let [[fetch-game {:keys [data loading]}] (use-lazy-query GAME_QUERY)]
      ($ :button {:on-click #(fetch-game {:variables {:id \"abc\"}})}
         \"Load Game\"))"
  ([query]
   (use-lazy-query query nil))
  ([query options]
   (gql-hooks/use-lazy-query query (assoc options :decoder decoder/decode-js-response))))

(defn use-mutation
  "Returns [mutate-fn result] with automatic response decoding.

  The mutate-fn accepts options and returns a promise resolving to
  the decoded response. The result map contains the current state.

  Returns [mutate-fn result-map] where:
  - `mutate-fn` - Function to execute the mutation, returns Promise
  - `result-map` - Map with :data, :loading, :error

  Example:
    (let [[create-game {:keys [loading]}] (use-mutation CREATE_GAME_MUTATION)]
      ($ :button {:on-click #(create-game {:variables {:deck-id deck-id}})
                  :disabled loading}
         \"Create Game\"))"
  ([mutation]
   (use-mutation mutation nil))
  ([mutation options]
   (gql-hooks/use-mutation mutation (assoc options :decoder decoder/decode-js-response))))

(defn use-subscription
  "Subscribes to a GraphQL subscription with automatic response decoding.

  Returns a map with:
  - `:data` - Decoded subscription data
  - `:loading` - Boolean indicating if subscription is connecting
  - `:error` - Error object if subscription failed

  Example:
    (let [{:keys [data]} (use-subscription GAME_UPDATED_SUBSCRIPTION
                                           {:variables {:game-id id}})]
      (when data
        (handle-game-event (:game-updated data))))"
  ([subscription]
   (use-subscription subscription nil))
  ([subscription options]
   (gql-hooks/use-subscription subscription (assoc options :decoder decoder/decode-js-response))))
