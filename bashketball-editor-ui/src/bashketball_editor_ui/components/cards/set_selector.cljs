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
  (prn "CURRENT_SET" current-set-slug)
  (let [{:keys [sets loading?]} (use-sets)
        navigate (router/use-navigate)
        options (into [{:value all-sets-value :label "All Sets"}]
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
                           (if (= value all-sets-value)
                             (navigate "/")
                             (navigate (str "/sets/" value))))})))
