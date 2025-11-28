(ns bashketball-editor-api.services.set
  "Card set business logic service.

  Implements business rules and validation for card set operations, including
  slug generation and Malli schema validation before repository operations.

  Uses [[bashketball-editor-api.graphql.schemas.card/CardSetInput]] as the base
  schema, adding optional `:slug` field for service-layer input."
  (:require
   [bashketball-editor-api.graphql.schemas.card :as gql-schemas]
   [bashketball-editor-api.models.protocol :as repo]
   [bashketball-editor-api.util.slug :as slug]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]))

(def CardSetInput
  "Input schema for card set creation.

  Derives from GraphQL [[gql-schemas/CardSetInput]], adding optional `:slug`
  field which can be provided or auto-generated from `:name`."
  (mu/merge
   gql-schemas/CardSetInput
   [:map [:slug {:optional true} [:maybe :string]]]))

(defn- validate-input!
  "Validates input against a schema, throwing on failure."
  [schema data context]
  (when-not (m/validate schema data)
    (throw (ex-info "Invalid card set input"
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

(defrecord SetService [set-repo card-repo])

(defn find-by
  "Finds a card set by criteria."
  [{:keys [set-repo]} criteria]
  (repo/find-by set-repo criteria))

(defn find-all
  "Finds all card sets."
  [{:keys [set-repo]} opts]
  (repo/find-all set-repo opts))

(defn create-set!
  "Creates a new card set with validation and slug generation.

  Validates the input against the CardSetInput schema, generates a slug from
  the name if not provided, validates the slug is URL-safe, then delegates
  to the repository."
  [{:keys [set-repo]} data]
  (let [_         (validate-input! CardSetInput data {:operation :create-set})
        validated (ensure-slug data)]
    (repo/create! set-repo validated)))

(defn update-set!
  "Updates an existing card set."
  [{:keys [set-repo]} slug data]
  (repo/update! set-repo slug data))

(defn delete-set!
  "Deletes a card set."
  [{:keys [set-repo]} slug]
  (repo/delete! set-repo slug))

(defn create-set-service
  "Creates a set service instance."
  [set-repo card-repo]
  (->SetService set-repo card-repo))
