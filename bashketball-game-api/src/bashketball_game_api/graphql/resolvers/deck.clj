(ns bashketball-game-api.graphql.resolvers.deck
  "GraphQL resolvers for deck queries and mutations.

  Provides Query resolvers for listing and fetching decks, and Mutation
  resolvers for deck CRUD operations. All deck operations require authentication."
  (:require
   [bashketball-game-api.services.catalog :as catalog]
   [bashketball-game-api.services.deck :as deck-svc]
   [bashketball-schemas.card :as card-schema]
   [graphql-server.core :refer [defresolver def-resolver-map]]))

(def Deck
  "GraphQL schema for Deck type.

  Used for GraphQL schema generation. Defines the complete Deck type including
  the `cards` field which is resolved lazily by the `Deck.cards` field resolver."
  [:map {:graphql/type :Deck}
   [:id :uuid]
   [:name :string]
   [:cardSlugs [:vector :string]]
   [:cards [:vector card-schema/Card]]
   [:isValid :boolean]
   [:validationErrors {:optional true} [:vector :string]]])

(def DeckResponse
  "Schema for deck data returned by resolvers.

  Separate from [[Deck]] because resolvers return partial data - the `cards`
  field is populated by the `Deck.cards` field resolver, not by the query/mutation
  resolvers directly. This schema makes `cards` optional for Malli validation."
  [:map {:graphql/type :Deck}
   [:id :uuid]
   [:name :string]
   [:cardSlugs [:vector :string]]
   [:cards {:optional true} [:vector card-schema/Card]]
   [:isValid :boolean]
   [:validationErrors {:optional true} [:vector :string]]])

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
  [:=> [:cat :any :any :any] [:vector DeckResponse]]
  [ctx _args _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)]
    (mapv deck->graphql (deck-svc/list-user-decks deck-service user-id))))

(defresolver :Query :deck
  "Returns a deck by ID if owned by the authenticated user."
  [:=> [:cat :any [:map [:id :uuid]] :any] [:maybe DeckResponse]]
  [ctx {:keys [id]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [deck (deck-svc/get-deck-for-user deck-service id user-id)]
      (deck->graphql deck))))

(defresolver :Deck :cards
  "Returns cards in a given deck"
  [:=> [:cat :any :any :any] [:vector card-schema/Card]]
  [ctx _args {:keys [cardSlugs]}]
  (let [card-catalog (get-in ctx [:request :resolver-map :card-catalog])]
    (mapv #(catalog/get-card card-catalog %) cardSlugs)))

;; ---------------------------------------------------------------------------
;; Mutation Resolvers

(defresolver :Mutation :createDeck
  "Creates a new empty deck for the authenticated user."
  [:=> [:cat :any [:map [:name :string]] :any] DeckResponse]
  [ctx {:keys [name]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        deck         (deck-svc/create-deck! deck-service user-id name)]
    (deck->graphql deck)))

(defresolver :Mutation :updateDeck
  "Updates a deck's name and/or card list."
  [:=> [:cat :any [:map
                   [:id :uuid]
                   [:name {:optional true} [:maybe :string]]
                   [:card-slugs {:optional true} [:vector :string]]] :any]
   [:maybe DeckResponse]]
  [ctx {:keys [id name card-slugs]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)
        updates      (cond-> {}
                       name (assoc :name name)
                       card-slugs (assoc :card-slugs card-slugs))]
    (when-let [deck (deck-svc/update-deck! deck-service id user-id updates)]
      (deck->graphql deck))))

(defresolver :Mutation :deleteDeck
  "Deletes a deck owned by the authenticated user."
  [:=> [:cat :any [:map [:id :uuid]] :any] :boolean]
  [ctx {:keys [id]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)]
    (boolean (deck-svc/delete-deck! deck-service id user-id))))

(defresolver :Mutation :validateDeck
  "Validates a deck and updates its validation state."
  [:=> [:cat :any [:map [:id :uuid]] :any] [:maybe DeckResponse]]
  [ctx {:keys [id]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [deck (deck-svc/validate-deck! deck-service id user-id)]
      (deck->graphql deck))))

(defresolver :Mutation :addCardsToDeck
  "Adds cards to an existing deck."
  [:=> [:cat :any [:map [:id :uuid] [:card-slugs [:vector :string]]] :any]
   [:maybe DeckResponse]]
  [ctx {:keys [id card-slugs]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [deck (deck-svc/add-cards-to-deck! deck-service id user-id card-slugs)]
      (deck->graphql deck))))

(defresolver :Mutation :removeCardsFromDeck
  "Removes cards from an existing deck."
  [:=> [:cat :any [:map [:id :uuid] [:card-slugs [:vector :string]]] :any]
   [:maybe DeckResponse]]
  [ctx {:keys [id card-slugs]} _value]
  (require-auth! ctx)
  (let [deck-service (get-deck-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [deck (deck-svc/remove-cards-from-deck! deck-service id user-id card-slugs)]
      (deck->graphql deck))))

(def-resolver-map)
