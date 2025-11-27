(ns bashketball-editor-api.graphql.schemas.card
  "Malli schemas for Bashketball game cards.

  Defines the type system for all card types in the game, including:
  - PlayerCard: Basketball player cards with stats
  - AbilityCard: Special ability cards
  - PlayCard: Single play action cards
  - SplitPlayCard: Cards with offense/defense effects
  - CoachingCard: Team-wide effect cards
  - StandardActionCard: Cards available to all players
  - TeamAssetCard: Persistent team resource cards

  Cards are stored as EDN files in a Git repository and use `:name` as
  the primary key within a set."
  (:require
   [malli.core :as m]
   [malli.util :as mu]))

(def CardType
  "Enum for card type discriminator."
  [:enum {:graphql/type :CardType}
   :card-type/PLAYER_CARD
   :card-type/ABILITY_CARD
   :card-type/SPLIT_PLAY_CARD
   :card-type/PLAY_CARD
   :card-type/COACHING_CARD
   :card-type/STANDARD_ACTION_CARD
   :card-type/TEAM_ASSET_CARD])

(def PlayerSize
  "Enum for player sizes."
  [:enum {:graphql/type :PlayerSize}
   :size/SM
   :size/MD
   :size/LG])

(def Card
  "Base card schema shared by all card types.

  Cards use `slug` as the primary key within a set. The slug is a URL-safe
  version of the name (lowercase, alphanumeric with hyphens)."
  [:map {:graphql/interface :Card}
   [:slug :string]
   [:name :string]
   [:set-id {:graphql/hidden true} :uuid]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-type CardType]
   [:created-at {:optional true} [:maybe :string]]
   [:updated-at {:optional true} [:maybe :string]]])

(def PlayerCard
  "Player cards represent basketball players with stats."
  (mu/merge
   Card
   [:map {:graphql/type :PlayerCard}
    [:card-type [:= :card-type/PLAYER_CARD]]
    [:deck-size :int]
    [:sht :int]
    [:pss :int]
    [:def :int]
    [:speed :int]
    [:size PlayerSize]
    [:abilities [:vector :string]]]))

(def AbilityCard
  "Ability cards provide special powers."
  (mu/merge
   Card
   [:map {:graphql/type :AbilityCard}
    [:card-type [:= :card-type/ABILITY_CARD]]
    [:abilities [:vector :string]]]))

(def CardWithFate
  "Intermediate schema for cards with fate values."
  (mu/merge
   Card
   [:map
    [:fate :int]]))

(def SplitPlayCard
  "Split play cards have both offense and defense effects."
  (mu/merge
   CardWithFate
   [:map {:graphql/type :SplitPlayCard}
    [:card-type [:= :card-type/SPLIT_PLAY_CARD]]
    [:offense :string]
    [:defense :string]]))

(def PlayCard
  "Play cards describe a single play action."
  (mu/merge
   CardWithFate
   [:map {:graphql/type :PlayCard}
    [:card-type [:= :card-type/PLAY_CARD]]
    [:play :string]]))

(def CoachingCard
  "Coaching cards provide team-wide effects."
  (mu/merge
   CardWithFate
   [:map {:graphql/type :CoachingCard}
    [:card-type [:= :card-type/COACHING_CARD]]
    [:coaching :string]]))

(def StandardActionCard
  "Standard action cards available to all players."
  (mu/merge
   CardWithFate
   [:map {:graphql/type :StandardActionCard}
    [:card-type [:= :card-type/STANDARD_ACTION_CARD]]
    [:offense :string]
    [:defense :string]]))

(def TeamAssetCard
  "Team asset cards represent persistent team resources."
  (mu/merge
   CardWithFate
   [:map {:graphql/type :TeamAssetCard}
    [:card-type [:= :card-type/TEAM_ASSET_CARD]]
    [:asset-power :string]]))

(def GameCard
  "Union type for all card types, dispatched by :card-type."
  [:multi {:dispatch :card-type
           :graphql/type :GameCard}
   [:card-type/PLAYER_CARD PlayerCard]
   [:card-type/ABILITY_CARD AbilityCard]
   [:card-type/SPLIT_PLAY_CARD SplitPlayCard]
   [:card-type/PLAY_CARD PlayCard]
   [:card-type/COACHING_CARD CoachingCard]
   [:card-type/STANDARD_ACTION_CARD StandardActionCard]
   [:card-type/TEAM_ASSET_CARD TeamAssetCard]])

