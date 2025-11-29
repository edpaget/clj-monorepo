(ns bashketball-schemas.core
  "Re-exports all schemas for convenient single-namespace access.

   Import this namespace to get all shared schemas:

   ```clojure
   (require '[bashketball-schemas.core :as schemas])

   (m/validate schemas/Card my-card)
   (m/validate schemas/User my-user)
   ```

   For more granular imports, use the specific namespaces:
   - [[bashketball-schemas.enums]] - Enum definitions
   - [[bashketball-schemas.card]] - Card type schemas
   - [[bashketball-schemas.user]] - User schema"
  (:require [bashketball-schemas.enums :as enums]
            [bashketball-schemas.card :as card]
            [bashketball-schemas.user :as user]))

;; =============================================================================
;; Enums
;; =============================================================================

(def CardType
  "Card type enumeration. See [[enums/CardType]]."
  enums/CardType)

(def Size
  "Player size enumeration. See [[enums/Size]]."
  enums/Size)

(def GameStatus
  "Game lifecycle status. See [[enums/GameStatus]]."
  enums/GameStatus)

(def Team
  "Team identifier (HOME/AWAY). See [[enums/Team]]."
  enums/Team)

(def GamePhase
  "Turn phase enumeration. See [[enums/GamePhase]]."
  enums/GamePhase)

(def BallStatus
  "Ball state enumeration. See [[enums/BallStatus]]."
  enums/BallStatus)

(def BallActionType
  "Ball movement type (SHOT/PASS). See [[enums/BallActionType]]."
  enums/BallActionType)

;; Enum utility functions
(def enum-values
  "Extract values from enum schema. See [[enums/enum-values]]."
  enums/enum-values)

(def enum->options
  "Convert enum to UI select options. See [[enums/enum->options]]."
  enums/enum->options)

;; =============================================================================
;; Card Schemas
;; =============================================================================

(def Card
  "Multi-schema for any card type. See [[card/Card]]."
  card/Card)

(def PlayerCard
  "Player card schema. See [[card/PlayerCard]]."
  card/PlayerCard)

(def AbilityCard
  "Ability card schema. See [[card/AbilityCard]]."
  card/AbilityCard)

(def PlayCard
  "Play card schema. See [[card/PlayCard]]."
  card/PlayCard)

(def StandardActionCard
  "Standard action card schema. See [[card/StandardActionCard]]."
  card/StandardActionCard)

(def SplitPlayCard
  "Split play card schema. See [[card/SplitPlayCard]]."
  card/SplitPlayCard)

(def CoachingCard
  "Coaching card schema. See [[card/CoachingCard]]."
  card/CoachingCard)

(def TeamAssetCard
  "Team asset card schema. See [[card/TeamAssetCard]]."
  card/TeamAssetCard)

(def CardSet
  "Card set metadata schema. See [[card/CardSet]]."
  card/CardSet)

(def Slug
  "Slug identifier schema. See [[card/Slug]]."
  card/Slug)

(def card-type->schema
  "Map from card type to schema. See [[card/card-type->schema]]."
  card/card-type->schema)

;; =============================================================================
;; User Schemas
;; =============================================================================

(def User
  "User schema. See [[user/User]]."
  user/User)

(def Email
  "Email address schema. See [[user/Email]]."
  user/Email)

(def GoogleId
  "Google OAuth ID schema. See [[user/GoogleId]]."
  user/GoogleId)

(def GitHubId
  "GitHub user ID schema. See [[user/GitHubId]]."
  user/GitHubId)
