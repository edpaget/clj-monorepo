(ns bashketball-editor-ui.views.card-view
  "Card detail view for displaying a single card."
  (:require
   ["@apollo/client" :refer [useQuery]]
   ["lucide-react" :refer [ArrowLeft Edit]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.components.ui.loading :refer [spinner]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui]]))

;; -----------------------------------------------------------------------------
;; Display components
;; -----------------------------------------------------------------------------

(defui stat-display
  "Display a stat value with label."
  [{:keys [label value]}]
  ($ :div {:class "text-center"}
     ($ :div {:class "text-sm font-bold text-gray-900"} value)
     ($ :div {:class "text-[10px] text-gray-500 uppercase"} label)))

(defui text-block
  "Display a text block with label."
  [{:keys [label value]}]
  (when value
    ($ :div {:class "space-y-0.5"}
       ($ :div {:class "text-[10px] font-medium text-gray-500 uppercase"} label)
       ($ :div {:class "text-gray-900 whitespace-pre-wrap leading-tight"} value))))

(defui abilities-list
  "Display a list of abilities."
  [{:keys [abilities]}]
  (when (seq abilities)
    ($ :div {:class "space-y-0.5"}
       ($ :div {:class "text-[10px] font-medium text-gray-500 uppercase"} "Abilities")
       ($ :ul {:class "list-disc list-inside space-y-0.5"}
          (for [[idx ability] (map-indexed vector abilities)]
            ($ :li {:key idx :class "text-gray-900 leading-tight"} ability))))))

(defui card-type-badge
  "Display the card type as a badge."
  [{:keys [card-type]}]
  (let [type-label (case card-type
                     "PlayerCard" "Player"
                     "PlayCard" "Play"
                     "AbilityCard" "Ability"
                     "SplitPlayCard" "Split Play"
                     "CoachingCard" "Coaching"
                     "TeamAssetCard" "Team Asset"
                     "StandardActionCard" "Standard Action"
                     card-type)]
    ($ :span {:class "inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium bg-gray-700 text-gray-100"}
       type-label)))

;; -----------------------------------------------------------------------------
;; Type-specific card displays
;; -----------------------------------------------------------------------------

(defui player-card-display
  "Display player card specific fields."
  [{:keys [^js card]}]
  ($ :<>
     ($ :div {:class "grid grid-cols-5 gap-1 p-1.5 bg-gray-100 rounded"}
        ($ stat-display {:label "SHT" :value (.-sht card)})
        ($ stat-display {:label "PSS" :value (.-pss card)})
        ($ stat-display {:label "DEF" :value (.-def card)})
        ($ stat-display {:label "SPD" :value (.-speed card)})
        ($ stat-display {:label "SIZE" :value (.-size card)}))
     ($ :div {:class "flex items-center gap-1 text-[10px] text-gray-500 mt-1"}
        ($ :span "Deck:")
        ($ :span {:class "font-medium text-gray-900"} (.-deckSize card)))
     ($ abilities-list {:abilities (js->clj (.-abilities card))})))

(defui play-card-display
  "Display play card specific fields."
  [{:keys [^js card]}]
  ($ text-block {:label "Play" :value (.-play card)}))

(defui ability-card-display
  "Display ability card specific fields."
  [{:keys [^js card]}]
  ($ abilities-list {:abilities (js->clj (.-abilities card))}))

(defui split-play-card-display
  "Display split play card specific fields."
  [{:keys [^js card]}]
  ($ :div {:class "grid grid-cols-2 gap-1"}
     ($ :div {:class "p-1 bg-green-100 rounded"}
        ($ text-block {:label "Offense" :value (.-offense card)}))
     ($ :div {:class "p-1 bg-red-100 rounded"}
        ($ text-block {:label "Defense" :value (.-defense card)}))))

(defui coaching-card-display
  "Display coaching card specific fields."
  [{:keys [^js card]}]
  ($ text-block {:label "Coaching" :value (.-coaching card)}))

(defui team-asset-card-display
  "Display team asset card specific fields."
  [{:keys [^js card]}]
  ($ text-block {:label "Asset Power" :value (.-assetPower card)}))

