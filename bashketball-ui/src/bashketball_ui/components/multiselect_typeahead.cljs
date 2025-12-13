(ns bashketball-ui.components.multiselect-typeahead
  "Type-ahead multiselect component for selecting multiple items from a list.

  Provides a searchable dropdown with selected items displayed as removable tags."
  (:require
   ["@radix-ui/react-popover" :as Popover]
   ["lucide-react" :refer [Check ChevronDown X]]
   [bashketball-ui.utils :refer [cn]]
   [clojure.string :as str]
   [uix.core :refer [$ defui use-state use-ref use-effect]]))

(defui tag
  "A removable tag/chip for displaying selected items."
  [{:keys [label on-remove]}]
  ($ :span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-blue-100 text-blue-800 text-sm"}
     label
     ($ :button {:type "button"
                 :class "hover:bg-blue-200 rounded-full p-0.5"
                 :on-click (fn [e]
                             (.stopPropagation e)
                             (on-remove))}
        ($ X {:className "h-3 w-3"}))))

(defui option-item
  "An individual option in the dropdown list."
  [{:keys [label selected? on-select]}]
  ($ :div {:class (cn "flex items-center gap-2 px-3 py-2 cursor-pointer rounded-sm"
                      "hover:bg-gray-100"
                      (when selected? "bg-gray-50"))
           :on-click on-select}
     ($ :div {:class (cn "w-4 h-4 border rounded flex items-center justify-center"
                         (if selected?
                           "bg-blue-600 border-blue-600"
                           "border-gray-300"))}
        (when selected?
          ($ Check {:className "h-3 w-3 text-white"})))
     ($ :span {:class "text-sm"} label)))

(defui multiselect-typeahead
  "A type-ahead multiselect component.

  Props:
  - `:value` - Vector of selected values (strings)
  - `:options` - Vector of options, each {:value \"val\" :label \"Label\"}
  - `:on-change` - Callback when selection changes (fn [new-values])
  - `:placeholder` - Placeholder text when no items selected
  - `:search-placeholder` - Placeholder for the search input
  - `:class` - Additional CSS classes for the trigger
  - `:id` - ID for the component
  - `:disabled` - Disable the component"
  [{:keys [value options on-change placeholder search-placeholder class id disabled]
    :or {placeholder "Select items..."
         search-placeholder "Search..."}}]
  (let [[open? set-open!] (use-state false)
        [search set-search!] (use-state "")
        input-ref (use-ref nil)
        selected-set (set (or value []))
        filtered-options (if (str/blank? search)
                           options
                           (filter (fn [{:keys [label]}]
                                     (str/includes? (str/lower-case label)
                                                    (str/lower-case search)))
                                   options))
        selected-options (filter #(selected-set (:value %)) options)
        _ (use-effect
           (fn []
             (when (and open? @input-ref)
               (.focus @input-ref)))
           [open?])]
    ($ Popover/Root {:open open? :onOpenChange set-open!}
       ($ Popover/Trigger {:asChild true :disabled disabled}
          ($ :button {:type "button"
                      :id id
                      :class (cn "flex min-h-[36px] w-full items-center justify-between rounded-md border border-gray-200 bg-white px-3 py-2 text-sm shadow-sm ring-offset-white focus:outline-none focus:ring-1 focus:ring-gray-950 disabled:cursor-not-allowed disabled:opacity-50"
                                 class)}
             ($ :div {:class "flex flex-wrap gap-1 flex-1"}
                (if (seq selected-options)
                  (for [{:keys [value label]} selected-options]
                    ($ tag {:key value
                            :label label
                            :on-remove (fn []
                                         (on-change (vec (remove #{value} (or value [])))))}))
                  ($ :span {:class "text-gray-500"} placeholder)))
             ($ ChevronDown {:className "h-4 w-4 opacity-50 flex-shrink-0 ml-2"})))
       ($ Popover/Portal
          ($ Popover/Content
             {:class "z-50 w-[var(--radix-popover-trigger-width)] rounded-md border border-gray-200 bg-white shadow-md"
              :sideOffset 4
              :align "start"}
             ($ :div {:class "p-2 border-b border-gray-100"}
                ($ :input {:ref input-ref
                           :type "text"
                           :class "w-full px-3 py-2 text-sm border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-950"
                           :placeholder search-placeholder
                           :value search
                           :on-change #(set-search! (.. % -target -value))}))
             ($ :div {:class "max-h-60 overflow-y-auto p-1"}
                (if (seq filtered-options)
                  (for [{:keys [value label]} filtered-options]
                    ($ option-item
                       {:key value
                        :label label
                        :selected? (contains? selected-set value)
                        :on-select (fn []
                                     (let [current (or (some-> on-change meta :value) value [])
                                           new-val (if (contains? selected-set value)
                                                     (vec (remove #{value} current))
                                                     (conj (vec current) value))]
                                       (on-change new-val)))}))
                  ($ :div {:class "px-3 py-2 text-sm text-gray-500"}
                     "No results found"))))))))
