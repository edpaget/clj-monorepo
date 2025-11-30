(ns bashketball-game-api.services.catalog
  "Card catalog service for loading and querying card data from the JAR.

  Loads card EDN files from the `io.github.bashketball/cards` dependency at
  startup. Cards are indexed by slug for fast lookup. Sets are discovered by
  scanning for `metadata.edn` files in the `cards/` classpath directory."
  (:require
   [bashketball-schemas.card :as card-schema]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [malli.core :as m])
  (:import
   [java.net JarURLConnection]
   [java.util.jar JarFile]))

(defprotocol CardCatalog
  "Protocol for querying card and set data."
  (get-card [this slug] "Returns a card by its slug, or nil if not found.")
  (get-cards [this] "Returns all cards as a sequence.")
  (get-cards-by-set [this set-slug] "Returns all cards in a set.")
  (get-cards-by-type [this card-type] "Returns all cards of a given type.")
  (get-set [this set-slug] "Returns set metadata by slug, or nil if not found.")
  (get-sets [this] "Returns all set metadata as a sequence."))

(defn- list-jar-entries
  "Lists all entries in a JAR file starting with the given prefix."
  [^JarFile jar-file prefix]
  (->> (.entries jar-file)
       enumeration-seq
       (map #(.getName %))
       (filter #(str/starts-with? % prefix))))

(defn- get-jar-file
  "Gets the JarFile for a classpath resource URL."
  [resource-url]
  (when (= "jar" (.getProtocol resource-url))
    (-> resource-url
        .openConnection
        ^JarURLConnection
        .getJarFile)))

(defn- load-edn-resource
  "Loads and parses an EDN file from the classpath."
  [path]
  (when-let [resource (io/resource path)]
    (-> resource slurp edn/read-string)))

(defn- discover-sets
  "Discovers all card sets by finding metadata.edn files."
  []
  (let [cards-url (io/resource "cards/")]
    (when-let [jar-file (get-jar-file cards-url)]
      (let [entries (list-jar-entries jar-file "cards/")]
        (->> entries
             (filter #(str/ends-with? % "/metadata.edn"))
             (map load-edn-resource)
             (filter some?))))))

(defn- discover-cards
  "Discovers all cards by scanning set directories."
  []
  (let [cards-url (io/resource "cards/")]
    (when-let [jar-file (get-jar-file cards-url)]
      (let [entries (list-jar-entries jar-file "cards/")]
        (->> entries
             (filter #(and (str/ends-with? % ".edn")
                           (not (str/ends-with? % "metadata.edn"))))
             (map load-edn-resource)
             (filter some?))))))

(defn- validate-card!
  "Validates a card against bashketball-schemas. Logs warning on invalid."
  [card]
  (if (m/validate card-schema/Card card)
    card
    (do
      (log/warn "Invalid card data:" (:slug card)
                (m/explain card-schema/Card card))
      nil)))

(defn- validate-set!
  "Validates set metadata against bashketball-schemas. Logs warning on invalid."
  [card-set]
  (if (m/validate card-schema/CardSet card-set)
    card-set
    (do
      (log/warn "Invalid set metadata:" (:slug card-set)
                (m/explain card-schema/CardSet card-set))
      nil)))

(defrecord InMemoryCardCatalog [cards-by-slug cards-by-set cards-by-type sets-by-slug]
  CardCatalog
  (get-card [_this slug]
    (get cards-by-slug slug))

  (get-cards [_this]
    (vals cards-by-slug))

  (get-cards-by-set [_this set-slug]
    (get cards-by-set set-slug []))

  (get-cards-by-type [_this card-type]
    (get cards-by-type card-type []))

  (get-set [_this set-slug]
    (get sets-by-slug set-slug))

  (get-sets [_this]
    (vals sets-by-slug)))

(defn create-card-catalog
  "Creates a card catalog by loading all cards and sets from the classpath.

  Scans the `cards/` directory in the classpath (from the cards JAR) for
  EDN files. Validates each card and set against bashketball-schemas.
  Invalid entries are logged and skipped."
  []
  (log/info "Loading card catalog from classpath...")
  (let [raw-sets      (discover-sets)
        raw-cards     (discover-cards)
        valid-sets    (->> raw-sets
                           (map validate-set!)
                           (filter some?))
        valid-cards   (->> raw-cards
                           (map validate-card!)
                           (filter some?))
        cards-by-slug (into {} (map (juxt :slug identity) valid-cards))
        cards-by-set  (group-by :set-slug valid-cards)
        cards-by-type (group-by :card-type valid-cards)
        sets-by-slug  (into {} (map (juxt :slug identity) valid-sets))]
    (log/info "Loaded" (count valid-cards) "cards from" (count valid-sets) "sets")
    (->InMemoryCardCatalog cards-by-slug cards-by-set cards-by-type sets-by-slug)))