(defui standard-action-card-display
  "Display standard action card specific fields."
  [{:keys [^js card]}]
  ($ :div {:class "grid grid-cols-2 gap-1"}
     ($ :div {:class "p-1 bg-green-100 rounded"}
        ($ text-block {:label "Offense" :value (.-offense card)}))
     ($ :div {:class "p-1 bg-red-100 rounded"}
        ($ text-block {:label "Defense" :value (.-defense card)}))))

;; -----------------------------------------------------------------------------
;; Main card view
;; -----------------------------------------------------------------------------

(def type-display-components
  {"PlayerCard" player-card-display
   "PlayCard" play-card-display
   "AbilityCard" ability-card-display
   "SplitPlayCard" split-play-card-display
   "CoachingCard" coaching-card-display
   "TeamAssetCard" team-asset-card-display
   "StandardActionCard" standard-action-card-display})

(defui card-preview
  "Display the card in MTG card aspect ratio (2.5:3.5 = 5:7)."
  [{:keys [^js card]}]
  (let [card-type         (.-__typename card)
        display-component (get type-display-components card-type)
        fate              (.-fate card)]
    ($ :div {:class "aspect-[5/7] w-80 bg-gradient-to-b from-gray-100 to-gray-200 rounded-xl border-4 border-gray-800 shadow-xl overflow-hidden flex flex-col"}
       ;; Card header with name
       ($ :div {:class "bg-gradient-to-r from-gray-700 to-gray-800 px-4 py-2 relative"}
          (when fate
            ($ :div {:class "absolute top-2 right-3 w-8 h-8 bg-amber-400 rounded-full flex items-center justify-center text-gray-900 font-bold text-lg shadow-md"}
               fate))
          ($ :h2 {:class "text-white font-bold text-lg truncate pr-10"} (.-name card)))
       ;; Card image area (placeholder)
       ($ :div {:class "h-32 bg-gradient-to-br from-blue-400 to-purple-500 mx-3 mt-3 rounded border-2 border-gray-600 flex items-center justify-center"}
          (when (.-imagePrompt card)
            ($ :div {:class "text-white/70 text-xs text-center px-2 italic"}
               (.-imagePrompt card))))
       ;; Card type badge
       ($ :div {:class "flex justify-center mt-1"}
          ($ card-type-badge {:card-type card-type}))
       ;; Card content area
       ($ :div {:class "flex-1 mx-3 my-2 bg-white/80 rounded p-2 overflow-y-auto text-xs"}
          (when display-component
            ($ display-component {:card card})))
       ;; Card footer with set info
       ($ :div {:class "bg-gray-800 px-3 py-1 text-gray-400 text-xs"}
          (.-setSlug card)))))

(defui card-view
  "Main card detail view."
  []
  (let [^js params (router/use-params)
        navigate   (router/use-navigate)
        slug       (:slug params)
        set-slug   (:setSlug params)

        card-query (useQuery q/CARD_QUERY
                             (clj->js {:variables {:slug (or slug "")
                                                   :setSlug (or set-slug "")}
                                       :skip (or (nil? slug) (nil? set-slug))}))
        loading?   (:loading card-query)
        error      (:error card-query)
        card       (some-> card-query :data :card)]

    ($ :div {:class "max-w-4xl mx-auto"}
       ;; Header
       ($ :div {:class "flex items-center justify-between mb-6"}
          ($ :div {:class "flex items-center gap-4"}
             ($ button {:variant :ghost
                        :on-click #(navigate (if set-slug
                                               (str "/?set=" set-slug)
                                               "/"))}
                ($ ArrowLeft {:className "w-4 h-4 mr-2"})
                "Back")
             (when card
               ($ :h1 {:class "text-2xl font-bold"} (.-name card))))
          (when card
            ($ button {:variant :outline
                       :on-click #(navigate (str "/cards/" set-slug "/" slug "/edit"))}
               ($ Edit {:className "w-4 h-4 mr-2"})
               "Edit")))

       ;; Content
       (cond
         loading?
         ($ :div {:class "flex justify-center py-12"}
            ($ spinner {:size :lg}))

         error
         ($ :div {:class "p-6 bg-red-50 border border-red-200 rounded-lg text-red-700"}
            "Error loading card: " (.-message error))

         (nil? card)
         ($ :div {:class "p-6 bg-yellow-50 border border-yellow-200 rounded-lg text-yellow-700"}
            "Card not found. Make sure you have the correct set selected.")

         :else
         ($ :div {:class "flex justify-center"}
            ($ card-preview {:card card}))))))
