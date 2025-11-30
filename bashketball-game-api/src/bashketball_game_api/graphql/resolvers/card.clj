(ns bashketball-game-api.graphql.resolvers.card
  "GraphQL resolvers for card and set queries.

  Provides Query resolvers for browsing the card catalog, including listing
  all cards/sets, filtering by set or type, and individual card/set lookup."
  (:require
   [bashketball-game-api.services.catalog :as catalog]
   [bashketball-schemas.card :as card-schema]
   [graphql-server.core :refer [defresolver def-resolver-map]]))

(def CardSet
  "GraphQL schema for CardSet type."
  [:map {:graphql/type :CardSet}
   [:slug :string]
   [:name :string]
   [:description {:optional true} [:maybe :string]]])

(defresolver :Query :sets
  "Returns all available card sets."
  [:=> [:cat :any :any :any] [:vector CardSet]]
  [ctx _args _value]
  (let [card-catalog (get-in ctx [:request :resolver-map :card-catalog])]
    (vec (catalog/get-sets card-catalog))))

(defresolver :Query :set
  "Returns a single card set by slug."
  [:=> [:cat :any [:map [:slug :string]] :any] [:maybe CardSet]]
  [ctx {:keys [slug]} _value]
  (let [card-catalog (get-in ctx [:request :resolver-map :card-catalog])]
    (catalog/get-set card-catalog slug)))

(defresolver :Query :cards
  "Returns cards, optionally filtered by set slug."
  [:=> [:cat :any [:map [:setSlug {:optional true} [:maybe :string]]] :any]
   [:vector card-schema/Card]]
  [ctx {:keys [setSlug]} _value]
  (let [card-catalog (get-in ctx [:request :resolver-map :card-catalog])]
    (if setSlug
      (vec (catalog/get-cards-by-set card-catalog setSlug))
      (vec (catalog/get-cards card-catalog)))))

(defresolver :Query :card
  "Returns a single card by slug."
  [:=> [:cat :any [:map [:slug :string]] :any] [:maybe card-schema/Card]]
  [ctx {:keys [slug]} _value]
  (let [card-catalog (get-in ctx [:request :resolver-map :card-catalog])]
    (catalog/get-card card-catalog slug)))

(def-resolver-map)
