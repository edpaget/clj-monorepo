(ns bashketball-editor-api.graphql.resolvers.card
  "GraphQL resolvers for card queries and mutations.

  Provides query and mutation resolvers for game cards with type-specific
  create operations and generic update/delete mutations."
  (:require
   [bashketball-editor-api.graphql.middleware :as middleware]
   [bashketball-editor-api.graphql.schemas.card :as schemas]
   [bashketball-editor-api.models.protocol :as repo]
   [graphql-server.core :as gql]))

(def GameCard
  "GraphQL output schema for game cards."
  schemas/GameCard)

(defn- transform-card
  "Transforms a card entity for GraphQL output.

  Converts timestamps to ISO strings."
  [card]
  (-> card
      (update :created-at #(when % (str %)))
      (update :updated-at #(when % (str %)))))

(gql/defresolver :Query :card
  "Fetches a single card by slug and set slug."
  [:=> [:cat :any [:map [:slug :string] [:setSlug :string]] :any]
   [:maybe GameCard]]
  [ctx {:keys [slug setSlug]} _value]
  (when-let [card (repo/find-by (:card-repo ctx)
                                {:slug slug
                                 :set-slug setSlug})]
    (transform-card card)))

(def ^:private default-limit 50)
(def ^:private max-limit 100)

(gql/defresolver :Query :cards
  "Lists cards with pagination, optionally filtered by set slug and/or card type.

  Pagination args:
  - `offset`: Number of cards to skip (default 0)
  - `limit`: Maximum cards to return (default 50, max 100)"
  [:=> [:cat :any [:map {:optional true}
                   [:setSlug {:optional true} :string]
                   [:cardType {:optional true} :string]
                   [:offset {:optional true} :int]
                   [:limit {:optional true} :int]] :any]
   schemas/CardsResponse]
  [ctx args _value]
  (let [card-type-kw (when-let [ct (:cardType args)]
                       (keyword "card-type" ct))
        opts         (cond-> {}
                       (:setSlug args) (assoc-in [:where :set-slug] (:setSlug args))
                       card-type-kw (assoc-in [:where :card-type] card-type-kw))
        all-cards    (repo/find-all (:card-repo ctx) opts)
        total        (count all-cards)
        offset       (or (:offset args) 0)
        limit        (min (or (:limit args) default-limit) max-limit)
        paginated    (->> all-cards
                          (drop offset)
                          (take limit)
                          (mapv transform-card))]
    {:data     paginated
     :pageInfo {:total   total
                :offset  offset
                :limit   limit
                :hasMore (< (+ offset (count paginated)) total)}}))

(gql/defresolver :Mutation :createPlayerCard
  "Creates a new player card."
  [:=> [:cat :any [:map [:setSlug :string] [:input schemas/PlayerCardInput]] :any]
   schemas/PlayerCard]
  [ctx {:keys [setSlug input]} _value]
  (let [card-data (-> input
                      (assoc :set-slug setSlug)
                      (assoc :card-type :card-type/PLAYER_CARD))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createAbilityCard
  "Creates a new ability card."
  [:=> [:cat :any [:map [:setSlug :string] [:input schemas/AbilityCardInput]] :any]
   schemas/AbilityCard]
  [ctx {:keys [setSlug input]} _value]
  (let [card-data (-> input
                      (assoc :set-slug setSlug)
                      (assoc :card-type :card-type/ABILITY_CARD))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createSplitPlayCard
  "Creates a new split play card."
  [:=> [:cat :any [:map [:setSlug :string] [:input schemas/SplitPlayCardInput]] :any]
   schemas/SplitPlayCard]
  [ctx {:keys [setSlug input]} _value]
  (let [card-data (-> input
                      (assoc :set-slug setSlug)
                      (assoc :card-type :card-type/SPLIT_PLAY_CARD))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createPlayCard
  "Creates a new play card."
  [:=> [:cat :any [:map [:setSlug :string] [:input schemas/PlayCardInput]] :any]
   schemas/PlayCard]
  [ctx {:keys [setSlug input]} _value]
  (let [card-data (-> input
                      (assoc :set-slug setSlug)
                      (assoc :card-type :card-type/PLAY_CARD))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createCoachingCard
  "Creates a new coaching card."
  [:=> [:cat :any [:map [:setSlug :string] [:input schemas/CoachingCardInput]] :any]
   schemas/CoachingCard]
  [ctx {:keys [setSlug input]} _value]
  (let [card-data (-> input
                      (assoc :set-slug setSlug)
                      (assoc :card-type :card-type/COACHING_CARD))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createStandardActionCard
  "Creates a new standard action card."
  [:=> [:cat :any [:map [:setSlug :string] [:input schemas/StandardActionCardInput]] :any]
   schemas/StandardActionCard]
  [ctx {:keys [setSlug input]} _value]
  (let [card-data (-> input
                      (assoc :set-slug setSlug)
                      (assoc :card-type :card-type/STANDARD_ACTION_CARD))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :createTeamAssetCard
  "Creates a new team asset card."
  [:=> [:cat :any [:map [:setSlug :string] [:input schemas/TeamAssetCardInput]] :any]
   schemas/TeamAssetCard]
  [ctx {:keys [setSlug input]} _value]
  (let [card-data (-> input
                      (assoc :set-slug setSlug)
                      (assoc :card-type :card-type/TEAM_ASSET_CARD))]
    (transform-card (repo/create! (:card-repo ctx) card-data))))

(gql/defresolver :Mutation :updateCard
  "Updates an existing card.

  Looks up the card by slug and setSlug, then applies the update fields.
  Server validates the updated card against its type-specific schema."
  [:=> [:cat :any [:map [:slug :string]
                   [:setSlug :string]
                   [:input schemas/CardUpdateInput]] :any]
   GameCard]
  [ctx {:keys [slug setSlug input]} _value]
  (transform-card (repo/update! (:card-repo ctx)
                                {:slug slug :set-slug setSlug}
                                input)))

(gql/defresolver :Mutation :deleteCard
  "Deletes a card by slug and set slug."
  [:=> [:cat :any [:map [:slug :string] [:setSlug :string]] :any]
   :boolean]
  [ctx {:keys [slug setSlug]} _value]
  (repo/delete! (:card-repo ctx)
                {:slug slug :set-slug setSlug}))

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
