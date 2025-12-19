(ns bashketball-ui.cards.card-preview
  "Card preview component for displaying cards in MTG-style format.

  Works with both GraphQL JS objects and Clojure maps via keyword access."
  (:require
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(defui stat-display
  "Display a stat value with label."
  [{:keys [label value]}]
  ($ :div {:class "text-center"}
     ($ :div {:class "text-sm font-bold text-gray-900"}
        (cond
          (nil? value) "-"
          (keyword? value) (name value)
          :else value))
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

(defn- format-subtype
  "Format a subtype keyword into a display label."
  [subtype]
  (let [s (if (keyword? subtype) (name subtype) subtype)]
    (-> s
        (str/replace #"^[a-z]+-subtype/" "")
        str/capitalize)))

(defui subtypes-display
  "Display a list of subtypes as small badges."
  [{:keys [subtypes label]}]
  (let [subtypes-vec (cond
                       (vector? subtypes) subtypes
                       (array? subtypes) (js->clj subtypes)
                       :else nil)]
    (when (seq subtypes-vec)
      ($ :div {:class "flex flex-wrap gap-1 items-center"}
         (when label
           ($ :span {:class "text-[10px] text-gray-500 uppercase"} label))
         (for [[idx subtype] (map-indexed vector subtypes-vec)]
           ($ :span {:key idx
                     :class "inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-purple-100 text-purple-700"}
              (format-subtype subtype)))))))

(defui card-type-badge
  "Display the card type as a badge."
  [{:keys [card-type]}]
  (let [type-str   (if (keyword? card-type) (name card-type) card-type)
        type-label (case type-str
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
                     (or type-str "Unknown"))]
    ($ :span {:class "inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium bg-gray-700 text-gray-100"}
       type-label)))

(defui player-card-display
  "Display player card specific fields."
  [{:keys [card]}]
  (let [abilities       (or (:abilities card) [])
        player-subtypes (or (:player-subtypes card) (:playerSubtypes card) [])]
    ($ :<>
       ($ :div {:class "grid grid-cols-5 gap-1 p-1.5 bg-gray-100 rounded"}
          ($ stat-display {:label "SHT" :value (:sht card)})
          ($ stat-display {:label "PSS" :value (:pss card)})
          ($ stat-display {:label "DEF" :value (:def card)})
          ($ stat-display {:label "SPD" :value (:speed card)})
          ($ stat-display {:label "SIZE" :value (:size card)}))
       (when (seq player-subtypes)
         ($ :div {:class "mt-1"}
            ($ subtypes-display {:subtypes player-subtypes})))
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
  ($ :div {:class "flex flex-col gap-2"}
     ($ text-block {:label "Offense" :value (:offense card)})
     ($ :hr {:class "border-gray-300 mx-2"})
     ($ text-block {:label "Defense" :value (:defense card)})))

(defui coaching-card-display
  "Display coaching card specific fields."
  [{:keys [card]}]
  ($ :div {:class "flex flex-col gap-2"}
     ($ text-block {:label "Coaching" :value (:coaching card)})
     ($ text-block {:label "Signal" :value (:signal card)})))

(defui team-asset-card-display
  "Display team asset card specific fields."
  [{:keys [card]}]
  ($ text-block {:label "Asset Power" :value (or (:asset-power card) (:assetPower card))}))

(defui standard-action-card-display
  "Display standard action card specific fields."
  [{:keys [card]}]
  ($ :div {:class "flex flex-col gap-2"}
     ($ text-block {:label "Offense" :value (:offense card)})
     ($ :hr {:class "border-gray-300 mx-2"})
     ($ text-block {:label "Defense" :value (:defense card)})))

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
  (let [raw-type          (or card-type
                              (:card-type card)
                              (:__typename card))
        resolved-type     (if (keyword? raw-type) (name raw-type) raw-type)
        resolved-set-slug (or set-slug
                              (:set-slug card)
                              (:setSlug card))
        display-component (get type-display-components resolved-type)
        fate              (:fate card)
        deck-size         (or (:deck-size card) (:deckSize card))
        name              (:name card)
        image-prompt      (or (:image-prompt card) (:imagePrompt card))
        card-subtypes     (or (:card-subtypes card) (:cardSubtypes card) [])]
    ($ :div {:class (str "aspect-[5/7] w-80 bg-gradient-to-b from-gray-100 to-gray-200 rounded-xl border-4 border-gray-800 shadow-xl overflow-hidden flex flex-col " class)}
       ($ :div {:class "bg-gradient-to-r from-gray-700 to-gray-800 px-4 py-2 relative"}
          ($ :div {:class "absolute top-2 right-3 flex items-center gap-1.5"}
             (when deck-size
               ($ :div {:class "w-6 h-6 bg-blue-500 rounded flex items-center justify-center text-white font-bold text-xs shadow-md"
                        :title "Deck size"}
                  deck-size))
             (when fate
               ($ :div {:class "w-6 h-6 bg-amber-400 rounded flex items-center justify-center text-gray-900 font-bold text-xs shadow-md"
                        :title "Fate"}
                  fate)))
          ($ :h2 {:class "text-white font-bold text-lg truncate pr-16"}
             (if (seq name) name "Card Name")))
       ($ :div {:class "h-32 bg-gradient-to-br from-blue-400 to-purple-500 mx-3 mt-3 rounded border-2 border-gray-600 flex items-center justify-center"}
          (when (seq image-prompt)
            ($ :div {:class "text-white/70 text-xs text-center px-2 italic"}
               image-prompt)))
       (when (or resolved-type (seq card-subtypes))
         ($ :div {:class "flex justify-center items-center gap-1 mt-1 flex-wrap"}
            (when resolved-type
              ($ card-type-badge {:card-type resolved-type}))
            (when (seq card-subtypes)
              ($ subtypes-display {:subtypes card-subtypes}))))
       ($ :div {:class "flex-1 mx-3 my-2 bg-white/80 rounded p-2 overflow-y-auto text-sm"}
          (when display-component
            ($ display-component {:card card})))
       ($ :div {:class "bg-gray-800 px-3 py-1 text-gray-400 text-xs"}
          (or resolved-set-slug "—")))))
