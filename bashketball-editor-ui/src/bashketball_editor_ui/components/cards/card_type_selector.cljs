(ns bashketball-editor-ui.components.cards.card-type-selector
  "Card type selector dropdown component.

   Card types are derived from [[bashketball-schemas.enums/CardType]]
   to ensure consistency with the backend."
  (:require
   [bashketball-schemas.enums :as enums]
   [bashketball-ui.components.select :refer [select]]
   [bashketball-ui.router :as router]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(def all-types-value "__all__")

(def card-type-labels
  "Human-readable labels for card types."
  {:card-type/PLAYER_CARD "Player"
   :card-type/PLAY_CARD "Play"
   :card-type/ABILITY_CARD "Ability"
   :card-type/SPLIT_PLAY_CARD "Split Play"
   :card-type/COACHING_CARD "Coaching"
   :card-type/TEAM_ASSET_CARD "Team Asset"
   :card-type/STANDARD_ACTION_CARD "Standard Action"})

(def card-types
  "Card type options derived from bashketball-schemas."
  (mapv (fn [ct]
          {:value (name ct)
           :label (get card-type-labels ct (-> ct name (str/replace "_" " ")))})
        (enums/enum-values enums/CardType)))

(defui card-type-selector
  "Dropdown selector for filtering cards by type.

  Props:
  - `:current-card-type` - Currently selected card type (optional)
  - `:class` - Additional CSS classes"
  [{:keys [current-card-type class]}]
  (let [[search-params set-search-params] (router/use-search-params)
        options                           (into [{:value all-types-value :label "All Types"}] card-types)]
    ($ select
       {:placeholder "Select a type..."
        :value (or current-card-type all-types-value)
        :options options
        :class class
        :on-value-change (fn [value]
                           (let [current-set (.get search-params "set")
                                 new-params  (cond-> {}
                                               current-set (assoc :set current-set)
                                               (not= value all-types-value) (assoc :type value))]
                             (set-search-params (clj->js new-params))))})))
