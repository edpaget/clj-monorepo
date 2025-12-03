(ns bashketball-game-ui.components.deck.card-selector
  "Card selector component for deck building.

  Displays available cards with filtering and allows adding to deck."
  (:require
   ["lucide-react" :refer [Minus Plus Search]]
   [bashketball-game-ui.hooks.use-cards :refer [use-cards use-sets]]
   [bashketball-game-ui.schemas.deck :as deck-schema]
   [bashketball-ui.cards.card-list-item :refer [card-list-item card-list-item-skeleton]]
   [bashketball-ui.components.input :refer [input]]
   [bashketball-ui.components.select :refer [select]]
   [bashketball-ui.utils :refer [cn]]
   [clojure.string :as str]
   [uix.core :refer [$ defui use-state use-memo]]))

(def ^:private all-value
  "Sentinel value for 'all' option since Radix Select doesn't allow empty strings."
  "__all__")

(def card-type-options
  "Options for card type filter."
  [{:value all-value :label "All Types"}
   {:value ":card-type/PLAYER_CARD" :label "Player"}
   {:value ":card-type/STANDARD_ACTION_CARD" :label "Action"}
   {:value ":card-type/SPLIT_PLAY_CARD" :label "Split Play"}
   {:value ":card-type/COACHING_CARD" :label "Coaching"}
   {:value ":card-type/TEAM_ASSET_CARD" :label "Team Asset"}
   {:value ":card-type/PLAY_CARD" :label "Play"}
   {:value ":card-type/ABILITY_CARD" :label "Ability"}])

(defn- normalize-filter
  "Converts the sentinel all-value back to nil for filtering.
  For type filters, converts string representation to keyword."
  [v]
  (when (and v (not= v all-value))
    (if (str/starts-with? v ":")
      (keyword (subs v 1))
      v)))

(defn- standard-action-card?
  "Returns true if the card is a STANDARD_ACTION_CARD (unlimited copies allowed)."
  [card]
  (= (:card-type card) :card-type/STANDARD_ACTION_CARD))

(defui card-selector-item
  "A card item in the selector with add and remove buttons."
  [{:keys [card on-add on-remove copies-in-deck max-copies]}]
  (let [unlimited?  (standard-action-card? card)
        at-max?     (and (not unlimited?) (>= copies-in-deck max-copies))
        can-remove? (pos? copies-in-deck)]
    ($ :div {:class "flex items-center border-b border-gray-100 hover:bg-gray-50"}
       ($ :div {:class "flex-1"}
          ($ card-list-item
             {:card card
              :on-click nil}))
       ($ :div {:class "flex items-center gap-2 px-4 shrink-0"}
          ($ :span {:class (cn "text-sm text-gray-500 min-w-[2.5rem] text-right"
                               (when-not can-remove? "invisible"))}
             (if unlimited?
               (str copies-in-deck "/∞")
               (str copies-in-deck "/" max-copies)))
          ($ :button
             {:class (cn "w-8 h-8 rounded-full flex items-center justify-center transition-colors"
                         (if can-remove?
                           "bg-red-100 text-red-600 hover:bg-red-200"
                           "bg-gray-100 text-gray-400 cursor-not-allowed"))
              :disabled (not can-remove?)
              :on-click #(when can-remove? (on-remove card))
              :title (if can-remove? "Remove from deck" "Not in deck")}
             ($ Minus {:className "w-4 h-4"}))
          ($ :button
             {:class (cn "w-8 h-8 rounded-full flex items-center justify-center transition-colors"
                         (if at-max?
                           "bg-gray-100 text-gray-400 cursor-not-allowed"
                           "bg-blue-500 text-white hover:bg-blue-600"))
              :disabled at-max?
              :on-click #(when-not at-max? (on-add card))
              :title (if at-max? "Maximum copies reached" "Add to deck")}
             ($ Plus {:className "w-4 h-4"}))))))

(defui card-selector
  "Card catalog browser for adding and removing cards from a deck.

  Props:
  - `:deck-slugs` - Vector of card slugs currently in the deck
  - `:on-add-card` - Callback when a card is added (receives card)
  - `:on-remove-card` - Callback when a card is removed (receives card)
  - `:class` - Additional CSS classes"
  [{:keys [deck-slugs on-add-card on-remove-card class]}]
  (let [[search set-search]           (use-state "")
        [set-filter set-set-filter]   (use-state all-value)
        [type-filter set-type-filter] (use-state all-value)
        set-filter-normalized         (normalize-filter set-filter)
        type-filter-normalized        (normalize-filter type-filter)
        {:keys [cards loading]}       (use-cards set-filter-normalized)
        {:keys [sets]}                (use-sets)
        slug-counts                   (use-memo
                                       (fn [] (frequencies deck-slugs))
                                       [deck-slugs])
        set-options                   (use-memo
                                       (fn []
                                         (into [{:value all-value :label "All Sets"}]
                                               (map (fn [s] {:value (:slug s) :label (:name s)}))
                                               sets))
                                       [sets])
        filtered-cards                (use-memo
                                       (fn []
                                         (cond->> cards
                                           type-filter-normalized
                                           (filter (fn [c]
                                                     (= (:card-type c) type-filter-normalized)))
                                           (seq search)
                                           (filter (fn [c]
                                                     (str/includes?
                                                      (str/lower-case (or (:name c) ""))
                                                      (str/lower-case search))))))
                                       [cards type-filter-normalized search])]
    ($ :div {:class (cn "flex flex-col h-full bg-white rounded-lg shadow border border-gray-200" class)}
       ($ :div {:class "p-4 border-b border-gray-200 space-y-3"}
          ($ :h3 {:class "font-semibold text-gray-900"} "Card Catalog")
          ($ :div {:class "relative"}
             ($ Search {:className "absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"})
             ($ input
                {:placeholder "Search cards..."
                 :value search
                 :on-change #(set-search (.. % -target -value))
                 :class "pl-9"}))
          ($ :div {:class "grid grid-cols-2 gap-2"}
             ($ select
                {:value set-filter
                 :on-value-change set-set-filter
                 :options set-options
                 :placeholder "All Sets"})
             ($ select
                {:value type-filter
                 :on-value-change set-type-filter
                 :options card-type-options
                 :placeholder "All Types"})))
       ($ :div {:class "flex-1 overflow-y-auto"}
          (if loading
            ($ :div {:class "divide-y divide-gray-100"}
               (for [i (range 5)]
                 ($ card-list-item-skeleton {:key i})))
            (if (seq filtered-cards)
              (for [card filtered-cards]
                ($ card-selector-item
                   {:key (:slug card)
                    :card card
                    :copies-in-deck (get slug-counts (:slug card) 0)
                    :max-copies (:max-copies-per-card deck-schema/deck-rules)
                    :on-add on-add-card
                    :on-remove on-remove-card}))
              ($ :div {:class "p-8 text-center text-gray-500"}
                 "No cards match your filters")))))))
