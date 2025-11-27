(ns bashketball-editor-ui.schemas.card
  "Malli schemas for Bashketball card types.

  Defines validation schemas for all seven card types used in the game,
  matching the backend EDN format."
  (:require
   [malli.core :as m]))

(def CardType
  "Enum of all valid card types."
  [:enum
   :card-type/PLAYER_CARD
   :card-type/ABILITY_CARD
   :card-type/SPLIT_PLAY_CARD
   :card-type/PLAY_CARD
   :card-type/COACHING_CARD
   :card-type/STANDARD_ACTION_CARD
   :card-type/TEAM_ASSET_CARD])

(def Size
  "Enum of player sizes."
  [:enum
   :size/XS
   :size/SM
   :size/MD
   :size/LG
   :size/XL])

(def BaseCard
  "Schema for fields common to all card types."
  [:map
   [:name :string]
   [:card-type CardType]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]])

(def PlayerCard
  "Schema for player character cards with stats and abilities."
  [:map
   [:name :string]
   [:card-type [:= :card-type/PLAYER_CARD]]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]
   [:sht [:int {:min 1 :max 10}]]
   [:pss [:int {:min 1 :max 10}]]
   [:def [:int {:min 1 :max 10}]]
   [:speed [:int {:min 1 :max 10}]]
   [:size Size]
   [:deck-size [:int {:min 1 :max 10}]]
   [:abilities [:vector :string]]])

(def AbilityCard
  "Schema for ability cards."
  [:map
   [:name :string]
   [:card-type [:= :card-type/ABILITY_CARD]]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]
   [:abilities [:vector :string]]])

(def SplitPlayCard
  "Schema for dual-use play cards with offense and defense text."
  [:map
   [:name :string]
   [:card-type [:= :card-type/SPLIT_PLAY_CARD]]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]
   [:fate [:int {:min 0 :max 10}]]
   [:offense :string]
   [:defense :string]])

(def PlayCard
  "Schema for action play cards."
  [:map
   [:name :string]
   [:card-type [:= :card-type/PLAY_CARD]]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]
   [:fate [:int {:min 0 :max 10}]]
   [:play :string]])

(def CoachingCard
  "Schema for coaching action cards."
  [:map
   [:name :string]
   [:card-type [:= :card-type/COACHING_CARD]]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]
   [:fate [:int {:min 0 :max 10}]]
   [:coaching :string]])

(def StandardActionCard
  "Schema for standard action cards with offense/defense text."
  [:map
   [:name :string]
   [:card-type [:= :card-type/STANDARD_ACTION_CARD]]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]
   [:fate [:int {:min 0 :max 10}]]
   [:offense :string]
   [:defense :string]])

(def TeamAssetCard
  "Schema for team enhancement cards."
  [:map
   [:name :string]
   [:card-type [:= :card-type/TEAM_ASSET_CARD]]
   [:version {:optional true :default "0"} :string]
   [:game-asset-id {:optional true} :uuid]
   [:image-prompt {:optional true} :string]
   [:fate [:int {:min 0 :max 10}]]
   [:asset-power :string]])

(def GameCard
  "Multi-schema that validates any card type based on the :card-type field."
  [:multi {:dispatch :card-type}
   [:card-type/PLAYER_CARD PlayerCard]
   [:card-type/ABILITY_CARD AbilityCard]
   [:card-type/SPLIT_PLAY_CARD SplitPlayCard]
   [:card-type/PLAY_CARD PlayCard]
   [:card-type/COACHING_CARD CoachingCard]
   [:card-type/STANDARD_ACTION_CARD StandardActionCard]
   [:card-type/TEAM_ASSET_CARD TeamAssetCard]])

(defn valid?
  "Returns true if the card matches its schema based on card-type."
  [card]
  (m/validate GameCard card))

(defn explain
  "Returns explanation of validation errors, or nil if valid."
  [card]
  (m/explain GameCard card))
