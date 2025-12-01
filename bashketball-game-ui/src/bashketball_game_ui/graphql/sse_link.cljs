(ns bashketball-game-ui.graphql.sse-link
  "Custom Apollo link for game subscriptions over SSE.

  The bashketball-game-api uses REST-style SSE endpoints rather than
  GraphQL-over-SSE. This link intercepts subscription operations and
  connects to the appropriate SSE endpoint based on the subscription name."
  (:require
   ["@apollo/client" :refer [ApolloLink]]
   ["zen-observable" :as Observable]
   [bashketball-game-ui.config :as config]))

(defn game-subscription-url
  "Returns SSE endpoint URL for game subscriptions."
  [game-id]
  (str config/api-base-url "/subscriptions/game/" game-id))

(defn lobby-subscription-url
  "Returns SSE endpoint URL for lobby subscriptions."
  []
  (str config/api-base-url "/subscriptions/lobby"))

(defn- get-subscription-name
  "Extracts the subscription name from a GraphQL operation."
  [operation]
  (let [definition (-> operation :query :definitions (aget 0))
        selections (-> definition :selectionSet :selections)]
    (when (pos? (:length selections))
      (-> selections (aget 0) :name :value))))

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
  from SSE events."
  [subscription-name variables]
  (Observable.
   (fn [observer]
     (let [url (case subscription-name
                 "gameUpdated" (game-subscription-url (:gameId variables))
                 "lobbyUpdated" (lobby-subscription-url)
                 nil)
           event-source (when url (create-event-source url))]

       (if-not event-source
         (do
           (.error observer (js/Error. (str "Unknown subscription: " subscription-name)))
           (fn []))

         (do
           (set! (.-onopen event-source)
                 (fn [_]
                   (js/console.debug "SSE connected to" url)))

           (set! (.-onmessage event-source)
                 (fn [event]
                   (let [data (js/JSON.parse (.-data event))]
                     (.next observer #js {:data (js-obj subscription-name data)}))))

           (set! (.-onerror event-source)
                 (fn [error]
                   (js/console.error "SSE error" error)
                   (when (= (.-readyState event-source) 2)
                     (.error observer error))))

           (fn []
             (js/console.debug "SSE disconnecting from" url)
             (.close event-source))))))))

(defn create-sse-link
  "Creates an Apollo link that handles subscriptions via SSE.

  Intercepts subscription operations and connects to the API's
  SSE endpoints. Non-subscription operations are forwarded to
  the next link in the chain."
  []
  (ApolloLink.
   (fn [operation forward]
     (if-not (is-subscription? operation)
       (forward operation)
       (let [subscription-name (get-subscription-name operation)
             variables (js->clj (:variables operation) :keywordize-keys true)]
         (handle-sse-subscription subscription-name variables))))))
