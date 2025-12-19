(ns bashketball-schemas.card
  "Card schemas matching the cards JAR structure.

   Defines Malli schemas for all Bashketball card types. Cards are stored
   as EDN files in a Git repository and packaged as a JAR for distribution.

   The [[Card]] multi-schema dispatches on `:card-type` to validate against
   the appropriate type-specific schema."
  (:require
   [bashketball-schemas.enums :as enums]
   [bashketball-schemas.types :as types]
   [malli.util :as mu]))

(def Slug
  "Card or set slug identifier.

   Slugs are URL-safe strings derived from names: lowercase, alphanumeric
   with hyphens. Used as primary keys within sets."
  [:string {:min 1 :max 100}])

(def BaseCard
  "Common fields shared by all card types.

   - `:slug` - Unique identifier within a set
   - `:name` - Display name
   - `:set-slug` - Parent set identifier
   - `:card-type` - Discriminator for multi-schema dispatch
   - `:image-prompt` - Optional AI image generation prompt
   - `:card-subtypes` - Optional vector of card subtype classifications"
  [:map {:graphql/interface :Card}
   [:slug Slug]
   [:name [:string {:min 1 :max 255}]]
   [:set-slug Slug]
   [:card-type enums/CardType]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-subtypes {:optional true} [:vector enums/CardSubtype]]
   [:created-at {:optional true} [:maybe types/DateTime]]
   [:updated-at {:optional true} [:maybe types/DateTime]]])

(def PlayerCardFields
  "Fields specific to player cards.

   Stats range from 1-10. Size affects gameplay mechanics.
   Abilities are string identifiers for special powers.
   Player subtypes indicate the fantasy creature type."
  [:map
   [:sht :int]
   [:pss :int]
   [:def :int]
   [:speed :int]
   [:size enums/Size]
   [:abilities [:vector :string]]
   [:player-subtypes [:vector {:min 1} enums/PlayerSubtype]]
   [:deck-size {:optional true} :int]])

(def AbilityCardFields
  "Fields specific to ability cards.

  `:removable` indicates whether the card can be detached after being attached
  to a player. Defaults to true if omitted.

  `:detach-destination` specifies where the card goes when detached:
  `:detach/DISCARD` (default) or `:detach/REMOVED`."
  [:map
   [:fate :int]
   [:abilities [:vector :string]]
   [:removable {:optional true} :boolean]
   [:detach-destination {:optional true} [:enum {:graphql/type :DetachDestination} :detach/DISCARD :detach/REMOVED]]])

(def PlayCardFields
  "Fields specific to play cards."
  [:map
   [:fate :int]
   [:play :string]])

(def ActionCardFields
  "Fields specific to standard action cards."
  [:map
   [:fate :int]
   [:offense {:optional true} [:maybe :string]]
   [:defense {:optional true} [:maybe :string]]])

(def SplitPlayCardFields
  "Fields specific to split play cards."
  [:map
   [:fate :int]
   [:offense :string]
   [:defense :string]])

(def CoachingCardFields
  "Fields specific to coaching cards.

   `:signal` is an optional effect triggered during the signal phase."
  [:map
   [:fate :int]
   [:coaching :string]
   [:signal {:optional true} [:maybe :string]]])

(def TeamAssetCardFields
  "Fields specific to team asset cards."
  [:map
   [:fate {:optional true} :int]
   [:asset-power :string]])

(def PlayerCard
  "Schema for player cards representing basketball players."
  (mu/update-properties
   (mu/merge BaseCard PlayerCardFields)
   assoc :graphql/type :PlayerCard))

(def AbilityCard
  "Schema for ability cards providing special powers."
  (mu/update-properties
   (mu/merge BaseCard AbilityCardFields)
   assoc :graphql/type :AbilityCard))

(def PlayCard
  "Schema for play cards describing single actions."
  (mu/update-properties
   (mu/merge BaseCard PlayCardFields)
   assoc :graphql/type :PlayCard))

(def StandardActionCard
  "Schema for standard action cards available to all players."
  (mu/update-properties
   (mu/merge BaseCard ActionCardFields)
   assoc :graphql/type :StandardActionCard))

(def SplitPlayCard
  "Schema for split play cards with offense/defense effects."
  (mu/update-properties
   (mu/merge BaseCard SplitPlayCardFields)
   assoc :graphql/type :SplitPlayCard))

(def CoachingCard
  "Schema for coaching cards with team-wide effects."
  (mu/update-properties
   (mu/merge BaseCard CoachingCardFields)
   assoc :graphql/type :CoachingCard))

(def TeamAssetCard
  "Schema for team asset cards representing persistent resources."
  (mu/update-properties
   (mu/merge BaseCard TeamAssetCardFields)
   assoc :graphql/type :TeamAssetCard))

(def Card
  "Multi-schema for any card type, dispatched on `:card-type`.

   Validates cards against their type-specific schema based on the
   `:card-type` field value. Exposed as `GameCard` union in GraphQL."
  [:multi {:dispatch :card-type
           :graphql/type :GameCard}
   [:card-type/PLAYER_CARD PlayerCard]
   [:card-type/ABILITY_CARD AbilityCard]
   [:card-type/PLAY_CARD PlayCard]
   [:card-type/STANDARD_ACTION_CARD StandardActionCard]
   [:card-type/SPLIT_PLAY_CARD SplitPlayCard]
   [:card-type/COACHING_CARD CoachingCard]
   [:card-type/TEAM_ASSET_CARD TeamAssetCard]])

(def card-type->schema
  "Map from card type keyword to its Malli schema."
  {:card-type/PLAYER_CARD PlayerCard
   :card-type/ABILITY_CARD AbilityCard
   :card-type/PLAY_CARD PlayCard
   :card-type/STANDARD_ACTION_CARD StandardActionCard
   :card-type/SPLIT_PLAY_CARD SplitPlayCard
   :card-type/COACHING_CARD CoachingCard
   :card-type/TEAM_ASSET_CARD TeamAssetCard})

(def CardSet
  "Schema for card set metadata.

   Sets group related cards together. The `:slug` is used for
   directory names and URL paths."
  [:map {:graphql/type :CardSet}
   [:slug Slug]
   [:name [:string {:min 1 :max 255}]]
   [:description {:optional true} [:maybe :string]]
   [:created-at {:optional true} [:maybe types/DateTime]]
   [:updated-at {:optional true} [:maybe types/DateTime]]])
