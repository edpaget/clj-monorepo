(ns bashketball-editor-ui.components.ui.select
  "Select dropdown component following shadcn/ui patterns.

  Provides an accessible select dropdown using Radix UI primitives."
  (:require
   ["@radix-ui/react-select" :as SelectPrimitive]
   ["clsx" :refer [clsx]]
   ["lucide-react" :refer [Check ChevronDown]]
   ["tailwind-merge" :refer [twMerge]]
   [uix.core :refer [$ defui]]))

(defn cn
  "Merges class names using clsx and tailwind-merge."
  [& classes]
  (twMerge (apply clsx (filter some? classes))))

(def select-trigger-classes
  "Base CSS classes for select trigger."
  "flex h-9 w-full items-center justify-between whitespace-nowrap rounded-md border border-gray-200 bg-transparent px-3 py-2 text-sm shadow-sm ring-offset-white placeholder:text-gray-500 focus:outline-none focus:ring-1 focus:ring-gray-950 disabled:cursor-not-allowed disabled:opacity-50")

(def select-content-classes
  "Base CSS classes for select content dropdown."
  "relative z-50 max-h-96 min-w-[8rem] overflow-hidden rounded-md border border-gray-200 bg-white text-gray-950 shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2")

(def select-item-classes
  "Base CSS classes for select item."
  "relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none focus:bg-gray-100 focus:text-gray-900 data-[disabled]:pointer-events-none data-[disabled]:opacity-50")

(defui select-trigger
  "The trigger button that opens the select dropdown."
  [{:keys [class placeholder children id]}]
  ($ SelectPrimitive/Trigger
     {:class (cn select-trigger-classes class)
      :id id}
     ($ SelectPrimitive/Value {:placeholder placeholder}
        children)
     ($ SelectPrimitive/Icon {:asChild true}
        ($ ChevronDown {:className "h-4 w-4 opacity-50"}))))

(defui select-content
  "The dropdown content container."
  [{:keys [class children position]
    :or {position "popper"}}]
  ($ SelectPrimitive/Portal
     ($ SelectPrimitive/Content
        {:class (cn select-content-classes
                    (when (= position "popper")
                      "data-[side=bottom]:translate-y-1 data-[side=left]:-translate-x-1 data-[side=right]:translate-x-1 data-[side=top]:-translate-y-1")
                    class)
         :position position}
        ($ SelectPrimitive/Viewport
           {:class (cn "p-1"
                       (when (= position "popper")
                         "h-[var(--radix-select-trigger-height)] w-full min-w-[var(--radix-select-trigger-width)]"))}
           children))))

(defui select-item
  "An individual selectable item in the dropdown."
  [{:keys [value class disabled children]}]
  ($ SelectPrimitive/Item
     {:class (cn select-item-classes class)
      :value value
      :disabled disabled}
     ($ :span {:class "absolute right-2 flex h-3.5 w-3.5 items-center justify-center"}
        ($ SelectPrimitive/ItemIndicator
           ($ Check {:className "h-4 w-4"})))
     ($ SelectPrimitive/ItemText children)))

(defui select-label
  "A label for a group of items."
  [{:keys [class children]}]
  ($ SelectPrimitive/Label
     {:class (cn "px-2 py-1.5 text-sm font-semibold" class)}
     children))

(defui select-separator
  "A visual separator between items."
  [{:keys [class]}]
  ($ SelectPrimitive/Separator
     {:class (cn "-mx-1 my-1 h-px bg-gray-100" class)}))

(defui select
  "A complete select dropdown component.

  Props:
  - `:value` - Controlled selected value
  - `:default-value` - Uncontrolled default value
  - `:on-value-change` - Callback when value changes (fn [value])
  - `:placeholder` - Placeholder text when no value selected
  - `:disabled` - Disable the select
  - `:options` - Vector of options, each {:value \"val\" :label \"Label\"}
  - `:class` - Additional CSS classes for the trigger
  - `:id` - ID for the trigger element
  - `:name` - Name for form submission

  Example:
  ```clojure
  ($ select
     {:placeholder \"Select a fruit...\"
      :options [{:value \"apple\" :label \"Apple\"}
                {:value \"banana\" :label \"Banana\"}]
      :on-value-change #(println \"Selected:\" %)})
  ```"
  [{:keys [value default-value on-value-change placeholder disabled options class id name]}]
  ($ SelectPrimitive/Root
     (cond-> {:disabled disabled
              :name name}
       (some? value) (assoc :value value)
       (and (nil? value) default-value) (assoc :defaultValue default-value)
       on-value-change (assoc :onValueChange on-value-change))
     ($ select-trigger {:class class :placeholder placeholder :id id})
     ($ select-content
        (for [{:keys [value label]} options]
          ($ select-item {:key value :value value} label)))))
