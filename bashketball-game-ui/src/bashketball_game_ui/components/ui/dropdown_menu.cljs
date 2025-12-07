(ns bashketball-game-ui.components.ui.dropdown-menu
  "Dropdown menu component wrapping Radix UI DropdownMenu.

  Provides accessible dropdown menus with keyboard navigation."
  (:require
   ["@radix-ui/react-dropdown-menu" :as DropdownMenu]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def dropdown-menu (.-Root DropdownMenu))
(def dropdown-menu-trigger (.-Trigger DropdownMenu))
(def dropdown-menu-portal (.-Portal DropdownMenu))
(def dropdown-menu-content* (.-Content DropdownMenu))
(def dropdown-menu-item* (.-Item DropdownMenu))
(def dropdown-menu-separator* (.-Separator DropdownMenu))

(defui dropdown-menu-content
  "Styled dropdown menu content container.

  Props:
  - align: 'start' | 'center' | 'end' (default 'end')
  - side: 'top' | 'bottom' | 'left' | 'right' (default 'bottom')
  - class: additional CSS classes
  - children: menu items"
  [{:keys [align side class children]
    :or   {align "end" side "bottom"}}]
  ($ dropdown-menu-portal
     ($ dropdown-menu-content*
        {:align       align
         :side        side
         :sideOffset  4
         :className   (cn "z-50 min-w-[140px] overflow-hidden rounded-md border bg-white p-1 shadow-md"
                          "animate-in fade-in-0 zoom-in-95"
                          "data-[side=bottom]:slide-in-from-top-2"
                          "data-[side=top]:slide-in-from-bottom-2"
                          class)}
        children)))

(defui dropdown-menu-item
  "Styled dropdown menu item.

  Props:
  - on-select: fn [] called when item selected
  - disabled: boolean
  - class: additional CSS classes
  - children: item content"
  [{:keys [on-select disabled class children]}]
  ($ dropdown-menu-item*
     {:onSelect  on-select
      :disabled  disabled
      :className (cn "relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none"
                     "focus:bg-slate-100 focus:text-slate-900"
                     "data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
                     class)}
     children))

(defui dropdown-menu-separator
  "Horizontal separator line in dropdown menu."
  []
  ($ dropdown-menu-separator*
     {:className "mx-1 my-1 h-px bg-slate-200"}))
