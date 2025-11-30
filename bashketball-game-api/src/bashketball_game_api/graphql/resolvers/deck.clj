(ns bashketball-game-api.graphql.resolvers.deck
  "GraphQL resolvers for deck queries and mutations.

  Provides Query resolvers for listing and fetching decks, and Mutation
  resolvers for deck CRUD operations. All deck operations require authentication."
  (:require
   [bashketball-game-api.services.deck :as deck-svc]
   [graphql-server.core :refer [defresolver def-resolver-map]]))

(def Deck
  "GraphQL schema for Deck type."
  [:map {:graphql/type :Deck}
   [:id :uuid]
   [:name :string]
   [:cardSlugs [:vector :string]]
   [:isValid :boolean]
   [:validationErrors {:optional true} [:maybe [:vector :string]]]])

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

(defn- deck->graphql
  "Transforms a deck record to GraphQL response format."
  [deck]
  {:id (:id deck)
   :name (:name deck)
   :cardSlugs (or (:card-slugs deck) [])
   :isValid (boolean (:is-valid deck))
   :validationErrors (:validation-errors deck)})

(defn- get-deck-service
  "Gets the deck service from the request context."
  [ctx]
  (get-in ctx [:request :resolver-map :deck-service]))

;; ---------------------------------------------------------------------------
;; Query Resolvers

(defresolver :Query :myDecks
  "Returns all decks owned by the authenticated user."
  [:=> [:cat :any :any :any] [:vector Deck]]
  [ctx _args _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)]
    (mapv deck->graphql (deck-svc/list-user-decks deck-service user-id))))

(defresolver :Query :deck
  "Returns a deck by ID if owned by the authenticated user."
  [:=> [:cat :any [:map [:id :string]] :any] [:maybe Deck]]
  [ctx {:keys [id]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck-id      (parse-uuid id)]
    (when-let [deck (deck-svc/get-deck-for-user deck-service deck-id user-id)]
      (deck->graphql deck))))

;; ---------------------------------------------------------------------------
;; Mutation Resolvers

(defresolver :Mutation :createDeck
  "Creates a new empty deck for the authenticated user."
  [:=> [:cat :any [:map [:name :string]] :any] Deck]
  [ctx {:keys [name]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck         (deck-svc/create-deck! deck-service user-id name)]
    (deck->graphql deck)))

(defresolver :Mutation :updateDeck
  "Updates a deck's name and/or card list."
  [:=> [:cat :any [:map
                   [:id :string]
                   [:name {:optional true} [:maybe :string]]
                   [:cardSlugs {:optional true} [:maybe [:vector :string]]]] :any]
   [:maybe Deck]]
  [ctx {:keys [id name cardSlugs]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck-id      (parse-uuid id)
        updates      (cond-> {}
                       name (assoc :name name)
                       cardSlugs (assoc :card-slugs cardSlugs))]
    (when-let [deck (deck-svc/update-deck! deck-service deck-id user-id updates)]
      (deck->graphql deck))))

(defresolver :Mutation :deleteDeck
  "Deletes a deck owned by the authenticated user."
  [:=> [:cat :any [:map [:id :string]] :any] :boolean]
  [ctx {:keys [id]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck-id      (parse-uuid id)]
    (boolean (deck-svc/delete-deck! deck-service deck-id user-id))))

(defresolver :Mutation :validateDeck
  "Validates a deck and updates its validation state."
  [:=> [:cat :any [:map [:id :string]] :any] [:maybe Deck]]
  [ctx {:keys [id]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck-id      (parse-uuid id)]
    (when-let [deck (deck-svc/validate-deck! deck-service deck-id user-id)]
      (deck->graphql deck))))

(defresolver :Mutation :addCardsToDeck
  "Adds cards to an existing deck."
  [:=> [:cat :any [:map [:id :string] [:cardSlugs [:vector :string]]] :any]
   [:maybe Deck]]
  [ctx {:keys [id cardSlugs]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck-id      (parse-uuid id)]
    (when-let [deck (deck-svc/add-cards-to-deck! deck-service deck-id user-id cardSlugs)]
      (deck->graphql deck))))

(defresolver :Mutation :removeCardsFromDeck
  "Removes cards from an existing deck."
  [:=> [:cat :any [:map [:id :string] [:cardSlugs [:vector :string]]] :any]
   [:maybe Deck]]
  [ctx {:keys [id cardSlugs]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck-id      (parse-uuid id)]
    (when-let [deck (deck-svc/remove-cards-from-deck! deck-service deck-id user-id cardSlugs)]
      (deck->graphql deck))))

(def-resolver-map)
