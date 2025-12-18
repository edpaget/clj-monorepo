(ns bashketball-game-api.game.hydration
  "Game state hydration utilities.

  Provides functions to collect card slugs from game state and hydrate
  deck state with full card data from a [[bashketball-game-api.services.catalog/CardCatalog]].
  Used by both the game service and GraphQL resolvers to populate deck `:cards`
  fields with hydrated card data for action processing and API responses."
  (:require
   [bashketball-game-api.services.catalog :as catalog]))

(defn collect-deck-slugs
  "Extracts all unique card slugs from a deck state.

  Scans `:draw-pile`, `:hand`, `:discard`, `:removed`, and `:examined` piles,
  returning distinct card slugs. Returns an empty sequence for nil deck."
  [deck]
  (->> (concat (:draw-pile deck)
               (:hand deck)
               (:discard deck)
               (:removed deck)
               (:examined deck))
       (map :card-slug)
       distinct))

(defn collect-player-attachment-slugs
  "Extracts card slugs from player attachments.

  Iterates through all players in a team roster, collecting `:card-slug`
  values from their `:attachments`. Returns nil values filtered out."
  [team-roster]
  (->> (vals (:players team-roster))
       (mapcat :attachments)
       (keep :card-slug)))

(defn collect-asset-slugs
  "Extracts card slugs from team assets.

  Returns a sequence of `:card-slug` values from the assets collection,
  filtering out any nil values."
  [assets]
  (keep :card-slug assets))

(defn collect-extra-slugs
  "Collects card slugs from play area, assets, and attachments.

  Gathers slugs from locations outside the deck piles: both teams' assets,
  player attachments, and the play area. Used to ensure all cards in play
  are available for hydration."
  [game-state]
  (let [home-assets (get-in game-state [:players :team/HOME :assets] [])
        away-assets (get-in game-state [:players :team/AWAY :assets] [])
        home-roster (get-in game-state [:players :team/HOME :team])
        away-roster (get-in game-state [:players :team/AWAY :team])
        play-area   (get game-state :play-area [])]
    (concat (collect-asset-slugs home-assets)
            (collect-asset-slugs away-assets)
            (collect-player-attachment-slugs home-roster)
            (collect-player-attachment-slugs away-roster)
            (map :card-slug play-area))))

(defn hydrate-deck
  "Adds `:cards` field to deck state with full card data from catalog.

  Takes a deck state map and a [[catalog/CardCatalog]] instance. Collects
  all unique slugs from the deck and looks up each card in the catalog.
  Returns the deck with a `:cards` vector containing hydrated card maps.
  Returns empty `:cards` vector if catalog is nil."
  [deck card-catalog]
  (let [slugs (collect-deck-slugs deck)
        cards (if card-catalog
                (->> slugs
                     (map #(catalog/get-card card-catalog %))
                     (filter some?)
                     vec)
                [])]
    (assoc deck :cards cards)))

(defn hydrate-game-state
  "Hydrates deck cards in game state for action processing and API responses.

  The game engine needs card data (including card-type) to determine how to
  handle played cards (e.g., team assets vs regular cards). This function
  populates the `:cards` field in both teams' decks with full card data.

  Includes cards from:
  - Draw pile, hand, discard, removed, and examined piles
  - Play area
  - Team assets
  - Player attachments

  Returns nil if game-state is nil."
  [game-state card-catalog]
  (when game-state
    (let [extra-slugs (collect-extra-slugs game-state)
          extra-cards (when card-catalog
                        (->> extra-slugs
                             (map #(catalog/get-card card-catalog %))
                             (filter some?)
                             vec))
          home-deck   (get-in game-state [:players :team/HOME :deck])
          away-deck   (get-in game-state [:players :team/AWAY :deck])
          home-cards  (:cards (hydrate-deck home-deck card-catalog))
          away-cards  (:cards (hydrate-deck away-deck card-catalog))
          home-all    (->> (concat home-cards extra-cards)
                           (filter some?)
                           distinct
                           vec)
          away-all    (->> (concat away-cards extra-cards)
                           (filter some?)
                           distinct
                           vec)]
      (-> game-state
          (assoc-in [:players :team/HOME :deck :cards] home-all)
          (assoc-in [:players :team/AWAY :deck :cards] away-all)))))
