(ns bashketball-game-api.services.starter-deck
  "Service for claiming and managing starter decks.

  Loads starter deck definitions from EDN configuration and provides
  functionality for users to claim their starter decks individually.
  Each starter deck can only be claimed once per user."
  (:require
   [bashketball-game-api.models.claimed-starter-deck :as claimed]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.services.deck :as deck-svc]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [malli.core :as m]))

(def StarterDeck
  "Malli schema for a starter deck definition."
  [:map
   [:id :keyword]
   [:name :string]
   [:description :string]
   [:card-slugs [:vector :string]]])

(def StarterDecksConfig
  "Malli schema for starter decks configuration."
  [:map
   [:starter-decks [:vector StarterDeck]]])

(defn load-starter-decks-config
  "Loads starter deck definitions from the classpath.

  First attempts to load from the cards JAR at `cards/starter-decks.edn`.
  Falls back to local resources at `starter-decks.edn` if not found in JAR.
  Returns a map with `:starter-decks` key containing a vector of
  starter deck definitions. Throws if config is invalid or not found."
  []
  (let [resource (or (io/resource "cards/starter-decks.edn")
                     (io/resource "starter-decks.edn"))]
    (when-not resource
      (throw (ex-info "Starter decks config not found on classpath"
                      {:searched ["cards/starter-decks.edn" "starter-decks.edn"]})))
    (let [config (-> resource slurp edn/read-string)]
      (when-not (m/validate StarterDecksConfig config)
        (throw (ex-info "Invalid starter decks config"
                        {:errors (m/explain StarterDecksConfig config)})))
      config)))

(defprotocol StarterDeckService
  "Protocol for starter deck operations."
  (get-starter-deck-definitions [this]
    "Returns all starter deck definitions.")
  (get-available-starter-decks [this user-id]
    "Returns starter deck definitions the user hasn't claimed yet.")
  (get-claimed-starter-decks [this user-id]
    "Returns user's claimed starter deck records with deck IDs.")
  (claim-starter-deck! [this user-id starter-deck-id]
    "Claims a single starter deck. Returns the claim record with deck, or nil if already claimed."))

(defrecord StarterDeckServiceImpl [deck-repo card-catalog validation-rules config]
  StarterDeckService
  (get-starter-deck-definitions [_this]
    (:starter-decks config))

  (get-available-starter-decks [_this user-id]
    (let [claimed-ids (->> (claimed/find-by-user user-id)
                           (map :starter-deck-id)
                           set)
          all-defs    (:starter-decks config)]
      (filterv #(not (contains? claimed-ids (name (:id %)))) all-defs)))

  (get-claimed-starter-decks [_this user-id]
    (->> (claimed/find-by-user user-id)
         (mapv #(update % :claimed-at str))))

  (claim-starter-deck! [_this user-id starter-deck-id]
    (let [starter-deck-id-str (if (keyword? starter-deck-id)
                                (name starter-deck-id)
                                starter-deck-id)]
      (when-not (claimed/has-claimed? user-id starter-deck-id-str)
        (let [definition (->> (:starter-decks config)
                              (filter #(= (name (:id %)) starter-deck-id-str))
                              first)]
          (when definition
            (let [validation (deck-svc/validate-deck card-catalog
                                                     {:card-slugs (:card-slugs definition)}
                                                     validation-rules)
                  deck       (proto/create! deck-repo
                                            {:user-id user-id
                                             :name (:name definition)
                                             :card-slugs (:card-slugs definition)
                                             :is-valid (:is-valid validation)
                                             :validation-errors (:validation-errors validation)})
                  claim      (claimed/record-claim! user-id starter-deck-id-str (:id deck))]
              (-> claim
                  (assoc :deck deck)
                  (update :claimed-at str)))))))))

(defn create-starter-deck-service
  "Creates a new starter deck service instance.

  Takes a deck repository and card catalog. Optionally accepts custom
  validation rules and configuration."
  ([deck-repo card-catalog]
   (create-starter-deck-service deck-repo card-catalog
                                deck-svc/default-validation-rules))
  ([deck-repo card-catalog validation-rules]
   (create-starter-deck-service deck-repo card-catalog
                                validation-rules (load-starter-decks-config)))
  ([deck-repo card-catalog validation-rules config]
   (->StarterDeckServiceImpl deck-repo card-catalog validation-rules config)))
