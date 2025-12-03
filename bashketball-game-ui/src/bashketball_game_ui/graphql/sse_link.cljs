(ns bashketball-game-ui.graphql.sse-link
  "Custom Apollo link for GraphQL subscriptions over SSE.

  Intercepts subscription operations and connects to the GraphQL
  SSE endpoint at `/graphql/subscriptions`. Encodes the subscription
  query as a URL parameter and streams events from the server."
  (:require
   ["@apollo/client" :refer [ApolloLink]]
   ["graphql" :refer [print] :rename {print gql-print}]
   ["rxjs" :refer [Observable]]
   [bashketball-game-ui.config :as config]))

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
  "Handles an SSE subscription by creating an Observable.

  Returns a zen-observable that emits GraphQL-shaped responses
  from SSE events. The server sends data as `data: {\"subscriptionName\": {...}}`
  which we pass through directly as Apollo expects."
  [query variables]
  (Observable.
   (fn [observer]
     (let [url          (graphql-subscription-url query variables)
           event-source (create-event-source url)]

       (set! (.-onopen event-source)
             (fn [_]
               (js/console.debug "SSE connected to" url)))

       (set! (.-onmessage event-source)
             (fn [event]
               (let [data (js/JSON.parse (.-data event))]
                 ;; Server sends GraphQL-shaped data directly: {subscriptionName: {...}}
                 (.next observer #js {:data data}))))

       (set! (.-onerror event-source)
             (fn [error]
               (js/console.error "SSE error" error)
               (when (= (.-readyState event-source) 2)
                 (.error observer error))))

       (fn []
         (js/console.debug "SSE disconnecting from" url)
         (.close event-source))))))

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
