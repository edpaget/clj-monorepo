(ns basketball-editor-ui.components.ui.button
  "Button component following shadcn/ui patterns.

  Provides multiple variants and sizes with consistent styling."
  (:require
   ["class-variance-authority" :refer [cva]]
   ["clsx" :refer [clsx]]
   ["tailwind-merge" :refer [twMerge]]
   [uix.core :refer [$ defui]]))

(defn cn
  "Merges class names using clsx and tailwind-merge."
  [& classes]
  (twMerge (apply clsx (filter some? classes))))

(def button-variants
  "CVA configuration for button variants and sizes."
  (cva
   "inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-gray-950 disabled:pointer-events-none disabled:opacity-50"
   #js {:variants
        #js {:variant
             #js {:default "bg-gray-900 text-gray-50 shadow hover:bg-gray-900/90"
                  :destructive "bg-red-500 text-gray-50 shadow-sm hover:bg-red-500/90"
                  :outline "border border-gray-200 bg-white shadow-sm hover:bg-gray-100 hover:text-gray-900"
                  :secondary "bg-gray-100 text-gray-900 shadow-sm hover:bg-gray-100/80"
                  :ghost "hover:bg-gray-100 hover:text-gray-900"
                  :link "text-gray-900 underline-offset-4 hover:underline"}
             :size
             #js {:default "h-9 px-4 py-2"
                  :sm "h-8 rounded-md px-3 text-xs"
                  :lg "h-10 rounded-md px-8"
                  :icon "h-9 w-9"}}
        :defaultVariants
        #js {:variant "default"
             :size "default"}}))

(defui button
  "A button component with multiple variants and sizes.

  Props:
  - `:variant` - Visual style: :default, :destructive, :outline, :secondary, :ghost, :link
  - `:size` - Button size: :default, :sm, :lg, :icon
  - `:class` - Additional CSS classes
  - `:on-click` - Click handler
  - `:disabled` - Disable the button
  - `:type` - Button type (button, submit, reset)

  Children are rendered as button content."
  [{:keys [variant size class on-click disabled type children]
    :or {variant :default size :default type "button"}}]
  ($ :button
     {:type type
      :class (cn (button-variants #js {:variant (name variant)
                                        :size (name size)})
                 class)
      :on-click on-click
      :disabled disabled}
     children))
