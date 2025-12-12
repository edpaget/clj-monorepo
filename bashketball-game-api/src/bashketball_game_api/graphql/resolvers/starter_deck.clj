(ns bashketball-game-api.graphql.resolvers.starter-deck
  "GraphQL resolvers for starter deck queries and mutations.

  Provides Query resolvers for listing starter deck definitions and user claims,
  and a Mutation resolver for claiming individual starter decks."
  (:require
   [bashketball-game-api.services.starter-deck :as starter-deck-svc]
   [graphql-server.core :refer [defresolver def-resolver-map]]))

(def StarterDeckDefinition
  "GraphQL schema for StarterDeckDefinition type.

  Represents a starter deck definition from the config file."
  [:map {:graphql/type :StarterDeckDefinition}
   [:id :string]
   [:name :string]
   [:description :string]
   [:card-count :int]])

(def ClaimedStarterDeck
  "GraphQL schema for ClaimedStarterDeck type.

  Represents a starter deck that has been claimed by a user."
  [:map {:graphql/type :ClaimedStarterDeck}
   [:starter-deck-id :string]
   [:deck-id :uuid]
   [:claimed-at :string]])

(def ClaimedStarterDeckResponse
  "Schema for claimed starter deck data returned by claim mutation.

  Includes the deck object directly for convenience."
  [:map {:graphql/type :ClaimedStarterDeck}
   [:starter-deck-id :string]
   [:deck-id :uuid]
   [:claimed-at :string]
   [:deck {:optional true} :any]])

(defn- get-user-id
  "Extracts and parses the user ID from the request context."
  [ctx]
  (when-let [id (get-in ctx [:request :authn/user-id])]
    (parse-uuid id)))

(defn- authenticated?
  "Returns true if the request is authenticated."
  [ctx]
  (get-in ctx [:request :authn/authenticated?]))

(defn- require-auth!
  "Throws an exception if the user is not authenticated."
  [ctx]
  (when-not (authenticated? ctx)
    (throw (ex-info "Authentication required" {:type :unauthorized}))))

(defn- get-starter-deck-service
  "Gets the starter deck service from the request context."
  [ctx]
  (get-in ctx [:request :resolver-map :starter-deck-service]))

(defn- definition->graphql
  "Transforms a starter deck definition to GraphQL response format."
  [{:keys [id name description card-slugs]}]
  {:id (clojure.core/name id)
   :name name
   :description description
   :card-count (count card-slugs)})

;; ---------------------------------------------------------------------------
;; Query Resolvers

(defresolver :Query :starterDecks
  "Returns all starter deck definitions."
  [:=> [:cat :any :any :any] [:vector StarterDeckDefinition]]
  [ctx _args _value]
  (let [service (get-starter-deck-service ctx)]
    (mapv definition->graphql (starter-deck-svc/get-starter-deck-definitions service))))

(defresolver :Query :availableStarterDecks
  "Returns starter deck definitions the authenticated user hasn't claimed."
  [:=> [:cat :any :any :any] [:vector StarterDeckDefinition]]
  [ctx _args _value]
  (require-auth! ctx)
  (let [service (get-starter-deck-service ctx)
        user-id (get-user-id ctx)]
    (mapv definition->graphql (starter-deck-svc/get-available-starter-decks service user-id))))

(defresolver :Query :claimedStarterDecks
  "Returns the authenticated user's claimed starter decks."
  [:=> [:cat :any :any :any] [:vector ClaimedStarterDeck]]
  [ctx _args _value]
  (require-auth! ctx)
  (let [service (get-starter-deck-service ctx)
        user-id (get-user-id ctx)]
    (starter-deck-svc/get-claimed-starter-decks service user-id)))

;; ---------------------------------------------------------------------------
;; Mutation Resolvers

(defresolver :Mutation :claimStarterDeck
  "Claims a starter deck for the authenticated user.

  Returns the claim record with the created deck, or null if already claimed
  or starter deck ID not found."
  [:=> [:cat :any [:map [:starter-deck-id :string]] :any] [:maybe ClaimedStarterDeckResponse]]
  [ctx {:keys [starter-deck-id]} _value]
  (require-auth! ctx)
  (let [service (get-starter-deck-service ctx)
        user-id (get-user-id ctx)]
    (starter-deck-svc/claim-starter-deck! service user-id starter-deck-id)))

(def-resolver-map)
