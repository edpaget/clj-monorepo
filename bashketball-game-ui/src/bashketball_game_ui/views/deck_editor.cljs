(ns bashketball-game-ui.views.deck-editor
  "Deck editor view for building and modifying decks.

  Provides a two-panel interface with card catalog and deck contents."
  (:require
   ["lucide-react" :refer [ArrowLeft Save]]
   [bashketball-game-ui.components.deck.card-selector :refer [card-selector]]
   [bashketball-game-ui.components.deck.deck-builder :refer [deck-builder]]
   [bashketball-game-ui.hooks.use-decks :refer [use-deck use-update-deck]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.components.input :refer [input]]
   [bashketball-ui.components.loading :refer [spinner button-spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state use-effect use-callback use-memo]]))

(defui deck-editor-view
  "Deck editor page component.

  Displays a two-panel layout for editing a deck:
  - Left: Card catalog with search and filters
  - Right: Current deck contents with validation"
  []
  (let [params                                         (router/use-params)
        deck-id                                        (:id params)
        navigate                                       (router/use-navigate)
        {:keys [deck loading error]}                   (use-deck deck-id)
        [update-deck {:keys [loading saving-loading]}] (use-update-deck)
        [local-name set-local-name]                    (use-state nil)
        [local-slugs set-local-slugs]                  (use-state nil)
        [local-cards set-local-cards]                  (use-state {})
        [has-changes? set-has-changes]                 (use-state false)
        deck-name                                      (or local-name (:name deck) "")
        deck-slugs                                     (or local-slugs (:card-slugs deck) [])
        server-cards                                   (:cards deck)
        ;; Merge server cards with locally added cards
        merged-cards                                   (use-memo
                                                        (fn []
                                                          (let [server-by-slug (into {} (map (juxt :slug identity)) server-cards)]
                                                            (vals (merge server-by-slug local-cards))))
                                                        [server-cards local-cards])]

    (use-effect
     (fn []
       (when deck
         (set-local-name (:name deck))
         (set-local-slugs (:card-slugs deck))
         (set-local-cards {})))
     [deck])

    (let [handle-add-card    (use-callback
                              (fn [card]
                                (set-local-slugs #(conj (or % []) (:slug card)))
                                (set-local-cards #(assoc % (:slug card) card))
                                (set-has-changes true))
                              [])
          handle-remove-card (use-callback
                              (fn [card]
                                (let [slug (:slug card)]
                                  (set-local-slugs
                                   (fn [slugs]
                                     (let [idx (.indexOf slugs slug)]
                                       (if (>= idx 0)
                                         (into (subvec slugs 0 idx)
                                               (subvec slugs (inc idx)))
                                         slugs))))
                                  (set-has-changes true)))
                              [])
          handle-name-change (use-callback
                              (fn [e]
                                (set-local-name (.. e -target -value))
                                (set-has-changes true))
                              [])
          handle-save        (use-callback
                              (fn []
                                (-> (update-deck {:id deck-id
                                                  :name local-name
                                                  :card-slugs local-slugs})
                                    (.then (fn [_]
                                             (set-has-changes false)))))
                              [deck-id local-name local-slugs update-deck])]

      (cond
        loading
        ($ :div {:class "flex items-center justify-center min-h-[400px]"}
           ($ spinner {:size :lg}))

        error
        ($ :div {:class "space-y-4"}
           ($ :div {:class "bg-red-50 border border-red-200 rounded-lg p-4 text-red-700"}
              "Failed to load deck. It may not exist or you don't have access.")
           ($ button
              {:variant :outline
               :on-click #(navigate "/decks")}
              ($ ArrowLeft {:className "w-4 h-4 mr-2"})
              "Back to Decks"))

        :else
        ($ :div {:class "space-y-4"}
           ($ :div {:class "flex items-center justify-between"}
              ($ :div {:class "flex items-center gap-4"}
                 ($ button
                    {:variant :ghost
                     :on-click #(navigate "/decks")}
                    ($ ArrowLeft {:className "w-4 h-4 mr-2"})
                    "Back")
                 ($ input
                    {:value deck-name
                     :on-change handle-name-change
                     :placeholder "Deck Name"
                     :class "text-xl font-semibold w-64"}))
              ($ button
                 {:on-click handle-save
                  :disabled (or saving-loading (not has-changes?))}
                 (if saving-loading
                   ($ button-spinner)
                   ($ Save {:className "w-4 h-4 mr-2"}))
                 "Save Deck"))

           ($ :div {:class "grid grid-cols-1 lg:grid-cols-2 gap-4 h-[calc(100vh-200px)]"}
              ($ card-selector
                 {:deck-slugs deck-slugs
                  :on-add-card handle-add-card
                  :on-remove-card handle-remove-card})
              ($ deck-builder
                 {:deck {:name deck-name
                         :card-slugs deck-slugs
                         :is-valid (:is-valid deck)
                         :validation-errors (:validation-errors deck)}
                  :cards merged-cards
                  :on-remove-card handle-remove-card})))))))
