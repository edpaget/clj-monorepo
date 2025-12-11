(ns bashketball-editor-ui.components.cards.set-selector
  "Set selector dropdown component.

  Provides a dropdown to select and navigate to different card sets."
  (:require
   ["@radix-ui/react-select" :as SelectPrimitive]
   ["lucide-react" :refer [Plus Settings]]
   [bashketball-editor-ui.context.auth :refer [use-auth]]
   [bashketball-editor-ui.hooks.sets :refer [use-sets]]
   [bashketball-ui.components.select :as s]
   [bashketball-ui.router :as router]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def all-sets-value "__all__")
(def create-new-value "__create__")
(def manage-sets-value "__manage__")

(defui set-selector
  "Dropdown selector for navigating between card sets.

  Props:
  - `:current-set-slug` - Currently selected set slug (optional)
  - `:class` - Additional CSS classes"
  [{:keys [current-set-slug class]}]
  (let [{:keys [logged-in?]}              (use-auth)
        {:keys [sets loading?]}           (use-sets)
        [search-params set-search-params] (router/use-search-params)
        navigate                          (router/use-navigate)
        set-options                       (mapv (fn [{:keys [slug name]}]
                                                  {:value slug :label name})
                                                sets)]
    ($ SelectPrimitive/Root
       {:value (or current-set-slug all-sets-value)
        :disabled loading?
        :onValueChange (fn [value]
                         (cond
                           (= value create-new-value)
                           (navigate "/sets/new")

                           (= value manage-sets-value)
                           (navigate "/sets/manage")

                           :else
                           (let [current-type (.get search-params "type")
                                 new-params   (cond-> {}
                                                (not= value all-sets-value) (assoc :set value)
                                                current-type (assoc :type current-type))]
                             (set-search-params (clj->js new-params)))))}
       ($ s/select-trigger {:class class
                            :placeholder (if loading? "Loading sets..." "Select a set...")})
       ($ s/select-content
          ($ s/select-item {:value all-sets-value} "All Sets")
          (when (seq set-options)
            ($ s/select-separator))
          (for [{:keys [value label]} set-options]
            ($ s/select-item {:key value :value value} label))
          (when logged-in?
            ($ :<>
               ($ s/select-separator)
               ($ SelectPrimitive/Item
                  {:class (cn s/select-item-classes "text-blue-600 font-medium")
                   :value create-new-value}
                  ($ :span {:class "flex items-center gap-2"}
                     ($ Plus {:className "w-4 h-4"})
                     "Create New Set"))
               ($ SelectPrimitive/Item
                  {:class (cn s/select-item-classes "text-gray-600")
                   :value manage-sets-value}
                  ($ :span {:class "flex items-center gap-2"}
                     ($ Settings {:className "w-4 h-4"})
                     "Manage Sets"))))))))
