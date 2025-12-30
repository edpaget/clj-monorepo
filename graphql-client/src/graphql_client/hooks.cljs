(ns graphql-client.hooks
  "React hooks wrapping Apollo Client with automatic response decoding.

  Provides [[use-query]], [[use-mutation]], and [[use-subscription]] hooks
  that wrap Apollo's hooks with automatic response decoding support.

  These hooks handle:
  - Converting kebab-case variable keys to camelCase for the request
  - Optionally decoding responses using a provided decoder function
  - Returning consistent result maps with :data, :loading, :error, etc."
  (:require
   ["@apollo/client/react" :refer [useQuery useMutation useSubscription]]
   [graphql-client.encode :as encode]
   [uix.core :as uix]))

(defn use-query
  "Executes a GraphQL query with optional automatic response decoding.

  Returns a map with:
  - `:data` - Response data (decoded if :decoder option provided)
  - `:loading` - Boolean indicating if query is in flight
  - `:error` - Error object if query failed
  - `:refetch` - Function to refetch the query

  Options include all Apollo useQuery options, plus:
  - `:decoder` - Function to decode the response data (default: identity)
  - `:variables` - Map with kebab-case keys (auto-converted to camelCase)

  Example:
    (let [{:keys [data loading error]} (use-query GAME_QUERY
                                         {:variables {:game-id id}
                                          :decoder my-decode-fn})]
      (when-not loading
        (:game data)))"
  ([query]
   (use-query query nil))
  ([query options]
   (let [decoder        (or (:decoder options) identity)
         apollo-options (-> options
                            (dissoc :decoder)
                            encode/encode-options)
         result         (useQuery query apollo-options)]
     {:data    (some-> result :data decoder)
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

  Options include all Apollo useQuery options, plus:
  - `:decoder` - Function to decode the response data (default: identity)

  Example:
    (let [[fetch-game {:keys [data loading]}] (use-lazy-query GAME_QUERY
                                                {:decoder my-decode-fn})]
      ($ :button {:on-click #(fetch-game {:variables {:id \"abc\"}})}
         \"Load Game\"))"
  ([query]
   (use-lazy-query query nil))
  ([query options]
   (let [decoder             (or (:decoder options) identity)
         apollo-options      (-> options
                                 (dissoc :decoder)
                                 encode/encode-options
                                 (doto (aset "skip" true)))
         [execute-fn result] (useQuery query apollo-options)
         decode-fn           (uix/use-callback (fn [data] (decoder data)) [decoder])]
     [(uix/use-callback
       (fn [exec-options]
         (-> (execute-fn (encode/encode-options exec-options))
             (.then (fn [res] (update res :data decode-fn)))))
       [execute-fn decode-fn])
      {:data    (some-> result :data decode-fn)
       :loading (:loading result)
       :error   (:error result)
       :called  (:called result)}])))

(defn use-mutation
  "Returns [mutate-fn result] with optional automatic response decoding.

  The mutate-fn accepts options and returns a promise resolving to
  the response. The result map contains the current state.

  Returns [mutate-fn result-map] where:
  - `mutate-fn` - Function to execute the mutation, returns Promise
  - `result-map` - Map with :data, :loading, :error

  Options include all Apollo useMutation options, plus:
  - `:decoder` - Function to decode the response data (default: identity)

  Example:
    (let [[create-game {:keys [loading]}] (use-mutation CREATE_GAME_MUTATION
                                            {:decoder my-decode-fn})]
      ($ :button {:on-click #(create-game {:variables {:deck-id deck-id}})
                  :disabled loading}
         \"Create Game\"))"
  ([mutation]
   (use-mutation mutation nil))
  ([mutation options]
   (let [decoder            (or (:decoder options) identity)
         apollo-options     (-> options
                                (dissoc :decoder)
                                encode/encode-options)
         [mutate-fn result] (useMutation mutation apollo-options)
         decode-fn          (uix/use-callback (fn [data] (decoder data)) [decoder])]
     [(uix/use-callback
       (fn
         ([] (mutate-fn))
         ([exec-options]
          (-> (mutate-fn (encode/encode-options exec-options))
              (.then (fn [response]
                       {:data (some-> response .-data decode-fn)})))))
       [mutate-fn decode-fn])
      {:data    (some-> result :data decode-fn)
       :loading (:loading result)
       :error   (:error result)}])))

(defn use-subscription
  "Subscribes to a GraphQL subscription with optional automatic response decoding.

  Returns a map with:
  - `:data` - Subscription data (decoded if :decoder option provided)
  - `:loading` - Boolean indicating if subscription is connecting
  - `:error` - Error object if subscription failed

  Options include all Apollo useSubscription options, plus:
  - `:decoder` - Function to decode the subscription data (default: identity)

  Example:
    (let [{:keys [data]} (use-subscription GAME_UPDATED_SUBSCRIPTION
                           {:variables {:game-id id}
                            :decoder my-decode-fn})]
      (when data
        (handle-game-event (:game-updated data))))"
  ([subscription]
   (use-subscription subscription nil))
  ([subscription options]
   (let [decoder        (or (:decoder options) identity)
         apollo-options (-> options
                            (dissoc :decoder)
                            encode/encode-options)
         result         (useSubscription subscription apollo-options)]
     {:data    (some-> result :data decoder)
      :loading (:loading result)
      :error   (:error result)})))
