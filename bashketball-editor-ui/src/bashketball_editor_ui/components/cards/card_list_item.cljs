(ns bashketball-editor-ui.components.cards.card-list-item
  "Card list item component for compact list view."
  (:require
   ["clsx" :refer [clsx]]
   ["lucide-react" :refer [ChevronRight]]
   ["tailwind-merge" :refer [twMerge]]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(defn cn
  [& classes]
  (twMerge (apply clsx (filter some? classes))))

(def card-type-colors
  "Color mapping for card types."
  {:card-type/PLAYER_CARD {:dot "bg-blue-500" :badge "bg-blue-100 text-blue-700"}
   :card-type/PLAY_CARD {:dot "bg-green-500" :badge "bg-green-100 text-green-700"}
   :card-type/ABILITY_CARD {:dot "bg-purple-500" :badge "bg-purple-100 text-purple-700"}
   :card-type/SPLIT_PLAY_CARD {:dot "bg-amber-500" :badge "bg-amber-100 text-amber-700"}
   :card-type/COACHING_CARD {:dot "bg-rose-500" :badge "bg-rose-100 text-rose-700"}
   :card-type/TEAM_ASSET_CARD {:dot "bg-cyan-500" :badge "bg-cyan-100 text-cyan-700"}
   :card-type/STANDARD_ACTION_CARD {:dot "bg-gray-500" :badge "bg-gray-100 text-gray-700"}})

(def default-colors
  {:dot "bg-gray-400" :badge "bg-gray-100 text-gray-600"})

(defn format-card-type
  "Formats card type keyword for display."
  [card-type]
  (when card-type
    (-> (name card-type)
        (str/replace #"_CARD$" "")
        (str/replace "_" " "))))

(defn format-relative-time
  "Formats a timestamp as relative time."
  [timestamp]
  (when timestamp
    (let [now        (js/Date.)
          then       (js/Date. timestamp)
          diff-ms    (- (.getTime now) (.getTime then))
          diff-mins  (js/Math.floor (/ diff-ms 60000))
          diff-hours (js/Math.floor (/ diff-mins 60))
          diff-days  (js/Math.floor (/ diff-hours 24))]
      (cond
        (< diff-mins 1) "just now"
        (< diff-mins 60) (str diff-mins "m ago")
        (< diff-hours 24) (str diff-hours "h ago")
        (< diff-days 7) (str diff-days "d ago")
        :else (-> then (.toLocaleDateString))))))

(defui card-list-item
  "A compact list item for displaying a card.

  Props:
  - `:card` - Card map with :slug, :name, :card-type, :updated-at
  - `:on-click` - Click handler (receives card)
  - `:selected?` - Whether this item is selected
  - `:class` - Additional CSS classes"
  [{:keys [card on-click selected? class]}]
  (let [{:keys [slug name card-type updated-at]} card
        colors                                   (get card-type-colors card-type default-colors)]
    ($ :div
       {:class (cn "flex items-center px-4 py-3 border-b border-gray-100 cursor-pointer transition-colors"
                   (if selected?
                     "bg-blue-50 border-l-2 border-l-blue-500"
                     "hover:bg-gray-50")
                   class)
        :role "button"
        :tabIndex 0
        :on-click #(when on-click (on-click card))
        :on-key-down #(when (and on-click (= (.-key %) "Enter"))
                        (on-click card))}
       ($ :div {:class (cn "w-2 h-2 rounded-full mr-3 flex-shrink-0" (:dot colors))})
       ($ :span {:class "flex-1 font-medium text-gray-900 truncate"} name)
       ($ :span {:class (cn "text-xs px-2 py-0.5 rounded-full ml-3 flex-shrink-0" (:badge colors))}
          (format-card-type card-type))
       ($ :span {:class "text-sm text-gray-400 ml-4 flex-shrink-0 w-16 text-right"}
          (format-relative-time updated-at))
       ($ ChevronRight {:className "w-4 h-4 text-gray-300 ml-2 flex-shrink-0"}))))

(defui card-list-item-skeleton
  "Loading skeleton for card list item."
  []
  ($ :div {:class "flex items-center px-4 py-3 border-b border-gray-100 animate-pulse"}
     ($ :div {:class "w-2 h-2 rounded-full mr-3 bg-gray-200"})
     ($ :div {:class "flex-1 h-4 bg-gray-200 rounded"})
     ($ :div {:class "w-16 h-5 bg-gray-200 rounded-full ml-3"})
     ($ :div {:class "w-12 h-4 bg-gray-200 rounded ml-4"})
     ($ :div {:class "w-4 h-4 ml-2"})))
