(ns bashketball-game.effect-catalog
  "Effect catalog protocol for looking up card effect definitions.

   The [[EffectCatalog]] protocol provides a uniform interface for retrieving
   structured effect definitions from cards. Implementations may load from
   a cards JAR, in-memory maps, or other sources.

   Usage:

   ```clojure
   (require '[bashketball-game.effect-catalog :as catalog])

   ;; Get abilities for a player card
   (catalog/get-abilities my-catalog \"michael-jordan\")

   ;; Get the play effect for a play card
   (catalog/get-play my-catalog \"fast-break\")

   ;; Get offense/defense modes for standard action
   (catalog/get-offense my-catalog \"shoot-block\")
   ```")

(defprotocol EffectCatalog
  "Protocol for looking up effect definitions from cards.

   Implementations provide access to the structured effect definitions
   for each card type. All methods take a card slug and return the
   relevant effect definition(s), or nil if not found."

  (get-card [this card-slug]
    "Returns the full card definition for the given slug.")

  (get-abilities [this card-slug]
    "Returns ability definitions for a player or ability card.

     Returns a vector of AbilityDef maps, or nil if the card has no abilities.")

  (get-play [this card-slug]
    "Returns the play definition for a play card.

     Returns a PlayDef map, or nil if not a play card.")

  (get-offense [this card-slug]
    "Returns the offense action definition for a standard action or split play.

     Returns an ActionModeDef map, or nil if not available.")

  (get-defense [this card-slug]
    "Returns the defense action definition for a standard action or split play.

     Returns an ActionModeDef map, or nil if not available.")

  (get-call [this card-slug]
    "Returns the call effect definition for a coaching card.

     Returns a CallDef map, or nil if not a coaching card.")

  (get-signal [this card-slug]
    "Returns the signal definition for a coaching card.

     Returns a SignalDef map, or nil if the card has no signal.")

  (get-asset-power [this card-slug]
    "Returns the asset power definition for a team asset card.

     Returns an AssetPowerDef map, or nil if not a team asset."))

;; =============================================================================
;; Map-based Implementation
;; =============================================================================

(defn- extract-by-type
  "Extracts a field from a card if it matches the expected card type(s)."
  [card field valid-types]
  (when (contains? valid-types (:card-type card))
    (get card field)))

(defrecord MapEffectCatalog [cards]
  EffectCatalog

  (get-card [_ card-slug]
    (get cards card-slug))

  (get-abilities [_ card-slug]
    (when-let [card (get cards card-slug)]
      (extract-by-type card :abilities
                       #{:card-type/PLAYER_CARD :card-type/ABILITY_CARD})))

  (get-play [_ card-slug]
    (when-let [card (get cards card-slug)]
      (extract-by-type card :play #{:card-type/PLAY_CARD})))

  (get-offense [_ card-slug]
    (when-let [card (get cards card-slug)]
      (extract-by-type card :offense
                       #{:card-type/STANDARD_ACTION_CARD :card-type/SPLIT_PLAY_CARD})))

  (get-defense [_ card-slug]
    (when-let [card (get cards card-slug)]
      (extract-by-type card :defense
                       #{:card-type/STANDARD_ACTION_CARD :card-type/SPLIT_PLAY_CARD})))

  (get-call [_ card-slug]
    (when-let [card (get cards card-slug)]
      (extract-by-type card :call #{:card-type/COACHING_CARD})))

  (get-signal [_ card-slug]
    (when-let [card (get cards card-slug)]
      (extract-by-type card :signal #{:card-type/COACHING_CARD})))

  (get-asset-power [_ card-slug]
    (when-let [card (get cards card-slug)]
      (extract-by-type card :asset-power #{:card-type/TEAM_ASSET_CARD}))))

(defn create-catalog
  "Creates an EffectCatalog from a map of card-slug -> card-definition.

   The cards map should contain full card definitions with structured
   effect fields (not string identifiers)."
  [cards]
  (->MapEffectCatalog cards))

(defn create-catalog-from-seq
  "Creates an EffectCatalog from a sequence of card definitions.

   Cards are indexed by their `:slug` field."
  [cards]
  (->MapEffectCatalog (into {} (map (juxt :slug identity) cards))))

;; =============================================================================
;; Inline Card Support (for tokens)
;; =============================================================================

(defn get-abilities-from-card
  "Extracts abilities directly from a card definition.

   Useful for token cards that have inline definitions rather than
   being looked up from a catalog."
  [card]
  (when (contains? #{:card-type/PLAYER_CARD :card-type/ABILITY_CARD}
                   (:card-type card))
    (:abilities card)))

(defn get-asset-power-from-card
  "Extracts asset power directly from a card definition.

   Useful for token assets that have inline definitions."
  [card]
  (when (= :card-type/TEAM_ASSET_CARD (:card-type card))
    (:asset-power card)))
