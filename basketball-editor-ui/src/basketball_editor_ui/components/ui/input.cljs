(ns basketball-editor-ui.components.ui.input
  "Input component following shadcn/ui patterns.

  Provides a styled text input with consistent appearance."
  (:require
   ["clsx" :refer [clsx]]
   ["tailwind-merge" :refer [twMerge]]
   [uix.core :refer [$ defui]]))

(defn cn
  "Merges class names using clsx and tailwind-merge."
  [& classes]
  (twMerge (apply clsx (filter some? classes))))

(def input-base-classes
  "Base CSS classes for input styling."
  "flex h-9 w-full rounded-md border border-gray-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-gray-950 disabled:cursor-not-allowed disabled:opacity-50")

(defui input
  "A text input component with consistent styling.

  Props:
  - `:type` - Input type (text, email, password, etc.)
  - `:placeholder` - Placeholder text
  - `:value` - Controlled value
  - `:default-value` - Uncontrolled default value
  - `:on-change` - Change handler
  - `:disabled` - Disable the input
  - `:class` - Additional CSS classes
  - `:id` - Input ID for label association
  - `:name` - Input name for form submission"
  [{:keys [type placeholder value default-value on-change disabled class id name]}]
  ($ :input
     (cond-> {:type (or type "text")
              :class (cn input-base-classes class)}
       placeholder (assoc :placeholder placeholder)
       (some? value) (assoc :value value)
       (and (nil? value) default-value) (assoc :default-value default-value)
       on-change (assoc :on-change on-change)
       disabled (assoc :disabled disabled)
       id (assoc :id id)
       name (assoc :name name))))
