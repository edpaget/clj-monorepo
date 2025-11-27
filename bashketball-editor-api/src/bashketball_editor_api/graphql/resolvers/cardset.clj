(ns bashketball-editor-api.graphql.resolvers.cardset
  "GraphQL resolvers for card set queries and mutations.

  Provides query and mutation resolvers for card sets, including a nested
  resolver for fetching cards within a set."
  (:require
   [bashketball-editor-api.graphql.middleware :as middleware]
   [bashketball-editor-api.graphql.schemas.card :as schemas]
   [bashketball-editor-api.models.protocol :as repo]
   [graphql-server.core :as gql]))

(defn- transform-card-set
  "Transforms a card set entity for GraphQL output.

  Converts timestamps to ISO strings."
  [card-set]
  (-> card-set
      (update :created-at #(when % (str %)))
      (update :updated-at #(when % (str %)))))

(defn- transform-card
  "Transforms a card entity for GraphQL output."
  [card]
  (-> card
      (update :created-at #(when % (str %)))
      (update :updated-at #(when % (str %)))))

(gql/defresolver :Query :cardSet
  "Fetches a single card set by slug."
  [:=> [:cat :any [:map [:slug :string]] :any]
   [:maybe schemas/CardSet]]
  [ctx {:keys [slug]} _value]
  (when-let [card-set (repo/find-by (:set-repo ctx) {:slug slug})]
    (transform-card-set card-set)))

(gql/defresolver :Query :cardSets
  "Lists all card sets."
  [:=> [:cat :any :any :any]
   schemas/CardSetsResponse]
  [ctx _args _value]
  {:data (mapv transform-card-set (repo/find-all (:set-repo ctx) {}))})

(gql/defresolver :Mutation :createCardSet
  "Creates a new card set."
  [:=> [:cat :any [:map [:input schemas/CardSetInput]] :any]
   schemas/CardSet]
  [ctx {:keys [input]} _value]
  (transform-card-set (repo/create! (:set-repo ctx) input)))

(gql/defresolver :Mutation :updateCardSet
  "Updates an existing card set."
  [:=> [:cat :any [:map [:slug :string] [:input schemas/CardSetInput]] :any]
   schemas/CardSet]
  [ctx {:keys [slug input]} _value]
  (transform-card-set (repo/update! (:set-repo ctx) slug input)))

(gql/defresolver :Mutation :deleteCardSet
  "Deletes a card set.

  Note: This only deletes the set metadata. Cards within the set are
  not automatically deleted."
  [:=> [:cat :any [:map [:slug :string]] :any]
   :boolean]
  [ctx {:keys [slug]} _value]
  (repo/delete! (:set-repo ctx) slug))

(defn cards-resolver
  "Nested resolver for fetching cards in a card set.

  This is manually defined since graphql-server doesn't support
  nested field resolvers through defresolver."
  [ctx _args card-set]
  {:data (mapv transform-card
               (repo/find-all (:card-repo ctx)
                              {:where {:set-slug (:slug card-set)}}))})

(def query-resolvers
  "Query resolvers for card sets."
  (let [all-resolvers (gql/collect-resolvers 'bashketball-editor-api.graphql.resolvers.cardset)]
    (into {}
          (filter (fn [[[obj _action] _]]
                    (= obj :Query))
                  all-resolvers))))

(def mutation-resolvers
  "Mutation resolvers for card sets (auth required)."
  (let [all-resolvers (gql/collect-resolvers 'bashketball-editor-api.graphql.resolvers.cardset)]
    (gql/apply-middleware [middleware/require-authentication]
                          (into {}
                                (filter (fn [[[obj _action] _]]
                                          (= obj :Mutation))
                                        all-resolvers)))))

(def resolvers
  "Combined query and mutation resolvers for card sets.

  Note: The CardSet.cards nested resolver is not included here.
  It needs to be added to the Lacinia schema separately via
  the handler/schema configuration."
  (merge query-resolvers mutation-resolvers))
