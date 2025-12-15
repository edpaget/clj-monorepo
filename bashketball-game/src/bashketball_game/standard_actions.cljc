(ns bashketball-game.standard-actions
  "Standard action card definitions.

  Standard actions are basic gameplay actions available to all players.
  Players can either play a standard action card from their hand, or
  discard 2 cards to use any standard action without the card.

  The three standard action cards are:
  - Shoot / Block
  - Pass / Steal
  - Screen / Check")

(def standard-action-cards
  "Vector of all standard action card definitions.

  Each card follows the [[bashketball-schemas.card/StandardActionCard]] schema
  with offense and defense options representing the two sides of the card."
  [{:slug      "shoot-block"
    :name      "Shoot / Block"
    :set-slug  "standard"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate      3
    :offense   "Ball carrier within 7 hexes of basket attempts Shot"
    :defense   "Force opponent to shoot or exhaust"}
   {:slug      "pass-steal"
    :name      "Pass / Steal"
    :set-slug  "standard"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate      3
    :offense   "Ball carrier attempts Pass to teammate within 6 hexes"
    :defense   "Engage ball carrier within 2 hexes. Attempt steal."}
   {:slug      "screen-check"
    :name      "Screen / Check"
    :set-slug  "standard"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate      3
    :offense   "Engage defender within 2 hexes. Screen play."
    :defense   "Engage opponent within 2 hexes. Check play."}])

(def standard-action-slugs
  "Set of valid standard action card slugs."
  #{"shoot-block" "pass-steal" "screen-check"})

(defn get-standard-action
  "Returns the standard action card definition for the given slug, or nil."
  [slug]
  (first (filter #(= (:slug %) slug) standard-action-cards)))

(defn valid-standard-action-slug?
  "Returns true if the slug is a valid standard action card."
  [slug]
  (contains? standard-action-slugs slug))
