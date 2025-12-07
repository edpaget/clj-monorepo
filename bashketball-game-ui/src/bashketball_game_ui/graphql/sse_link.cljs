(ns bashketball-game-ui.graphql.sse-link
  "Custom Apollo link for GraphQL subscriptions over SSE.

  Intercepts subscription operations and connects to the GraphQL
  SSE endpoint at `/graphql/subscriptions`. Encodes the subscription
  query as a URL parameter and streams events from the server.

  Includes automatic retry logic - will attempt to reconnect up to 3 times
  before raising an error to the subscriber."
  (:require
   ["@apollo/client" :refer [ApolloLink]]
   ["graphql" :refer [print] :rename {print gql-print}]
   ["rxjs" :refer [Observable]]
   [bashketball-game-ui.config :as config]))

(def ^:private max-retries
  "Maximum number of reconnection attempts before raising an error."
  3)

(def ^:private retry-delay-ms
  "Delay in milliseconds before attempting to reconnect."
  1000)

(defn- graphql-subscription-url
  "Builds the GraphQL subscription URL with encoded query and variables.

  The endpoint expects the query as a URL parameter in the format:
  `/graphql/subscriptions?query=subscription {...}&variables={...}`"
  [query variables]
  (let [base-url      (str config/api-base-url "/graphql/subscriptions")
        ;; Convert query AST to string using graphql's print function
        query-str     (gql-print query)
        encoded-query (js/encodeURIComponent query-str)
        url           (str base-url "?query=" encoded-query)]
    (if (and variables (pos? (count (js/Object.keys variables))))
      (str url "&variables=" (js/encodeURIComponent (js/JSON.stringify variables)))
      url)))

(defn- is-subscription?
  "Returns true if the operation is a subscription."
  [operation]
  (let [definition (-> operation :query :definitions (aget 0))]
    (and (= "OperationDefinition" (:kind definition))
         (= "subscription" (:operation definition)))))

(defn- create-event-source
  "Creates an EventSource connection to the SSE endpoint.

  Returns the EventSource instance. The caller is responsible for
  setting up event handlers and closing the connection."
  [url]
  (js/EventSource. url #js {:withCredentials true}))

(defn- handle-sse-subscription
  "Handles an SSE subscription by creating an Observable with retry logic.

  Returns an Observable that emits GraphQL-shaped responses from SSE events.
  The server sends data as `data: {\"subscriptionName\": {...}}` which we pass
  through directly as Apollo expects.

  On connection errors, will retry up to [[max-retries]] times with a delay of
  [[retry-delay-ms]] between attempts. After exhausting retries, raises the
  error to the observer."
  [query variables]
  (Observable.
   (fn [observer]
     (let [url            (graphql-subscription-url query variables)
           retry-count    (atom 0)
           current-source (atom nil)
           closed?        (atom false)
           connect-fn     (atom nil)]

       (reset! connect-fn
               (fn []
                 (when-not @closed?
                   (let [event-source (create-event-source url)]
                     (reset! current-source event-source)

                     (set! (.-onopen event-source)
                           (fn [_]
                             (js/console.debug "SSE connected to" url)
                             (reset! retry-count 0)))

                     (set! (.-onmessage event-source)
                           (fn [event]
                             (let [data (js/JSON.parse (.-data event))]
                               (.next observer #js {:data data}))))

                     (set! (.-onerror event-source)
                           (fn [error]
                             (js/console.error "SSE error" error)
                             (when (= (.-readyState event-source) 2)
                               (.close event-source)
                               (if (< @retry-count max-retries)
                                 (do
                                   (swap! retry-count inc)
                                   (js/console.debug "SSE reconnecting, attempt" @retry-count "of" max-retries)
                                   (js/setTimeout @connect-fn retry-delay-ms))
                                 (do
                                   (js/console.error "SSE max retries exceeded")
                                   (.error observer error))))))))))

       (@connect-fn)

       (fn []
         (js/console.debug "SSE disconnecting from" url)
         (reset! closed? true)
         (when-let [source @current-source]
           (.close source)))))))

(defn create-sse-link
  "Creates an Apollo link that handles subscriptions via SSE.

  Intercepts subscription operations and connects to the GraphQL
  SSE endpoint. Non-subscription operations are forwarded to
  the next link in the chain."
  []
  (ApolloLink.
   (fn [operation forward]
     (if-not (is-subscription? operation)
       (forward operation)
       (handle-sse-subscription (:query operation) (:variables operation))))))
