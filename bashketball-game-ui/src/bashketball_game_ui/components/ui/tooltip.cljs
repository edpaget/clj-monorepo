(ns bashketball-game-ui.components.ui.tooltip
  "Tooltip component wrapping Radix UI Tooltip.

  Provides accessible tooltips with keyboard support and customizable
  positioning."
  (:require
   ["@radix-ui/react-tooltip" :as Tooltip]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def tooltip-provider (.-Provider Tooltip))
(def tooltip-root (.-Root Tooltip))
(def tooltip-trigger (.-Trigger Tooltip))
(def tooltip-portal (.-Portal Tooltip))
(def tooltip-content* (.-Content Tooltip))
(def tooltip-arrow* (.-Arrow Tooltip))

(defui tooltip-content
  "Styled tooltip content container.

  Props:
  - side: 'top' | 'bottom' | 'left' | 'right' (default 'top')
  - align: 'start' | 'center' | 'end' (default 'center')
  - class: additional CSS classes
  - children: tooltip content"
  [{:keys [side align class children]
    :or   {side "top" align "center"}}]
  ($ tooltip-portal
     ($ tooltip-content*
        {:side       side
         :align      align
         :sideOffset 4
         :className  (cn "z-50 rounded-md bg-gray-900 px-3 py-2 text-sm text-white shadow-md"
                         "animate-in fade-in-0 zoom-in-95"
                         "data-[side=bottom]:slide-in-from-top-2"
                         "data-[side=top]:slide-in-from-bottom-2"
                         "data-[side=left]:slide-in-from-right-2"
                         "data-[side=right]:slide-in-from-left-2"
                         class)}
        children
        ($ tooltip-arrow* {:className "fill-gray-900"}))))

(defui tooltip
  "Complete tooltip component with trigger and content.

  Props:
  - content: tooltip content (string or element)
  - children: trigger element
  - side: 'top' | 'bottom' | 'left' | 'right' (default 'top')
  - align: 'start' | 'center' | 'end' (default 'center')
  - delay-duration: delay in ms before showing (default 300)
  - open: controlled open state (optional)
  - on-open-change: fn [open] called when open state changes (optional)"
  [{:keys [content children side align delay-duration open on-open-change]
    :or   {side "top" align "center" delay-duration 300}}]
  ($ tooltip-provider {:delayDuration delay-duration}
     ($ tooltip-root
        (merge
         {}
         (when (some? open) {:open open})
         (when on-open-change {:onOpenChange on-open-change}))
        ($ tooltip-trigger {:asChild true}
           children)
        ($ tooltip-content {:side side :align align}
           content))))

(defui tooltip-wrapper
  "Wrapper that adds a tooltip to any element.

  Use this when you need to add a tooltip to an element that may be
  disabled (like a button). Wraps the element in a span to ensure
  the tooltip trigger works correctly.

  Props:
  - content: tooltip content (string or element), or nil to skip tooltip
  - children: element to wrap
  - side: 'top' | 'bottom' | 'left' | 'right' (default 'top')
  - delay-duration: delay in ms before showing (default 300)"
  [{:keys [content children side delay-duration]
    :or   {side "top" delay-duration 300}}]
  (if content
    ($ tooltip-provider {:delayDuration delay-duration}
       ($ tooltip-root
          ($ tooltip-trigger {:asChild true}
             ($ :span {:class "inline-block"}
                children))
          ($ tooltip-content {:side side}
             content)))
    children))
