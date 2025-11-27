(ns bashketball-editor-api.graphql.resolvers.card
  "GraphQL resolvers for card queries and mutations.

  Provides query and mutation resolvers for game cards with type-specific
  create operations and generic update/delete mutations."
  (:require
   [bashketball-editor-api.git.cards :as git-cards]
   [bashketball-editor-api.graphql.middleware :as middleware]
   [bashketball-editor-api.graphql.schemas.card :as schemas]
   [bashketball-editor-api.models.protocol :as repo]
   [graphql-server.core :as gql]))

(def GameCard
  "GraphQL output schema for game cards."
  schemas/GameCard)

(defn- transform-card
  "Transforms a card entity for GraphQL output.

  Converts timestamps to ISO strings and set-id to string."
  [card]
  (-> card
      (update :set-id str)
      (update :created-at #(when % (str %)))
      (update :updated-at #(when % (str %)))))

(defn- get-user-context
  "Extracts user context from GraphQL context for Git operations."
  [ctx]
  (let [user-id (get-in ctx [:request :authn/user-id])
        user    (repo/find-by (:user-repo ctx) {:id (parse-uuid user-id)})]
    {:name (:name user)
     :email (:email user)
     :github-token (:github-token user)}))

(gql/defresolver :Query :card
  "Fetches a single card by slug and set ID."
  [:=> [:cat :any [:map [:slug :string] [:setId :string]] :any]
   [:maybe GameCard]]
  [ctx {:keys [slug setId]} _value]
  (when-let [card (repo/find-by (:card-repo ctx)
                                {:slug slug
                                 :set-id (parse-uuid setId)})]
    (transform-card card)))

(gql/defresolver :Query :cards
  "Lists cards, optionally filtered by set ID and/or card type."
  [:=> [:cat :any [:map {:optional true}
                   [:setId {:optional true} :string]
                   [:cardType {:optional true} :string]] :any]
   [:vector GameCard]]
  [ctx args _value]
  (let [card-type-kw (when-let [ct (:cardType args)]
                       (keyword "card-type" ct))
        opts         (cond-> {}
                       (:setId args) (assoc-in [:where :set-id] (parse-uuid (:setId args)))
                       card-type-kw (assoc-in [:where :card-type] card-type-kw))]
    (mapv transform-card (repo/find-all (:card-repo ctx) opts))))

(gql/defresolver :Mutation :createPlayerCard
  "Creates a new player card."
  [:=> [:cat :any [:map [:setId :string] [:input schemas/PlayerCardInput]] :any]
   schemas/PlayerCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/PLAYER_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createAbilityCard
  "Creates a new ability card."
  [:=> [:cat :any [:map [:setId :string] [:input schemas/AbilityCardInput]] :any]
   schemas/AbilityCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/ABILITY_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createSplitPlayCard
  "Creates a new split play card."
  [:=> [:cat :any [:map [:setId :string] [:input schemas/SplitPlayCardInput]] :any]
   schemas/SplitPlayCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/SPLIT_PLAY_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createPlayCard
  "Creates a new play card."
  [:=> [:cat :any [:map [:setId :string] [:input schemas/PlayCardInput]] :any]
   schemas/PlayCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/PLAY_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createCoachingCard
  "Creates a new coaching card."
  [:=> [:cat :any [:map [:setId :string] [:input schemas/CoachingCardInput]] :any]
   schemas/CoachingCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/COACHING_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createStandardActionCard
  "Creates a new standard action card."
  [:=> [:cat :any [:map [:setId :string] [:input schemas/StandardActionCardInput]] :any]
   schemas/StandardActionCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/STANDARD_ACTION_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createTeamAssetCard
  "Creates a new team asset card."
  [:=> [:cat :any [:map [:setId :string] [:input schemas/TeamAssetCardInput]] :any]
   schemas/TeamAssetCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/TEAM_ASSET_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :updateCard
  "Updates an existing card.

  Looks up the card by slug and setId, then applies the update fields.
  Server validates the updated card against its type-specific schema."
  [:=> [:cat :any [:map [:slug :string]
                   [:setId :string]
                   [:input schemas/CardUpdateInput]] :any]
   GameCard]
  [ctx {:keys [slug setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :_user (get-user-context ctx)))]
    (transform-card (repo/update! (:card-repo ctx)
                                  {:slug slug}
                                  card-data))))

(gql/defresolver :Mutation :deleteCard
  "Deletes a card by slug and set ID."
  [:=> [:cat :any [:map [:slug :string] [:setId :string]] :any]
   :boolean]
  [ctx {:keys [slug setId]} _value]
  (git-cards/delete-card! (:card-repo ctx)
                          slug
                          (parse-uuid setId)
                          (get-user-context ctx)))

(def query-resolvers
  "Query resolvers for cards (no auth required for queries)."
  (gql/collect-resolvers 'bashketball-editor-api.graphql.resolvers.card))

(def mutation-resolvers
  "Mutation resolvers for cards (auth required)."
  (gql/apply-middleware [middleware/require-authentication]
                        (into {}
                              (filter (fn [[[obj _action] _]]
                                        (= obj :Mutation))
                                      query-resolvers))))

(def resolvers
  "Combined query and mutation resolvers for cards."
  (merge (into {}
               (filter (fn [[[obj _action] _]]
                         (= obj :Query))
                       query-resolvers))
         mutation-resolvers))
