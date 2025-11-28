(ns bashketball-editor-ui.components.cards.card-preview
  "Card preview component for displaying cards in MTG-style format.

  Works with both GraphQL JS objects and Clojure maps via keyword access."
  (:require
   [uix.core :refer [$ defui]]))

;; -----------------------------------------------------------------------------
;; Display components
;; -----------------------------------------------------------------------------

(defui stat-display
  "Display a stat value with label."
  [{:keys [label value]}]
  ($ :div {:class "text-center"}
     ($ :div {:class "text-sm font-bold text-gray-900"} (or value "-"))
     ($ :div {:class "text-[10px] text-gray-500 uppercase"} label)))

(defui text-block
  "Display a text block with label."
  [{:keys [label value]}]
  (when (seq value)
    ($ :div {:class "space-y-0.5"}
       ($ :div {:class "text-[10px] font-medium text-gray-500 uppercase"} label)
       ($ :div {:class "text-gray-900 whitespace-pre-wrap leading-tight"} value))))

(defui abilities-list
  "Display a list of abilities."
  [{:keys [abilities]}]
  (let [abilities-vec (cond
                        (vector? abilities) abilities
                        (array? abilities) (js->clj abilities)
                        :else nil)]
    (when (seq abilities-vec)
      ($ :div {:class "space-y-0.5"}
         ($ :div {:class "text-[10px] font-medium text-gray-500 uppercase"} "Abilities")
         ($ :ul {:class "list-disc list-inside space-y-0.5"}
            (for [[idx ability] (map-indexed vector abilities-vec)]
              ($ :li {:key idx :class "text-gray-900 leading-tight"} ability)))))))

(defui card-type-badge
  "Display the card type as a badge."
  [{:keys [card-type]}]
  (let [type-label (case card-type
                     "PlayerCard" "Player"
                     "PLAYER_CARD" "Player"
                     "PlayCard" "Play"
                     "PLAY_CARD" "Play"
                     "AbilityCard" "Ability"
                     "ABILITY_CARD" "Ability"
                     "SplitPlayCard" "Split Play"
                     "SPLIT_PLAY_CARD" "Split Play"
                     "CoachingCard" "Coaching"
                     "COACHING_CARD" "Coaching"
                     "TeamAssetCard" "Team Asset"
                     "TEAM_ASSET_CARD" "Team Asset"
                     "StandardActionCard" "Standard Action"
                     "STANDARD_ACTION_CARD" "Standard Action"
                     (or card-type "Unknown"))]
    ($ :span {:class "inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium bg-gray-700 text-gray-100"}
       type-label)))

;; -----------------------------------------------------------------------------
;; Type-specific card displays
;; -----------------------------------------------------------------------------

(defui player-card-display
  "Display player card specific fields."
  [{:keys [card]}]
  (let [abilities (or (:abilities card) [])]
    ($ :<>
       ($ :div {:class "grid grid-cols-5 gap-1 p-1.5 bg-gray-100 rounded"}
          ($ stat-display {:label "SHT" :value (:sht card)})
          ($ stat-display {:label "PSS" :value (:pss card)})
          ($ stat-display {:label "DEF" :value (:def card)})
          ($ stat-display {:label "SPD" :value (:speed card)})
          ($ stat-display {:label "SIZE" :value (:size card)}))
       ($ :div {:class "flex items-center gap-1 text-[10px] text-gray-500 mt-1"}
          ($ :span "Deck:")
          ($ :span {:class "font-medium text-gray-900"} (or (:deck-size card) (:deckSize card) "-")))
       ($ abilities-list {:abilities abilities}))))

(defui play-card-display
  "Display play card specific fields."
  [{:keys [card]}]
  ($ text-block {:label "Play" :value (:play card)}))

(defui ability-card-display
  "Display ability card specific fields."
  [{:keys [card]}]
  ($ abilities-list {:abilities (or (:abilities card) [])}))

(defui split-play-card-display
  "Display split play card specific fields."
  [{:keys [card]}]
  ($ :div {:class "grid grid-cols-2 gap-1"}
     ($ :div {:class "p-1 bg-green-100 rounded"}
        ($ text-block {:label "Offense" :value (:offense card)}))
     ($ :div {:class "p-1 bg-red-100 rounded"}
        ($ text-block {:label "Defense" :value (:defense card)}))))

