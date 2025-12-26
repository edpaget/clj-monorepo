(ns bashketball-game-ui.components.game.skill-test-modal
  "Modal component for skill test resolution.

  Displays:
  - Test stat and base value
  - Modifiers from abilities
  - Fate reveal button/result
  - Final total vs target with success/failure"
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defn- stat-label
  "Returns human-readable label for a stat keyword."
  [stat]
  (case stat
    :stat/SHOOTING "Shooting"
    :stat/PASSING "Passing"
    :stat/DEFENSE "Defense"
    :stat/SPEED "Speed"
    (name stat)))

(defui modifier-row
  "Displays a single modifier with source and amount."
  [{:keys [modifier]}]
  (let [{:keys [source amount reason]} modifier]
    ($ :div {:class "flex justify-between text-sm py-1"}
       ($ :span {:class "text-gray-600"} (or reason source))
       ($ :span {:class (if (pos? amount)
                          "font-medium text-green-600"
                          "font-medium text-red-600")}
          (if (pos? amount) (str "+" amount) amount)))))

(defui modifiers-section
  "Displays all modifiers applied to the skill test."
  [{:keys [modifiers]}]
  (when (seq modifiers)
    ($ :div {:class "border-t border-gray-200 py-2 mb-2"}
       ($ :div {:class "text-xs text-gray-500 mb-1"} "Modifiers")
       (for [[idx modifier] (map-indexed vector modifiers)]
         ($ modifier-row {:key idx :modifier modifier})))))

(defui skill-test-modal
  "Modal for skill test resolution.

  Props:
  - open?: boolean, whether modal is open
  - skill-test: map with skill test data
    - :stat - stat being tested
    - :base-value - base stat value
    - :modifiers - vector of modifier maps
    - :fate - revealed fate value (nil if not yet revealed)
    - :total - computed total (nil if not resolved)
    - :target-value - target to beat (optional)
  - on-reveal-fate: fn [] called when fate button clicked
  - on-close: fn [] called to close modal"
  [{:keys [open? skill-test on-reveal-fate on-close]}]
  (when open?
    (let [{:keys [stat base-value modifiers fate total target-value]} skill-test
          modifier-total (reduce + 0 (map :amount modifiers))
          has-fate? (some? fate)
          computed-total (when has-fate?
                           (+ base-value modifier-total fate))
          success? (when (and computed-total target-value)
                     (>= computed-total target-value))]
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ;; Backdrop
         ($ :div {:class "fixed inset-0 bg-black/50"})
         ;; Modal content
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-sm mx-4"}
            ;; Header
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
               "Skill Test")

            ;; Stat and base value
            ($ :div {:class "flex justify-between items-center mb-2"}
               ($ :span {:class "text-gray-700 font-medium"}
                  (stat-label stat))
               ($ :span {:class "text-2xl font-bold text-gray-900"}
                  base-value))

            ;; Modifiers
            ($ modifiers-section {:modifiers modifiers})

            ;; Modifier total (if any)
            (when (and (seq modifiers) (not= 0 modifier-total))
              ($ :div {:class "flex justify-between text-sm py-1 border-t border-gray-100"}
                 ($ :span {:class "text-gray-600"} "Modifier Total")
                 ($ :span {:class (if (pos? modifier-total)
                                    "font-medium text-green-600"
                                    "font-medium text-red-600")}
                    (if (pos? modifier-total)
                      (str "+" modifier-total)
                      modifier-total))))

            ;; Fate section
            (if has-fate?
              ($ :<>
                 ($ :div {:class "flex justify-between items-center py-3 border-t border-gray-200"}
                    ($ :span {:class "text-purple-600 font-medium"} "Fate")
                    ($ :span {:class "text-4xl font-bold text-purple-600"} fate))

                 ;; Total
                 ($ :div {:class "flex justify-between items-center py-3 border-t-2 border-gray-300"}
                    ($ :span {:class "text-gray-900 font-semibold"} "Total")
                    ($ :span {:class "text-3xl font-bold text-gray-900"}
                       (or total computed-total)))

                 ;; Target comparison
                 (when target-value
                   ($ :div {:class "text-center py-3"}
                      ($ :span {:class "text-sm text-gray-500"}
                         (str "Target: " target-value))
                      ($ :div {:class (str "text-xl font-bold mt-1 "
                                           (if success? "text-green-600" "text-red-600"))}
                         (if success? "SUCCESS!" "FAILED"))))

                 ;; Continue button
                 ($ button {:variant :default
                            :on-click on-close
                            :class "w-full mt-4"}
                    "Continue"))

              ;; Fate not yet revealed
              ($ :div {:class "pt-4 border-t border-gray-200"}
                 ($ button {:variant :default
                            :on-click on-reveal-fate
                            :class "w-full"}
                    "Reveal Fate"))))))))
