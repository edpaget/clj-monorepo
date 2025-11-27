(ns bashketball-editor-ui.components.ui.textarea
  "Textarea component following shadcn/ui patterns."
  (:require
   ["clsx" :refer [clsx]]
   ["tailwind-merge" :refer [twMerge]]
   [uix.core :refer [$ defui]]))

(defn cn
  "Merges class names using clsx and tailwind-merge."
  [& classes]
  (twMerge (apply clsx (filter some? classes))))

(def textarea-classes
  "Base CSS classes for textarea."
  "flex min-h-[60px] w-full rounded-md border border-gray-200 bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-gray-950 disabled:cursor-not-allowed disabled:opacity-50")

(defui textarea
  "A textarea component.

  Props:
  - `:value` - Current value
  - `:on-change` - Change handler
  - `:placeholder` - Placeholder text
  - `:disabled` - Disable the textarea
  - `:class` - Additional CSS classes
  - `:rows` - Number of rows"
  [{:keys [value on-change placeholder disabled class rows id name]}]
  ($ :textarea
     {:id id
      :name name
      :value value
      :on-change on-change
      :placeholder placeholder
      :disabled disabled
      :rows (or rows 3)
      :class (cn textarea-classes class)}))
