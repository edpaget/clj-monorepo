(ns bashketball-editor-api.graphql.resolvers.cardset
  "GraphQL resolvers for card set queries and mutations.

  Provides query and mutation resolvers for card sets, including a nested
  resolver for fetching cards within a set."
  (:require
   [bashketball-editor-api.git.sets :as git-sets]
   [bashketball-editor-api.graphql.middleware :as middleware]
   [bashketball-editor-api.graphql.schemas.card :as schemas]
   [bashketball-editor-api.models.protocol :as repo]
   [graphql-server.core :as gql]))

(defn- transform-card-set
  "Transforms a card set entity for GraphQL output.

  Converts timestamps to ISO strings and id to string."
  [card-set]
  (-> card-set
      (update :id str)
      (update :created-at #(when % (str %)))
      (update :updated-at #(when % (str %)))))

(defn- transform-card
  "Transforms a card entity for GraphQL output."
  [card]
  (-> card
      (update :set-id str)
      (update :created-at #(when % (str %)))
      (update :updated-at #(when % (str %)))))

(defn- get-user-context
  "Extracts user context from GraphQL context for Git operations."
  [ctx]
  (let [user-id (get-in ctx [:request :authn/user-id])
        user (repo/find-by (:user-repo ctx) {:id (parse-uuid user-id)})]
    {:name (:name user)
     :email (:email user)
     :github-token (:github-token user)}))

(gql/defresolver :Query :cardSet
  "Fetches a single card set by ID."
  [:=> [:cat :any [:map [:id :string]] :any]
   [:maybe schemas/CardSet]]
  [ctx {:keys [id]} _value]
  (when-let [card-set (repo/find-by (:set-repo ctx) {:id (parse-uuid id)})]
    (transform-card-set card-set)))

(gql/defresolver :Query :cardSets
  "Lists all card sets."
  [:=> [:cat :any :any :any]
   [:vector schemas/CardSet]]
  [ctx _args _value]
  (mapv transform-card-set (repo/find-all (:set-repo ctx) {})))

(gql/defresolver :Mutation :createCardSet
  "Creates a new card set."
  [:=> [:cat :any [:map [:input schemas/CardSetInput]] :any]
   schemas/CardSet]
  [ctx {:keys [input]} _value]
  (let [set-data (assoc input :_user (get-user-context ctx))]
    (transform-card-set (repo/create! (:set-repo ctx) set-data))))

(gql/defresolver :Mutation :updateCardSet
  "Updates an existing card set."
  [:=> [:cat :any [:map [:id :string] [:input schemas/CardSetInput]] :any]
   schemas/CardSet]
  [ctx {:keys [id input]} _value]
  (let [set-data (assoc input :_user (get-user-context ctx))]
    (transform-card-set (repo/update! (:set-repo ctx)
                                      (parse-uuid id)
                                      set-data))))

(gql/defresolver :Mutation :deleteCardSet
  "Deletes a card set.

  Note: This only deletes the set metadata. Cards within the set are
  not automatically deleted."
  [:=> [:cat :any [:map [:id :string]] :any]
   :boolean]
  [ctx {:keys [id]} _value]
  (git-sets/delete-set! (:set-repo ctx)
                        (parse-uuid id)
                        (get-user-context ctx)))

(defn cards-resolver
  "Nested resolver for fetching cards in a card set.

  This is manually defined since graphql-server doesn't support
  nested field resolvers through defresolver."
  [ctx _args card-set]
  (let [set-id (if (string? (:id card-set))
                 (parse-uuid (:id card-set))
                 (:id card-set))]
    (mapv transform-card
          (repo/find-all (:card-repo ctx)
                         {:where {:set-id set-id}}))))

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
