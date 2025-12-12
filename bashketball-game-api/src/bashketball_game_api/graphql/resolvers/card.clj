(ns bashketball-game-api.graphql.resolvers.card
  "GraphQL resolvers for card and set queries.

  Provides Query resolvers for browsing the card catalog, including listing
  all cards/sets, filtering by set or type, and individual card/set lookup."
  (:require
   [bashketball-game-api.services.catalog :as catalog]
   [bashketball-schemas.card :as card-schema]
   [bashketball-schemas.enums :as enums]
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
  "Returns cards, optionally filtered by set slug and/or card type."
  [:=> [:cat :any [:map
                   [:set-slug {:optional true} [:maybe :string]]
                   [:card-type {:optional true} [:maybe enums/CardType]]] :any]
   [:vector card-schema/Card]]
  [ctx {:keys [set-slug card-type]} _value]
  (let [card-catalog (get-in ctx [:request :resolver-map :card-catalog])
        cards        (cond
                       (and set-slug card-type)
                       (->> (catalog/get-cards-by-set card-catalog set-slug)
                            (filter #(= (:card-type %) card-type)))

                       set-slug
                       (catalog/get-cards-by-set card-catalog set-slug)

                       card-type
                       (catalog/get-cards-by-type card-catalog card-type)

                       :else
                       (catalog/get-cards card-catalog))]
    (vec cards)))

(defresolver :Query :card
  "Returns a single card by slug."
  [:=> [:cat :any [:map [:slug :string]] :any] [:maybe card-schema/Card]]
  [ctx {:keys [slug]} _value]
  (let [card-catalog (get-in ctx [:request :resolver-map :card-catalog])]
    (catalog/get-card card-catalog slug)))

(def-resolver-map)
