(ns bashketball-game-api.services.deck
  "Deck management service with validation.

  Provides business logic for deck CRUD operations and validation against
  deck building rules. Uses [[bashketball-game-api.services.catalog/CardCatalog]]
  to validate card slugs and types."
  (:require
   [bashketball-game-api.models.deck :as deck-model]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.services.catalog :as catalog]
   [malli.core :as m]))

(def ValidationRules
  "Malli schema for deck validation rules."
  [:map
   [:min-players pos-int?]
   [:max-players pos-int?]
   [:min-action-cards pos-int?]
   [:max-action-cards pos-int?]
   [:max-copies-per-card pos-int?]])

(def default-validation-rules
  "Default deck validation rules."
  {:min-players 3
   :max-players 5
   :min-action-cards 10
   :max-action-cards 65
   :max-copies-per-card 2})

(defn- standard-action-card?
  "Returns true if the card is a STANDARD_ACTION_CARD."
  [card]
  (= :card-type/STANDARD_ACTION_CARD (:card-type card)))

(defn- count-duplicates
  "Returns a map of card-slug -> count for slugs that exceed the limit.

  Takes a card catalog to look up card types. STANDARD_ACTION_CARD types
  are exempt from the copy limit and can have unlimited copies."
  [card-catalog card-slugs max-copies]
  (->> card-slugs
       frequencies
       (filter (fn [[slug count]]
                 (let [card (catalog/get-card card-catalog slug)]
                   (and (> count max-copies)
                        (not (standard-action-card? card))))))
       (into {})))

(defn- player-card?
  "Returns true if the card is a player card."
  [card]
  (= :card-type/PLAYER_CARD (:card-type card)))

(defn validate-deck
  "Validates a deck's card slugs against deck building rules.

  Takes a card catalog, deck data, and optional validation rules. Returns a map
  with `:is-valid` boolean and `:validation-errors` vector of error messages.

  Validates:
  - All card slugs exist in the catalog
  - Player card count is within min/max bounds
  - Action card count is within min/max bounds
  - No card exceeds the max copies limit"
  ([card-catalog deck]
   (validate-deck card-catalog deck default-validation-rules))
  ([card-catalog deck rules]
   {:pre [(m/validate ValidationRules rules)]}
   (let [{:keys [min-players max-players
                 min-action-cards max-action-cards
                 max-copies-per-card]}             rules
         card-slugs                                (:card-slugs deck)
         cards                                     (map #(catalog/get-card card-catalog %) card-slugs)
         invalid-slugs                             (->> (map vector card-slugs cards)
                                                        (filter (fn [[_ card]] (nil? card)))
                                                        (map first))
         valid-cards                               (filter some? cards)
         players                                   (filter player-card? valid-cards)
         actions                                   (remove player-card? valid-cards)
         player-count                              (count players)
         action-count                              (count actions)
         over-limit-cards                          (count-duplicates card-catalog card-slugs max-copies-per-card)
         errors                                    (cond-> []
                                                     (seq invalid-slugs)
                                                     (conj (str "Unknown cards: " (pr-str invalid-slugs)))

                                                     (< player-count min-players)
                                                     (conj (str "Need at least " min-players " player cards (have " player-count ")"))

                                                     (> player-count max-players)
                                                     (conj (str "Maximum " max-players " player cards allowed (have " player-count ")"))

                                                     (< action-count min-action-cards)
                                                     (conj (str "Need at least " min-action-cards " action cards (have " action-count ")"))

                                                     (> action-count max-action-cards)
                                                     (conj (str "Maximum " max-action-cards " action cards allowed (have " action-count ")"))

                                                     (seq over-limit-cards)
                                                     (conj (str "Cards over " max-copies-per-card " copy limit: "
                                                                (pr-str (keys over-limit-cards)))))]
     {:is-valid (empty? errors)
      :validation-errors (when (seq errors) errors)})))

(defprotocol DeckService
  "Protocol for deck management operations."
  (list-user-decks [this user-id]
    "Returns all decks for the given user.")
  (get-deck [this deck-id]
    "Returns a deck by ID, or nil if not found.")
  (get-deck-for-user [this deck-id user-id]
    "Returns a deck by ID if owned by the user, or nil.")
  (create-deck! [this user-id deck-name]
    "Creates a new empty deck for the user.")
  (update-deck! [this deck-id user-id updates]
    "Updates a deck if owned by the user. Returns updated deck or nil.")
  (delete-deck! [this deck-id user-id]
    "Deletes a deck if owned by the user. Returns true if deleted.")
  (validate-deck! [this deck-id user-id]
    "Validates and updates a deck's validation state. Returns updated deck or nil.")
  (add-cards-to-deck! [this deck-id user-id card-slugs]
    "Adds cards to a deck. Returns updated deck or nil.")
  (remove-cards-from-deck! [this deck-id user-id card-slugs]
    "Removes cards from a deck. Returns updated deck or nil."))

(defrecord DeckServiceImpl [deck-repo card-catalog validation-rules]
  DeckService
  (list-user-decks [_this user-id]
    (deck-model/find-by-user deck-repo user-id))

  (get-deck [_this deck-id]
    (proto/find-by deck-repo {:id deck-id}))

  (get-deck-for-user [_this deck-id user-id]
    (let [deck (proto/find-by deck-repo {:id deck-id})]
      (when (and deck (= user-id (:user-id deck)))
        deck)))

  (create-deck! [_this user-id deck-name]
    (proto/create! deck-repo {:user-id user-id
                              :name deck-name
                              :card-slugs []
                              :is-valid false}))

  (update-deck! [this deck-id user-id updates]
    (when (get-deck-for-user this deck-id user-id)
      (let [allowed-keys #{:name :card-slugs}
            filtered     (select-keys updates allowed-keys)]
        (when (seq filtered)
          (let [updated (proto/update! deck-repo deck-id filtered)]
            (if (contains? filtered :card-slugs)
              (let [validation (validate-deck card-catalog updated validation-rules)]
                (proto/update! deck-repo deck-id validation))
              updated))))))

  (delete-deck! [this deck-id user-id]
    (when (get-deck-for-user this deck-id user-id)
      (proto/delete! deck-repo deck-id)))

  (validate-deck! [this deck-id user-id]
    (when-let [deck (get-deck-for-user this deck-id user-id)]
      (let [validation (validate-deck card-catalog deck validation-rules)]
        (proto/update! deck-repo deck-id validation))))

  (add-cards-to-deck! [this deck-id user-id card-slugs]
    (when-let [deck (get-deck-for-user this deck-id user-id)]
      (let [new-slugs (into (vec (:card-slugs deck)) card-slugs)]
        (update-deck! this deck-id user-id {:card-slugs new-slugs}))))

  (remove-cards-from-deck! [this deck-id user-id card-slugs]
    (when-let [deck (get-deck-for-user this deck-id user-id)]
      (let [to-remove     (frequencies card-slugs)
            [new-slugs _] (reduce (fn [[acc remaining] slug]
                                    (if (pos? (get remaining slug 0))
                                      [acc (update remaining slug dec)]
                                      [(conj acc slug) remaining]))
                                  [[] to-remove]
                                  (:card-slugs deck))]
        (update-deck! this deck-id user-id {:card-slugs new-slugs})))))

(defn create-deck-service
  "Creates a new deck service instance.

  Takes a deck repository and card catalog. Optionally accepts custom
  validation rules map."
  ([deck-repo card-catalog]
   (create-deck-service deck-repo card-catalog default-validation-rules))
  ([deck-repo card-catalog validation-rules]
   (->DeckServiceImpl deck-repo card-catalog validation-rules)))
