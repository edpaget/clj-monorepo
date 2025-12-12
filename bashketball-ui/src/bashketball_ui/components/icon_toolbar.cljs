(ns bashketball-ui.components.icon-toolbar
  "Icon toolbar for inserting special characters into text inputs.

  Provides a row of clickable icon buttons that insert characters at the
  current cursor position in an associated textarea or input element."
  (:require
   [uix.core :refer [$ defui]]))

(def game-icons
  "Icons commonly used in Bashketball card text."
  [{:icon "⬡" :label "Hex" :title "Hexagon (hex space)"}
   {:icon "↷" :label "Turn" :title "Turn arrow"}
   {:icon "✓" :label "Check" :title "Checkmark (success)"}
   {:icon "✗" :label "X" :title "X mark (failure)"}])

(defui icon-button
  "A single icon button in the toolbar."
  [{:keys [icon title on-click]}]
  ($ :button
     {:type "button"
      :title title
      :on-click on-click
      :class "px-2 py-1 text-lg border border-gray-200 rounded hover:bg-gray-100
              focus:outline-none focus:ring-1 focus:ring-gray-400 transition-colors"}
     icon))

(defui icon-toolbar
  "Toolbar with clickable icon buttons.

  Takes an `on-insert` callback that receives the icon string to insert.
  The parent component is responsible for handling cursor position and
  value updates."
  [{:keys [on-insert icons]}]
  (let [icons (or icons game-icons)]
    ($ :div {:class "flex gap-1 mb-1"}
       (for [{:keys [icon label title]} icons]
         ($ icon-button
            {:key label
             :icon icon
             :title title
             :on-click #(on-insert icon)})))))