(defui coaching-card-display
  "Display coaching card specific fields."
  [{:keys [card]}]
  ($ text-block {:label "Coaching" :value (:coaching card)}))

(defui team-asset-card-display
  "Display team asset card specific fields."
  [{:keys [card]}]
  ($ text-block {:label "Asset Power" :value (or (:asset-power card) (:assetPower card))}))

(defui standard-action-card-display
  "Display standard action card specific fields."
  [{:keys [card]}]
  ($ :div {:class "grid grid-cols-2 gap-1"}
     ($ :div {:class "p-1 bg-green-100 rounded"}
        ($ text-block {:label "Offense" :value (:offense card)}))
     ($ :div {:class "p-1 bg-red-100 rounded"}
        ($ text-block {:label "Defense" :value (:defense card)}))))

;; -----------------------------------------------------------------------------
;; Main card preview
;; -----------------------------------------------------------------------------

(def type-display-components
  "Map of card type to display component."
  {"PlayerCard" player-card-display
   "PLAYER_CARD" player-card-display
   "PlayCard" play-card-display
   "PLAY_CARD" play-card-display
   "AbilityCard" ability-card-display
   "ABILITY_CARD" ability-card-display
   "SplitPlayCard" split-play-card-display
   "SPLIT_PLAY_CARD" split-play-card-display
   "CoachingCard" coaching-card-display
   "COACHING_CARD" coaching-card-display
   "TeamAssetCard" team-asset-card-display
   "TEAM_ASSET_CARD" team-asset-card-display
   "StandardActionCard" standard-action-card-display
   "STANDARD_ACTION_CARD" standard-action-card-display})

(defui card-preview
  "Display the card in MTG card aspect ratio (2.5:3.5 = 5:7).

  Props:
  - `:card` - Card data (map or JS object with keyword access)
  - `:card-type` - Card type string (optional, uses :card-type or :__typename from card)
  - `:set-slug` - Set slug for footer (optional, uses :set-slug or :setSlug from card)
  - `:class` - Additional CSS classes for the container"
  [{:keys [card card-type set-slug class]}]
  (let [resolved-type     (or card-type
                              (:card-type card)
                              (:__typename card))
        resolved-set-slug (or set-slug
                              (:set-slug card)
                              (:setSlug card))
        display-component (get type-display-components resolved-type)
        fate              (:fate card)
        name              (:name card)
        image-prompt      (or (:image-prompt card) (:imagePrompt card))]
    ($ :div {:class (str "aspect-[5/7] w-80 bg-gradient-to-b from-gray-100 to-gray-200 rounded-xl border-4 border-gray-800 shadow-xl overflow-hidden flex flex-col " class)}
       ;; Card header with name
       ($ :div {:class "bg-gradient-to-r from-gray-700 to-gray-800 px-4 py-2 relative"}
          (when fate
            ($ :div {:class "absolute top-2 right-3 w-8 h-8 bg-amber-400 rounded-full flex items-center justify-center text-gray-900 font-bold text-lg shadow-md"}
               fate))
          ($ :h2 {:class "text-white font-bold text-lg truncate pr-10"}
             (if (seq name) name "Card Name")))
       ;; Card image area (placeholder)
       ($ :div {:class "h-32 bg-gradient-to-br from-blue-400 to-purple-500 mx-3 mt-3 rounded border-2 border-gray-600 flex items-center justify-center"}
          (when (seq image-prompt)
            ($ :div {:class "text-white/70 text-xs text-center px-2 italic"}
               image-prompt)))
       ;; Card type badge
       (when resolved-type
         ($ :div {:class "flex justify-center mt-1"}
            ($ card-type-badge {:card-type resolved-type})))
       ;; Card content area
       ($ :div {:class "flex-1 mx-3 my-2 bg-white/80 rounded p-2 overflow-y-auto text-xs"}
          (when display-component
            ($ display-component {:card card})))
       ;; Card footer with set info
       ($ :div {:class "bg-gray-800 px-3 py-1 text-gray-400 text-xs"}
          (or resolved-set-slug "—")))))
