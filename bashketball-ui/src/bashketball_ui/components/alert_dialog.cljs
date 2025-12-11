(ns bashketball-ui.components.alert-dialog
  "Alert dialog component for destructive action confirmation.

  Provides an accessible modal dialog using Radix UI AlertDialog primitive.
  Follows shadcn/ui patterns for consistent styling."
  (:require
   ["@radix-ui/react-alert-dialog" :as AlertDialogPrimitive]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def overlay-classes
  "CSS classes for dialog overlay."
  "fixed inset-0 z-50 bg-black/80 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0")

(def content-classes
  "CSS classes for dialog content."
  "fixed left-[50%] top-[50%] z-50 grid w-full max-w-lg translate-x-[-50%] translate-y-[-50%] gap-4 border border-gray-200 bg-white p-6 shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%] sm:rounded-lg")

(defui alert-dialog
  "Root alert dialog component.

  Props:
  - `:open` - Controlled open state
  - `:on-open-change` - Callback when open state changes
  - `:children` - Dialog content (trigger, content, etc.)"
  [{:keys [open on-open-change children]}]
  ($ AlertDialogPrimitive/Root
     {:open open :onOpenChange on-open-change}
     children))

(defui alert-dialog-trigger
  "Button that triggers the dialog to open."
  [{:keys [as-child children]}]
  ($ AlertDialogPrimitive/Trigger
     {:asChild (if (some? as-child) as-child true)}
     children))

(defui alert-dialog-content
  "The modal content container."
  [{:keys [class children]}]
  ($ AlertDialogPrimitive/Portal
     ($ AlertDialogPrimitive/Overlay {:class overlay-classes})
     ($ AlertDialogPrimitive/Content
        {:class (cn content-classes class)}
        children)))

(defui alert-dialog-header
  "Header section containing title and description."
  [{:keys [class children]}]
  ($ :div {:class (cn "flex flex-col space-y-2 text-center sm:text-left" class)}
     children))

(defui alert-dialog-footer
  "Footer section containing action buttons."
  [{:keys [class children]}]
  ($ :div {:class (cn "flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2" class)}
     children))

(defui alert-dialog-title
  "Dialog title text."
  [{:keys [class children]}]
  ($ AlertDialogPrimitive/Title
     {:class (cn "text-lg font-semibold" class)}
     children))

(defui alert-dialog-description
  "Dialog description text."
  [{:keys [class children]}]
  ($ AlertDialogPrimitive/Description
     {:class (cn "text-sm text-gray-500" class)}
     children))

(defui alert-dialog-cancel
  "Cancel button that closes the dialog."
  [{:keys [class children on-click]}]
  ($ AlertDialogPrimitive/Cancel {:asChild true}
     ($ button
        {:variant :outline
         :class class
         :on-click on-click}
        (or children "Cancel"))))

(defui alert-dialog-action
  "Action button for the destructive action."
  [{:keys [class children on-click disabled]}]
  ($ AlertDialogPrimitive/Action {:asChild true}
     ($ button
        {:variant :destructive
         :class class
         :on-click on-click
         :disabled disabled}
        children)))
