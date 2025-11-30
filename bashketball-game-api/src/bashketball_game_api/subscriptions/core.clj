(ns bashketball-game-api.subscriptions.core
  "Subscription infrastructure using core.async channels.

  Provides topic-based pub/sub for real-time updates. Topics are vectors
  like `[:game game-id]` or `[:lobby]`. Subscribers receive core.async
  channels that emit messages published to their topic."
  (:require [clojure.core.async :as async]
            [malli.core :as m]))

(def Topic
  "A topic is a vector identifying what to subscribe to."
  [:vector :any])

(def Message
  "A message published to subscribers."
  [:map
   [:type :keyword]
   [:data {:optional true} :any]])

(defprotocol ISubscriptionManager
  "Protocol for managing pub/sub subscriptions."

  (subscribe! [this topic]
    "Subscribe to a topic. Returns a core.async channel that will receive
     messages published to this topic. Channel uses sliding-buffer to prevent
     slow consumers from blocking publishers.")

  (unsubscribe! [this topic channel]
    "Unsubscribe a channel from a topic. Closes the channel.")

  (publish! [this topic message]
    "Publish a message to all subscribers of a topic. Non-blocking.")

  (subscriber-count [this topic]
    "Returns the number of active subscribers for a topic.")

  (topics [this]
    "Returns all topics with active subscribers."))

(defrecord CoreAsyncSubscriptionManager [subscriptions]
  ISubscriptionManager

  (subscribe! [_ topic]
    {:pre [(m/validate Topic topic)]}
    (let [ch (async/chan (async/sliding-buffer 10))]
      (swap! subscriptions update topic (fnil conj #{}) ch)
      ch))

  (unsubscribe! [_ topic channel]
    (async/close! channel)
    (swap! subscriptions
           (fn [subs]
             (let [updated (update subs topic disj channel)]
               (if (empty? (get updated topic))
                 (dissoc updated topic)
                 updated))))
    nil)

  (publish! [_ topic message]
    (doseq [ch (get @subscriptions topic)]
      (async/offer! ch message))
    nil)

  (subscriber-count [_ topic]
    (count (get @subscriptions topic)))

  (topics [_]
    (keys @subscriptions)))

(defn create-subscription-manager
  "Creates a new CoreAsyncSubscriptionManager."
  []
  (->CoreAsyncSubscriptionManager (atom {})))
