(ns bashketball-ui.components.textarea-with-icons
  "Textarea with icon insertion toolbar.

  Combines [[bashketball-ui.components.textarea]] with
  [[bashketball-ui.components.icon-toolbar/icon-toolbar]] to provide
  easy insertion of special characters at cursor position."
  (:require
   [bashketball-ui.components.icon-toolbar :refer [icon-toolbar]]
   [uix.core :refer [$ defui use-ref use-callback]]))

(defn insert-at-cursor
  "Inserts text at cursor position in a string.

  Takes the current value, cursor position, and text to insert.
  Returns the new string with text inserted at the cursor position."
  [value cursor-pos text]
  (let [value (or value "")
        pos   (min cursor-pos (count value))]
    (str (subs value 0 pos) text (subs value pos))))

(defui textarea-with-icons
  "Textarea with an icon toolbar for inserting special characters.

  Renders an icon toolbar above a textarea. Clicking an icon inserts
  the character at the current cursor position and refocuses the textarea.

  Props are the same as [[textarea]] plus:
  - `:icons` - optional custom icon list (defaults to game icons)
  - `:on-value-change` - called with new value when icon inserted"
  [{:keys [value on-change on-value-change icons id name placeholder disabled class rows]}]
  (let [textarea-ref  (use-ref nil)

        handle-insert (use-callback
                       (fn [icon]
                         (when-let [el @textarea-ref]
                           (let [cursor-pos (.-selectionStart el)
                                 new-value  (insert-at-cursor value cursor-pos icon)
                                 new-cursor (+ cursor-pos (count icon))]
                             (if on-value-change
                               (on-value-change new-value)
                               (when on-change
                                 (on-change #js {:target #js {:value new-value}})))
                             (js/requestAnimationFrame
                              (fn []
                                (.focus el)
                                (.setSelectionRange el new-cursor new-cursor))))))
                       [value on-change on-value-change])]

    ($ :div {:class "space-y-1"}
       ($ icon-toolbar {:on-insert handle-insert :icons icons})
       ($ :textarea
          {:ref textarea-ref
           :id id
           :name name
           :value value
           :on-change on-change
           :placeholder placeholder
           :disabled disabled
           :rows (or rows 3)
           :class (str "flex min-h-[60px] w-full rounded-md border border-gray-200 "
                       "bg-transparent px-3 py-2 text-sm shadow-sm "
                       "placeholder:text-gray-500 focus-visible:outline-none "
                       "focus-visible:ring-1 focus-visible:ring-gray-950 "
                       "disabled:cursor-not-allowed disabled:opacity-50 "
                       class)}))))
