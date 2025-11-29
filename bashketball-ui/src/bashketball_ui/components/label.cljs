(ns bashketball-ui.components.label
  "Label component following shadcn/ui patterns."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

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
