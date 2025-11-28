(ns bashketball-editor-api.services.card
  "Card business logic service.

  Implements business rules and validation for card operations, including
  slug generation and Malli schema validation before repository operations.

  Uses schemas from [[bashketball-editor-api.graphql.schemas.card]] as the
  base, adding `:set-slug` for service-layer validation."
  (:require
   [bashketball-editor-api.graphql.schemas.card :as gql-schemas]
   [bashketball-editor-api.models.protocol :as repo]
   [bashketball-editor-api.util.slug :as slug]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]))

(def ^:private set-slug-field
  "Schema fragment for set-slug field, added to card inputs for service validation."
  [:map [:set-slug :string]])

(defn- with-set-slug
  "Adds :set-slug field to a GraphQL input schema for service-layer validation."
  [schema]
  (mu/merge schema set-slug-field))

(def card-type->input-schema
  "Map from card type to its input schema.

  Derives from GraphQL schemas, adding `:set-slug` which is provided by
  the resolver but not part of the GraphQL input type."
  {:card-type/PLAYER_CARD (with-set-slug gql-schemas/PlayerCardInput)
   :card-type/ABILITY_CARD (with-set-slug gql-schemas/AbilityCardInput)
   :card-type/SPLIT_PLAY_CARD (with-set-slug gql-schemas/SplitPlayCardInput)
   :card-type/PLAY_CARD (with-set-slug gql-schemas/PlayCardInput)
   :card-type/COACHING_CARD (with-set-slug gql-schemas/CoachingCardInput)
   :card-type/STANDARD_ACTION_CARD (with-set-slug gql-schemas/StandardActionCardInput)
   :card-type/TEAM_ASSET_CARD (with-set-slug gql-schemas/TeamAssetCardInput)})

(def BaseCardInput
  "Fallback input schema for unknown card types."
  (with-set-slug
    [:map
     [:slug {:optional true} [:maybe :string]]
     [:name :string]
     [:image-prompt {:optional true} [:maybe :string]]]))

(defn- validate-input!
  "Validates input against a schema, throwing on failure."
  [schema data context]
  (when-not (m/validate schema data)
    (throw (ex-info "Invalid card input"
                    {:context context
                     :errors (me/humanize (m/explain schema data))})))
  data)

(defn- ensure-slug
  "Ensures data has a valid slug, generating from name if not provided."
  [data]
  (let [provided-slug  (:slug data)
        generated-slug (when (or (nil? provided-slug) (empty? provided-slug))
                         (slug/slugify (:name data)))
        final-slug     (or provided-slug generated-slug)]
    (slug/validate-slug! final-slug {:name (:name data)
                                     :provided-slug provided-slug})
    (assoc data :slug final-slug)))

(defrecord CardService [card-repo])

(defn find-by
  "Finds a card by criteria."
  [{:keys [card-repo]} criteria]
  (repo/find-by card-repo criteria))

(defn find-all
  "Finds all cards matching options."
  [{:keys [card-repo]} opts]
  (repo/find-all card-repo opts))

(defn create-card!
  "Creates a new card with validation and slug generation.

  Validates the input against the appropriate schema for the card type,
  generates a slug from the name if not provided, validates the slug is
  URL-safe, then delegates to the repository."
  [{:keys [card-repo]} card-type data]
  (let [schema    (get card-type->input-schema card-type BaseCardInput)
        _         (validate-input! schema data {:card-type card-type})
        validated (-> data
                      ensure-slug
                      (assoc :card-type card-type))]
    (repo/create! card-repo validated)))

(defn update-card!
  "Updates an existing card."
  [{:keys [card-repo]} criteria data]
  (repo/update! card-repo criteria data))

(defn delete-card!
  "Deletes a card."
  [{:keys [card-repo]} id]
  (repo/delete! card-repo id))

(defn create-card-service
  "Creates a card service instance."
  [card-repo]
  (->CardService card-repo))
