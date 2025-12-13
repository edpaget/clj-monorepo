(ns bashketball-game-ui.schemas.deck
  "Deck schemas for the UI.

  Provides Malli schemas for deck validation and card type helpers."
  (:require
   [bashketball-schemas.card :as card]
   [bashketball-schemas.core :as schemas]))

(def Deck
  "Schema for a deck after decoding from GraphQL.

  Keys are kebab-case after transformation from camelCase GraphQL response."
  [:map
   [:id :uuid]
   [:name [:string {:min 1 :max 255}]]
   [:card-slugs [:vector card/Slug]]
   [:cards {:optional true} [:vector card/Card]]
   [:is-valid :boolean]
   [:validation-errors {:optional true} [:vector :string]]
   [:created-at {:optional true} [:maybe schemas/DateTime]]
   [:updated-at {:optional true} [:maybe schemas/DateTime]]])

(def deck-rules
  "Deck validation rules for the UI."
  {:min-player-cards 3
   :max-player-cards 5
   :min-action-cards 10
   :max-action-cards 65
   :max-copies-per-card 2})

(defn player-card?
  "Returns true if the card is a player card."
  [card]
  (= (:card-type card) :card-type/PLAYER_CARD))

(defn standard-action-card?
  "Returns true if the card is a STANDARD_ACTION_CARD (unlimited copies allowed)."
  [card]
  (= (:card-type card) :card-type/STANDARD_ACTION_CARD))

(defn action-card?
  "Returns true if the card is an action-type card (non-player)."
  [card]
  (not (player-card? card)))

(defn count-card-copies
  "Returns a map of slug -> count for cards in the deck."
  [card-slugs]
  (frequencies card-slugs))

(defn validate-deck-client
  "Performs client-side validation of a deck.

  Returns a vector of validation error strings, empty if valid.
  STANDARD_ACTION_CARD types are exempt from the copy limit."
  [deck cards-by-slug]
  (let [card-slugs   (:card-slugs deck)
        cards        (keep #(get cards-by-slug %) card-slugs)
        player-cards (filter player-card? cards)
        action-cards (filter action-card? cards)
        copy-counts  (count-card-copies card-slugs)
        errors       (transient [])]
    (when (< (count player-cards) (:min-player-cards deck-rules))
      (conj! errors (str "Need at least " (:min-player-cards deck-rules) " player cards")))
    (when (> (count player-cards) (:max-player-cards deck-rules))
      (conj! errors (str "Maximum " (:max-player-cards deck-rules) " player cards allowed")))
    (when (< (count action-cards) (:min-action-cards deck-rules))
      (conj! errors (str "Need at least " (:min-action-cards deck-rules) " action cards")))
    (when (> (count action-cards) (:max-action-cards deck-rules))
      (conj! errors (str "Maximum " (:max-action-cards deck-rules) " action cards allowed")))
    (doseq [[slug count] copy-counts]
      (let [card (get cards-by-slug slug)]
        (when (and (> count (:max-copies-per-card deck-rules))
                   (not (standard-action-card? card)))
          (conj! errors (str "Maximum " (:max-copies-per-card deck-rules) " copies of " slug " allowed")))))
    (persistent! errors)))

(def card-type-labels
  "Human-readable labels for card types."
  {:card-type/PLAYER_CARD "Player"
   :card-type/ABILITY_CARD "Ability"
   :card-type/PLAY_CARD "Play"
   :card-type/STANDARD_ACTION_CARD "Action"
   :card-type/SPLIT_PLAY_CARD "Split Play"
   :card-type/COACHING_CARD "Coaching"
   :card-type/TEAM_ASSET_CARD "Team Asset"})

(defn card-type-label
  "Returns a human-readable label for a card type."
  [card-type]
  (get card-type-labels card-type (name card-type)))
