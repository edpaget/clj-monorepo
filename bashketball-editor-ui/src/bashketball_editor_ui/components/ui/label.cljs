(ns bashketball-editor-ui.components.ui.label
  "Label component following shadcn/ui patterns."
  (:require
   ["clsx" :refer [clsx]]
   ["tailwind-merge" :refer [twMerge]]
   [uix.core :refer [$ defui]]))

(defn cn
  "Merges class names using clsx and tailwind-merge."
  [& classes]
  (twMerge (apply clsx (filter some? classes))))

(defui label
  "A label component for form fields.

  Props:
  - `:for` - ID of the associated form element
  - `:class` - Additional CSS classes
  - `:children` - Label text content"
  [{:keys [for class children]}]
  ($ :label
     {:htmlFor for
      :class (cn "text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70" class)}
     children))
