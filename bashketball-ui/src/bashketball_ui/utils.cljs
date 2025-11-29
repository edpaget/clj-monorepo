(ns bashketball-ui.utils
  "Shared utility functions for UI components."
  (:require
   ["clsx" :refer [clsx]]
   ["tailwind-merge" :refer [twMerge]]))

(defn cn
  "Merges class names using clsx and tailwind-merge.

  Combines multiple class name arguments, filtering out nil values,
  and uses tailwind-merge to resolve conflicting Tailwind classes."
  [& classes]
  (twMerge (apply clsx (filter some? classes))))
