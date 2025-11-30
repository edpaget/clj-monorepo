(ns bashketball-game-ui.views.decks
  "Deck management view.

  Shows user's decks and allows creating/editing decks."
  (:require
   ["lucide-react" :refer [Plus]]
   [bashketball-game-ui.components.deck.deck-list :refer [deck-list]]
   [bashketball-game-ui.hooks.use-decks :refer [use-my-decks use-create-deck use-delete-deck]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.components.input :refer [input]]
   [bashketball-ui.components.loading :refer [button-spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui create-deck-dialog
  "Dialog for creating a new deck."
  [{:keys [open? on-close on-create creating?]}]
  (let [[name set-name] (use-state "")]
    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ($ :div {:class "fixed inset-0 bg-black/50"
                  :on-click on-close})
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
            ($ :h2 {:class "text-xl font-semibold text-gray-900 mb-4"}
               "Create New Deck")
            ($ :form {:on-submit (fn [e]
                                   (.preventDefault e)
                                   (when (seq name)
                                     (on-create name)
                                     (set-name "")))}
               ($ :div {:class "space-y-4"}
                  ($ :div
                     ($ :label {:class "block text-sm font-medium text-gray-700 mb-1"
                                :html-for "deck-name"}
                        "Deck Name")
                     ($ input
                        {:id "deck-name"
                         :placeholder "My Awesome Deck"
                         :value name
                         :on-change #(set-name (.. % -target -value))
                         :disabled creating?}))
                  ($ :div {:class "flex justify-end gap-3 pt-4"}
                     ($ button
                        {:variant :outline
                         :type "button"
                         :on-click on-close
                         :disabled creating?}
                        "Cancel")
                     ($ button
                        {:type "submit"
                         :disabled (or creating? (empty? name))}
                        (when creating?
                          ($ button-spinner))
                        "Create Deck")))))))))

(defui delete-confirm-dialog
  "Confirmation dialog for deleting a deck."
  [{:keys [deck on-close on-confirm deleting?]}]
  (when deck
    ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
       ($ :div {:class "fixed inset-0 bg-black/50"
                :on-click on-close})
       ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
          ($ :h2 {:class "text-xl font-semibold text-gray-900 mb-2"}
             "Delete Deck")
          ($ :p {:class "text-gray-600 mb-6"}
             (str "Are you sure you want to delete \"" (:name deck) "\"? This action cannot be undone."))
          ($ :div {:class "flex justify-end gap-3"}
             ($ button
                {:variant :outline
                 :on-click on-close
                 :disabled deleting?}
                "Cancel")
             ($ button
                {:variant :destructive
                 :on-click #(on-confirm (:id deck))
                 :disabled deleting?}
                (when deleting?
                  ($ button-spinner))
                "Delete"))))))

(defui decks-view
  "Decks page component.

  Displays the user's deck collection with options to create, edit, and delete."
  []
  (let [{:keys [decks loading error refetch]}            (use-my-decks)
        [create-deck {:keys [loading creating-loading]}] (use-create-deck)
        [delete-deck {:keys [loading deleting-loading]}] (use-delete-deck)
        navigate                                         (router/use-navigate)
        [show-create? set-show-create]                   (use-state false)
        [deck-to-delete set-deck-to-delete]              (use-state nil)]
    ($ :div {:class "space-y-6"}
       ($ :div {:class "flex justify-between items-center"}
          ($ :h1 {:class "text-2xl font-bold text-gray-900"} "My Decks")
          ($ button
             {:on-click #(set-show-create true)}
             ($ Plus {:className "w-4 h-4 mr-2"})
             "Create Deck"))

       (when error
         ($ :div {:class "bg-red-50 border border-red-200 rounded-lg p-4 text-red-700"}
            "Failed to load decks. Please try again."))

       ($ deck-list
          {:decks decks
           :loading loading
           :on-edit #(navigate (str "/decks/" (:id %)))
           :on-delete #(set-deck-to-delete %)})

       ($ create-deck-dialog
          {:open? show-create?
           :on-close #(set-show-create false)
           :creating? creating-loading
           :on-create (fn [name]
                        (-> (create-deck name)
                            (.then (fn [^js result]
                                     (set-show-create false)
                                     (let [deck-id (-> result .-data .-createDeck :id)]
                                       (navigate (str "/decks/" deck-id)))))))})

       ($ delete-confirm-dialog
          {:deck deck-to-delete
           :on-close #(set-deck-to-delete nil)
           :deleting? deleting-loading
           :on-confirm (fn [id]
                         (-> (delete-deck id)
                             (.then (fn [_]
                                      (set-deck-to-delete nil)))))}))))
