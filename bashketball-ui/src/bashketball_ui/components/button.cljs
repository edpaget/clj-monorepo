(ns bashketball-ui.components.button
  "Button component following shadcn/ui patterns.

  Provides multiple variants and sizes with consistent styling."
  (:require
   ["class-variance-authority" :refer [cva]]
   ["react" :as react]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$]]))

(def button-variants
  "CVA configuration for button variants and sizes.

  Sizes:
  - sm: Compact size for dense UIs
  - default: Standard size
  - lg: Touch-friendly size (44px min height per Apple HIG)
  - icon: Square icon button"
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
                  :lg "min-h-[44px] h-11 rounded-md px-5 text-base"
                  :icon "h-9 w-9"}}
        :defaultVariants
        #js {:variant "default"
             :size "default"}}))

(def button
  "A button component with multiple variants and sizes.

  Props:
  - `:variant` - Visual style: :default, :destructive, :outline, :secondary, :ghost, :link
  - `:size` - Button size: :default, :sm, :lg, :icon
  - `:class` - Additional CSS classes
  - `:on-click` - Click handler
  - `:disabled` - Disable the button
  - `:type` - Button type (button, submit, reset)
  - `:title` - Tooltip text

  Children are rendered as button content.

  Forwards refs and additional props for compatibility with Radix UI primitives."
  (react/forwardRef
   (fn [^js props ref]
     ;; Keep children as-is from props (don't convert with js->clj)
     (let [children     (.-children props)
           ;; Convert other props for easier access
           variant      (or (.-variant props) "default")
           size         (or (.-size props) "default")
           custom-class (or (.-class props) (.-className props))
           btn-type     (or (.-type props) "button")
           on-click     (.-on-click props)
           variant-name (if (keyword? variant) (name variant) variant)
           size-name    (if (keyword? size) (name size) size)
           button-class (cn (button-variants #js {:variant variant-name :size size-name}) custom-class)
           ;; Build final props: start with original, override with our computed values
           final-props  (js/Object.assign
                         #js {}
                         props
                         #js {:ref ref
                              :type btn-type
                              :className button-class})]
       ;; Remove non-DOM props
       (js-delete final-props "variant")
       (js-delete final-props "size")
       (js-delete final-props "class")
       (js-delete final-props "children")
       (js-delete final-props "on-click")
       ;; Handle on-click -> onClick conversion
       (when on-click
         (aset final-props "onClick" on-click))
       ($ :button final-props children)))))
