(ns bashketball-game-ui.components.game.action-button-with-tooltip
  "Action button that shows 'why not?' tooltip when disabled.

  Combines the standard action button with a tooltip that explains
  why the action is unavailable when disabled."
  (:require
   [bashketball-game-ui.components.ui.tooltip :as tooltip]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defui explanation-content
  "Renders explanation messages for tooltip content.

  Props:
  - explanations: vector of {:key keyword :message string} maps"
  [{:keys [explanations]}]
  ($ :div {:class "space-y-1"}
     (for [{:keys [key message]} explanations]
       ($ :div {:key   (name key)
                :class "text-sm"}
          message))))

(defui action-button-with-tooltip
  "Action button that shows explanation tooltip when disabled.

  When the button is disabled and has explanations, hovering over it
  shows a tooltip explaining why the action is unavailable.

  Props:
  - label: button text
  - on-click: click handler
  - disabled: boolean
  - explanation: vector of {:key keyword :message string} maps (optional)
  - variant: button variant (default :outline)
  - class: additional CSS classes"
  [{:keys [label on-click disabled explanation variant class]}]
  (let [has-explanation (and disabled (seq explanation))
        btn             ($ button {:variant  (or variant :outline)
                                   :size     :lg
                                   :on-click on-click
                                   :disabled disabled
                                   :class    (cn "min-h-[44px]" class)}
                           label)]
    (if has-explanation
      ;; Wrap in span for tooltip trigger since disabled buttons don't fire mouse events
      ($ tooltip/tooltip-wrapper
         {:content ($ explanation-content {:explanations explanation})
          :side    "top"}
         btn)
      btn)))
