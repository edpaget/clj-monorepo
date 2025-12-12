(ns bashketball-ui.components.avatar
  "Avatar component with image and initials fallback.

  Displays user avatars with automatic fallback to initials when the image
  fails to load (e.g., 404 from server)."
  (:require
   ["class-variance-authority" :refer [cva]]
   [bashketball-ui.utils :refer [cn]]
   [clojure.string :as str]
   [uix.core :refer [$ defui use-state]]))

(def avatar-variants
  "CVA configuration for avatar sizes."
  (cva
   "relative inline-flex items-center justify-center rounded-full overflow-hidden bg-gray-200"
   #js {:variants
        #js {:size
             #js {:sm "w-6 h-6 text-xs"
                  :md "w-8 h-8 text-sm"
                  :lg "w-12 h-12 text-base"
                  :xl "w-16 h-16 text-lg"}}
        :defaultVariants
        #js {:size "md"}}))

(def fallback-colors
  "Background colors for initials fallback, indexed by character code."
  ["bg-red-500" "bg-blue-500" "bg-green-500" "bg-yellow-500"
   "bg-purple-500" "bg-pink-500" "bg-indigo-500" "bg-teal-500"])

(defn- get-initials
  "Extracts initials from name or email.

  Returns the first character uppercased. For names with spaces,
  returns first letter of first and last name."
  [name email]
  (let [display (or name email "?")]
    (if-let [parts (and name (seq (filter seq (str/split name #"\s+"))))]
      (if (> (count parts) 1)
        (str (first (first parts)) (first (last parts)))
        (str (first (first parts))))
      (str (first display)))))

(defn- get-fallback-color
  "Returns a consistent background color based on the name/email."
  [name email]
  (let [s    (or name email "")
        code (if (seq s) (.charCodeAt s 0) 0)]
    (nth fallback-colors (mod code (count fallback-colors)))))

(defui avatar-fallback
  "Fallback display showing initials.

  Props:
  - `:name` - User's display name
  - `:email` - User's email (fallback for initials)
  - `:size` - Size variant: :sm, :md, :lg, :xl"
  [{:keys [name email size class]}]
  (let [initials (get-initials name email)
        bg-color (get-fallback-color name email)]
    ($ :div
       {:class (cn (avatar-variants #js {:size (cljs.core/name (or size :md))})
                   bg-color
                   "text-white font-medium select-none"
                   class)
        :role "img"
        :aria-label (or name email "User avatar")}
       (str/upper-case initials))))

(defui avatar
  "User avatar component with image and initials fallback.

  Displays the user's avatar image. If the image fails to load (404, network
  error, etc.), automatically falls back to displaying the user's initials
  on a colored background.

  Props:
  - `:src` - Image URL (can be relative like `/api/avatars/:id`)
  - `:name` - User's display name (used for alt text and initials)
  - `:email` - User's email (fallback for initials if no name)
  - `:size` - Size variant: :sm (24px), :md (32px), :lg (48px), :xl (64px)
  - `:class` - Additional CSS classes

  The fallback color is determined by hashing the name/email for consistency."
  [{:keys [src name email size class]}]
  (let [[img-error? set-img-error!] (use-state false)
        size-name                   (cljs.core/name (or size :md))]
    (if (or img-error? (not src))
      ($ avatar-fallback {:name name :email email :size size :class class})
      ($ :div {:class (cn (avatar-variants #js {:size size-name}) class)}
         ($ :img
            {:src src
             :alt (or name email "User avatar")
             :class "w-full h-full object-cover"
             :onError #(set-img-error! true)})))))
