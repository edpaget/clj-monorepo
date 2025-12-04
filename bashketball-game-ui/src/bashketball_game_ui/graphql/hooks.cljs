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
   ["@apollo/client/react" :refer [useQuery useMutation useSubscription]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [camel-snake-kebab.core :as csk]
   [clojure.walk :as walk]
   [uix.core :as uix]))

(defn- encode-variable-keys
  "Converts kebab-case keys to camelCase in a variables map.

  Recursively transforms all map keys for GraphQL compatibility."
  [variables]
  (when variables
    (walk/postwalk
     (fn [x]
       (if (map? x)
         (into {} (map (fn [[k v]] [(csk/->camelCase k) v])) x)
         x))
     variables)))

(defn- encode-options
  "Encodes hook options for Apollo, converting variable keys to camelCase.

  Always returns a JS object (never null) for Apollo Client 4 compatibility."
  [options]
  (clj->js (if options
             (update options :variables encode-variable-keys)
             {})))

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
   (let [result (useQuery query (encode-options options))]
     {:data    (some-> result :data decoder/decode-js-response)
      :loading (:loading result)
      :error   (:error result)
      :refetch (:refetch result)})))

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
   (let [[execute-fn result] (useQuery query (doto (encode-options options)
                                               (aset "skip" true)))
         decode-fn           (uix/use-callback (fn [data] (decoder/decode-js-response data)) [])]
     [(uix/use-callback
       (fn [exec-options]
         (-> (execute-fn (encode-options exec-options))
             (.then (fn [res] (update res :data decode-fn)))))
       [execute-fn decode-fn])
      {:data    (some-> result :data decode-fn)
       :loading (:loading result)
       :error   (:error result)
       :called  (:called result)}])))

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
   (let [[mutate-fn result] (useMutation mutation (encode-options options))
         decode-fn          (uix/use-callback (fn [data] (decoder/decode-js-response data)) [])]
     [(uix/use-callback
       (fn
         ([] (mutate-fn))
         ([exec-options]
          (-> (mutate-fn (encode-options exec-options))
              (.then (fn [response]
                       {:data (some-> response .-data decode-fn)})))))
       [mutate-fn decode-fn])
      {:data    (some-> result :data decode-fn)
       :loading (:loading result)
       :error   (:error result)}])))

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
   (let [result (useSubscription subscription (encode-options options))]
     {:data    (some-> result :data decoder/decode-js-response)
      :loading (:loading result)
      :error   (:error result)})))
