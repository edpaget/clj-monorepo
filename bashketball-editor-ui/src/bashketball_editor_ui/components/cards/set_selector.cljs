(ns bashketball-editor-ui.components.cards.set-selector
  "Set selector dropdown component.

  Provides a dropdown to select and navigate to different card sets."
  (:require
   [bashketball-editor-ui.components.ui.select :refer [select]]
   [bashketball-editor-ui.hooks.sets :refer [use-sets]]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui]]))

(def all-sets-value "__all__")

(defui set-selector
  "Dropdown selector for navigating between card sets.

  Props:
  - `:current-set-slug` - Currently selected set slug (optional)
  - `:class` - Additional CSS classes"
  [{:keys [current-set-slug class]}]
  (let [{:keys [sets loading?]}           (use-sets)
        [search-params set-search-params] (router/use-search-params)
        options                           (into [{:value all-sets-value :label "All Sets"}]
                                                (map (fn [{:keys [slug name]}]
                                                       {:value slug :label name})
                                                     sets))]
    ($ select
       {:placeholder (if loading? "Loading sets..." "Select a set...")
        :value (or current-set-slug all-sets-value)
        :options options
        :disabled loading?
        :class class
        :on-value-change (fn [value]
                           (let [current-type (.get search-params "type")
                                 new-params   (cond-> {}
                                                (not= value all-sets-value) (assoc :set value)
                                                current-type (assoc :type current-type))]
                             (set-search-params (clj->js new-params))))})))
