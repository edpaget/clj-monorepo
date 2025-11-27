(ns bashketball-editor-ui.components.cards.card-type-selector
  "Card type selector dropdown component."
  (:require
   [bashketball-editor-ui.components.ui.select :refer [select]]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui]]))

(def all-types-value "__all__")

(def card-types
  [{:value "PLAYER_CARD" :label "Player"}
   {:value "PLAY_CARD" :label "Play"}
   {:value "ABILITY_CARD" :label "Ability"}
   {:value "SPLIT_PLAY_CARD" :label "Split Play"}
   {:value "COACHING_CARD" :label "Coaching"}
   {:value "TEAM_ASSET_CARD" :label "Team Asset"}
   {:value "STANDARD_ACTION_CARD" :label "Standard Action"}])

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
