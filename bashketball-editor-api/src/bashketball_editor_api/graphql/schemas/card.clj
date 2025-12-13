(ns bashketball-editor-api.graphql.schemas.card
  "GraphQL card schemas for the editor API.

   Re-exports card schemas from [[bashketball-schemas.card]] which include
   GraphQL metadata. Defines API-specific input schemas and response wrappers."
  (:require
   [bashketball-schemas.card :as base-card]
   [bashketball-schemas.enums :as enums]
   [malli.core :as m]
   [malli.util :as mu]))

;; =============================================================================
;; Re-exports from bashketball-schemas (with GraphQL annotations)
;; =============================================================================

(def CardType enums/CardType)
(def PlayerSize enums/Size)
(def CardSubtype enums/CardSubtype)
(def PlayerSubtype enums/PlayerSubtype)

(def BaseCard base-card/BaseCard)
(def Card base-card/Card)
(def GameCard base-card/Card)
(def PlayerCard base-card/PlayerCard)
(def AbilityCard base-card/AbilityCard)
(def PlayCard base-card/PlayCard)
(def StandardActionCard base-card/StandardActionCard)
(def SplitPlayCard base-card/SplitPlayCard)
(def CoachingCard base-card/CoachingCard)
(def TeamAssetCard base-card/TeamAssetCard)
(def CardSet base-card/CardSet)
(def card-type->schema base-card/card-type->schema)

;; =============================================================================
;; Input schemas (GraphQL mutation inputs - API-specific)
;; =============================================================================

(def PlayerCardInput
  "Input schema for creating/updating player cards."
  [:map {:graphql/type :PlayerCardInput}
   [:slug {:optional true} [:maybe :string]]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:deck-size {:optional true} :int]
   [:sht {:optional true} :int]
   [:pss {:optional true} :int]
   [:def {:optional true} :int]
   [:speed {:optional true} :int]
   [:size {:optional true} PlayerSize]
   [:abilities {:optional true} [:vector :string]]
   [:player-subtypes [:vector {:min 1} PlayerSubtype]]])

(def AbilityCardInput
  "Input schema for creating/updating ability cards."
  [:map {:graphql/type :AbilityCardInput}
   [:slug {:optional true} [:maybe :string]]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:fate {:optional true} :int]
   [:abilities {:optional true} [:vector :string]]])

(def SplitPlayCardInput
  "Input schema for creating/updating split play cards."
  [:map {:graphql/type :SplitPlayCardInput}
   [:slug {:optional true} [:maybe :string]]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

(def PlayCardInput
  "Input schema for creating/updating play cards."
  [:map {:graphql/type :PlayCardInput}
   [:slug {:optional true} [:maybe :string]]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:fate {:optional true} :int]
   [:play {:optional true} :string]])

(def CoachingCardInput
  "Input schema for creating/updating coaching cards."
  [:map {:graphql/type :CoachingCardInput}
   [:slug {:optional true} [:maybe :string]]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:fate {:optional true} :int]
   [:coaching {:optional true} :string]])

(def StandardActionCardInput
  "Input schema for creating/updating standard action cards."
  [:map {:graphql/type :StandardActionCardInput}
   [:slug {:optional true} [:maybe :string]]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

(def TeamAssetCardInput
  "Input schema for creating/updating team asset cards."
  [:map {:graphql/type :TeamAssetCardInput}
   [:slug {:optional true} [:maybe :string]]
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
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
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:deck-size {:optional true} :int]
   [:sht {:optional true} :int]
   [:pss {:optional true} :int]
   [:def {:optional true} :int]
   [:speed {:optional true} :int]
   [:size {:optional true} PlayerSize]
   [:abilities {:optional true} [:vector :string]]
   [:player-subtypes {:optional true} [:vector {:min 1} PlayerSubtype]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]
   [:play {:optional true} :string]
   [:coaching {:optional true} :string]
   [:asset-power {:optional true} :string]])

;; =============================================================================
;; Response wrappers (GraphQL-specific)
;; =============================================================================

(def PageInfo
  "Pagination metadata."
  [:map {:graphql/type :PageInfo}
   [:total :int]
   [:offset :int]
   [:limit :int]
   [:has-more :boolean]])

(def CardsResponse
  "Response wrapper for card list queries with pagination."
  [:map {:graphql/type :CardsResponse}
   [:data [:vector GameCard]]
   [:page-info PageInfo]])

(def CardSetsResponse
  "Response wrapper for card set list queries."
  [:map {:graphql/type :CardSetsResponse}
   [:data [:vector CardSet]]])

;; =============================================================================
;; Internal schemas (for storage with inst timestamps)
;; =============================================================================

(def InternalCard
  "Internal schema for card storage (with inst timestamps)."
  [:map
   [:slug :string]
   [:name :string]
   [:set-slug :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector CardSubtype]]
   [:card-type CardType]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(def InternalPlayerCard
  "Internal schema for player cards with inst timestamps."
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
    [:abilities [:vector :string]]
    [:player-subtypes [:vector {:min 1} PlayerSubtype]]]))

(def internal-card-type->schema
  "Map from card type enum to its internal schema."
  {:card-type/PLAYER_CARD InternalPlayerCard
   :card-type/ABILITY_CARD AbilityCard
   :card-type/SPLIT_PLAY_CARD SplitPlayCard
   :card-type/PLAY_CARD PlayCard
   :card-type/COACHING_CARD CoachingCard
   :card-type/STANDARD_ACTION_CARD StandardActionCard
   :card-type/TEAM_ASSET_CARD TeamAssetCard})

;; =============================================================================
;; Validation functions
;; =============================================================================

(defn validate-card
  "Validates a card against its type-specific schema."
  [card]
  (let [card-type (:card-type card)
        schema    (get card-type->schema card-type)]
    (if schema
      (m/validate schema card)
      false)))

(defn explain-card
  "Returns validation errors for a card."
  [card]
  (let [card-type (:card-type card)
        schema    (get card-type->schema card-type)]
    (when schema
      (m/explain schema card))))
