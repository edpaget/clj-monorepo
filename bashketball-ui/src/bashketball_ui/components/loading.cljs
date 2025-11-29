(ns bashketball-ui.components.loading
  "Loading indicator components following shadcn/ui patterns.

  Provides spinner and skeleton loading states."
  (:require
   ["class-variance-authority" :refer [cva]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def spinner-variants
  "CVA configuration for spinner variants."
  (cva
   "animate-spin rounded-full border-2 border-current border-t-transparent"
   #js {:variants
        #js {:size
             #js {:xs "h-3 w-3"
                  :sm "h-4 w-4"
                  :md "h-6 w-6"
                  :lg "h-8 w-8"
                  :xl "h-12 w-12"}}
        :defaultVariants
        #js {:size "md"}}))

(defui spinner
  "A spinning loading indicator.

  Props:
  - `:size` - Spinner size: :xs, :sm, :md (default), :lg, :xl
  - `:class` - Additional CSS classes
  - `:label` - Accessible label (default: \"Loading\")"
  [{:keys [size class label]
    :or {size :md label "Loading"}}]
  ($ :div
     {:class (cn (spinner-variants #js {:size (name size)}) class)
      :role "status"
      :aria-label label}
     ($ :span {:class "sr-only"} label)))

(defui skeleton
  "A placeholder skeleton for loading content.

  Props:
  - `:class` - CSS classes to control size and shape
  - `:variant` - Shape variant: :default, :circle, :text

  Examples:
  ```clojure
  ;; Rectangle placeholder
  ($ skeleton {:class \"h-12 w-full\"})

  ;; Circle avatar placeholder
  ($ skeleton {:variant :circle :class \"h-10 w-10\"})

  ;; Text line placeholder
  ($ skeleton {:variant :text :class \"w-3/4\"})
  ```"
  [{:keys [class variant]
    :or {variant :default}}]
  ($ :div
     {:class (cn "animate-pulse bg-gray-200"
                 (case variant
                   :circle "rounded-full"
                   :text "h-4 rounded"
                   "rounded-md")
                 class)
      :aria-hidden "true"}))

(defui loading-overlay
  "A full-container loading overlay with spinner.

  Props:
  - `:message` - Optional loading message
  - `:spinner-size` - Size of spinner (default :lg)
  - `:class` - Additional CSS classes"
  [{:keys [message spinner-size class]
    :or {spinner-size :lg}}]
  ($ :div
     {:class (cn "flex flex-col items-center justify-center gap-3 p-4" class)
      :role "status"
      :aria-busy "true"}
     ($ spinner {:size spinner-size})
     (when message
       ($ :p {:class "text-sm text-gray-500"} message))))

(defui loading-dots
  "Animated loading dots.

  Props:
  - `:class` - Additional CSS classes"
  [{:keys [class]}]
  ($ :div
     {:class (cn "flex items-center gap-1" class)
      :role "status"
      :aria-label "Loading"}
     ($ :span {:class "sr-only"} "Loading")
     (for [i (range 3)]
       ($ :div
          {:key i
           :class "h-2 w-2 rounded-full bg-gray-400 animate-bounce"
           :style {:animation-delay (str (* i 0.15) "s")}}))))

(defui button-spinner
  "A small spinner sized for use inside buttons.

  Props:
  - `:class` - Additional CSS classes"
  [{:keys [class]}]
  ($ spinner {:size :sm :class (cn "-ml-1 mr-2" class)}))