(def card-type->schema
  "Map from card type enum to its schema."
  {:card-type/PLAYER_CARD PlayerCard
   :card-type/ABILITY_CARD AbilityCard
   :card-type/SPLIT_PLAY_CARD SplitPlayCard
   :card-type/PLAY_CARD PlayCard
   :card-type/COACHING_CARD CoachingCard
   :card-type/STANDARD_ACTION_CARD StandardActionCard
   :card-type/TEAM_ASSET_CARD TeamAssetCard})

(def CardSet
  "Schema for card set metadata."
  [:map {:graphql/type :CardSet}
   [:id :string]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:created-at {:optional true} [:maybe :string]]
   [:updated-at {:optional true} [:maybe :string]]])

(def PlayerCardInput
  "Input schema for creating/updating player cards."
  [:map {:graphql/type :PlayerCardInput}
   [:slug :string]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:deck-size {:optional true} :int]
   [:sht {:optional true} :int]
   [:pss {:optional true} :int]
   [:def {:optional true} :int]
   [:speed {:optional true} :int]
   [:size {:optional true} PlayerSize]
   [:abilities {:optional true} [:vector :string]]])

(def AbilityCardInput
  "Input schema for creating/updating ability cards."
  [:map {:graphql/type :AbilityCardInput}
   [:slug :string]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:abilities {:optional true} [:vector :string]]])

(def SplitPlayCardInput
  "Input schema for creating/updating split play cards."
  [:map {:graphql/type :SplitPlayCardInput}
   [:slug :string]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

(def PlayCardInput
  "Input schema for creating/updating play cards."
  [:map {:graphql/type :PlayCardInput}
   [:slug :string]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:fate {:optional true} :int]
   [:play {:optional true} :string]])

(def CoachingCardInput
  "Input schema for creating/updating coaching cards."
  [:map {:graphql/type :CoachingCardInput}
   [:slug :string]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:fate {:optional true} :int]
   [:coaching {:optional true} :string]])

(def StandardActionCardInput
  "Input schema for creating/updating standard action cards."
  [:map {:graphql/type :StandardActionCardInput}
   [:slug :string]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

(def TeamAssetCardInput
  "Input schema for creating/updating team asset cards."
  [:map {:graphql/type :TeamAssetCardInput}
   [:slug :string]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:fate {:optional true} :int]
   [:asset-power {:optional true} :string]])

(def CardSetInput
  "Input schema for creating/updating card sets."
  [:map {:graphql/type :CardSetInput}
   [:name :string]
   [:description {:optional true} [:maybe :string]]])

(def CardUpdateInput
  "Input schema for generic card updates.

  Note: slug cannot be updated as it's the primary key."
  [:map {:graphql/type :CardUpdateInput}
   [:name {:optional true} :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:deck-size {:optional true} :int]
   [:sht {:optional true} :int]
   [:pss {:optional true} :int]
   [:def {:optional true} :int]
   [:speed {:optional true} :int]
   [:size {:optional true} PlayerSize]
   [:abilities {:optional true} [:vector :string]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]
   [:play {:optional true} :string]
   [:coaching {:optional true} :string]
   [:asset-power {:optional true} :string]])

(def InternalCard
  "Internal schema for card storage (with UUID set-id and inst timestamps)."
  [:map
   [:slug :string]
   [:name :string]
   [:set-id :uuid]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-type CardType]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(def InternalPlayerCard
  "Internal schema for player cards."
  (mu/merge
   InternalCard
   [:map
    [:card-type [:= :card-type/PLAYER_CARD]]
    [:deck-size :int]
    [:sht :int]
    [:pss :int]
    [:def :int]
    [:speed :int]
    [:size PlayerSize]
    [:abilities [:vector :string]]]))

(def internal-card-type->schema
  "Map from card type enum to its internal schema."
  {:card-type/PLAYER_CARD InternalPlayerCard
   :card-type/ABILITY_CARD AbilityCard
   :card-type/SPLIT_PLAY_CARD SplitPlayCard
   :card-type/PLAY_CARD PlayCard
   :card-type/COACHING_CARD CoachingCard
   :card-type/STANDARD_ACTION_CARD StandardActionCard
   :card-type/TEAM_ASSET_CARD TeamAssetCard})

(defn validate-card
  "Validates a card against its type-specific schema."
  [card]
  (let [card-type (:card-type card)
        schema (get card-type->schema card-type)]
    (if schema
      (m/validate schema card)
      false)))

(defn explain-card
  "Returns validation errors for a card."
  [card]
  (let [card-type (:card-type card)
        schema (get card-type->schema card-type)]
    (when schema
      (m/explain schema card))))
