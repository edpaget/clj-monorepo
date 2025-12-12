(ns bashketball-game-ui.components.deck.card-selector
  "Card selector component for deck building.

  Displays available cards with filtering and allows adding to deck."
  (:require
   ["lucide-react" :refer [ChevronDown Minus Plus Search]]
   [bashketball-game-ui.hooks.use-cards :refer [use-cards use-sets]]
   [bashketball-game-ui.schemas.deck :as deck-schema]
   [bashketball-ui.cards.card-list-item :refer [card-list-item card-list-item-skeleton]]
   [bashketball-ui.cards.card-preview :refer [card-preview]]
   [bashketball-ui.components.input :refer [input]]
   [bashketball-ui.components.select :refer [select]]
   [bashketball-ui.utils :refer [cn]]
   [clojure.string :as str]
   [uix.core :refer [$ defui use-state use-memo]]))

(def ^:private all-value
  "Sentinel value for 'all' option since Radix Select doesn't allow empty strings."
  "__all__")

(def card-type-options
  "Options for card type filter.
  Values are GraphQL enum values (SCREAMING_SNAKE_CASE)."
  [{:value all-value :label "All Types"}
   {:value "PLAYER_CARD" :label "Player"}
   {:value "STANDARD_ACTION_CARD" :label "Action"}
   {:value "SPLIT_PLAY_CARD" :label "Split Play"}
   {:value "COACHING_CARD" :label "Coaching"}
   {:value "TEAM_ASSET_CARD" :label "Team Asset"}
   {:value "PLAY_CARD" :label "Play"}
   {:value "ABILITY_CARD" :label "Ability"}])

(defn- normalize-filter
  "Converts the sentinel all-value back to nil for filtering."
  [v]
  (when (and v (not= v all-value))
    v))

(defn- standard-action-card?
  "Returns true if the card is a STANDARD_ACTION_CARD (unlimited copies allowed)."
  [card]
  (= (:card-type card) :card-type/STANDARD_ACTION_CARD))

(defui card-selector-item
  "A card item in the selector with add/remove buttons and expandable preview.

  Props:
  - `:card` - Card data map
  - `:on-add` - Callback when add button clicked
  - `:on-remove` - Callback when remove button clicked
  - `:copies-in-deck` - Number of copies currently in deck
  - `:max-copies` - Maximum copies allowed
  - `:expanded?` - Whether the card preview is expanded
  - `:on-toggle-expand` - Callback to toggle expansion"
  [{:keys [card on-add on-remove copies-in-deck max-copies expanded? on-toggle-expand]}]
  (let [unlimited?  (standard-action-card? card)
        at-max?     (and (not unlimited?) (>= copies-in-deck max-copies))
        can-remove? (pos? copies-in-deck)]
    ($ :<>
       ($ :div {:class (cn "flex items-center border-b border-gray-100 hover:bg-gray-50 cursor-pointer"
                           (when expanded? "bg-blue-50"))}
          ($ :div {:class "flex-1"
                   :on-click #(on-toggle-expand card)}
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
                ($ Plus {:className "w-4 h-4"}))
             ($ :button
                {:class (cn "w-6 h-6 flex items-center justify-center text-gray-400 hover:text-gray-600 transition-transform"
                            (when expanded? "rotate-180"))
                 :on-click #(on-toggle-expand card)
                 :title (if expanded? "Collapse preview" "Expand preview")}
                ($ ChevronDown {:className "w-4 h-4"}))))
       (when expanded?
         ($ :div {:class "p-4 bg-gray-50 border-b border-gray-200 flex justify-center"}
            ($ card-preview {:card card}))))))

(defui card-selector
  "Card catalog browser for adding and removing cards from a deck.

  Props:
  - `:deck-slugs` - Vector of card slugs currently in the deck
  - `:on-add-card` - Callback when a card is added (receives card)
  - `:on-remove-card` - Callback when a card is removed (receives card)
  - `:class` - Additional CSS classes"
  [{:keys [deck-slugs on-add-card on-remove-card class]}]
  (let [[search set-search]               (use-state "")
        [set-filter set-set-filter]       (use-state all-value)
        [type-filter set-type-filter]     (use-state all-value)
        [expanded-slug set-expanded-slug] (use-state nil)
        set-filter-normalized         (normalize-filter set-filter)
        type-filter-normalized        (normalize-filter type-filter)
        {:keys [cards loading]}       (use-cards {:set-slug set-filter-normalized
                                                  :card-type type-filter-normalized})
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
                                           (seq search)
                                           (filter (fn [c]
                                                     (str/includes?
                                                      (str/lower-case (or (:name c) ""))
                                                      (str/lower-case search))))))
                                       [cards search])]
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
                    :on-remove on-remove-card
                    :expanded? (= expanded-slug (:slug card))
                    :on-toggle-expand (fn [c]
                                        (set-expanded-slug
                                         (when (not= expanded-slug (:slug c))
                                           (:slug c))))}))
              ($ :div {:class "p-8 text-center text-gray-500"}
                 "No cards match your filters")))))))
