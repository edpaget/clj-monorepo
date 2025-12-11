(ns bashketball-editor-ui.views.card-editor
  "Card editor view for creating and editing cards."
  (:require
   ["@apollo/client" :refer [useApolloClient useQuery]]
   ["lucide-react" :refer [ArrowLeft Save Trash2]]
   [bashketball-editor-ui.components.cards.card-type-selector :refer [card-types]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.hooks.sets :refer [use-sets]]
   [bashketball-schemas.enums :as enums]
   [bashketball-ui.cards.card-preview :refer [card-preview]]
   [bashketball-ui.components.alert-dialog :as alert]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.components.input :refer [input]]
   [bashketball-ui.components.label :refer [label]]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.components.select :refer [select]]
   [bashketball-ui.components.textarea :refer [textarea]]
   [bashketball-ui.hooks.form :as form]
   [bashketball-ui.router :as router]
   [clojure.string :as str]
   [uix.core :refer [$ defui use-state use-effect]]))

;; -----------------------------------------------------------------------------
;; Field definitions - declarative schema for each card type
;; -----------------------------------------------------------------------------

(def size-labels
  "Human-readable labels for player sizes."
  {:size/SM "Small"
   :size/MD "Medium"
   :size/LG "Large"})

(def size-options
  "Size options derived from bashketball-schemas."
  (mapv (fn [s]
          {:value (name s)
           :label (get size-labels s (name s))})
        (enums/enum-values enums/Size)))

(def common-fields
  [{:key :name :label "Card Name" :type :text :placeholder "Enter card name..."}
   {:key :image-prompt :label "Image Prompt" :type :textarea :rows 3
    :placeholder "Describe the card image for AI generation..."}])

(def card-type-fields
  {"PLAYER_CARD"
   [{:type :row
     :fields [{:key :sht :label "Shooting" :type :stat}
              {:key :pss :label "Passing" :type :stat}
              {:key :def :label "Defense" :type :stat}]}
    {:type :row
     :fields [{:key :speed :label "Speed" :type :stat}
              {:key :deck-size :label "Deck Size" :type :stat}
              {:key :size :label "Size" :type :select :options size-options :default "MD"}]}
    {:key :abilities :label "Abilities (one per line)" :type :textarea-list :rows 4}]

   "PLAY_CARD"
   [{:key :fate :label "Fate" :type :stat}
    {:key :play :label "Play Text" :type :textarea :rows 4}]

   "ABILITY_CARD"
   [{:key :abilities :label "Abilities (one per line)" :type :textarea-list :rows 4}]

   "SPLIT_PLAY_CARD"
   [{:key :fate :label "Fate" :type :stat}
    {:key :offense :label "Offense Text" :type :textarea :rows 3}
    {:key :defense :label "Defense Text" :type :textarea :rows 3}]

   "COACHING_CARD"
   [{:key :fate :label "Fate" :type :stat}
    {:key :coaching :label "Coaching Text" :type :textarea :rows 4}]

   "TEAM_ASSET_CARD"
   [{:key :fate :label "Fate" :type :stat}
    {:key :asset-power :label "Asset Power Text" :type :textarea :rows 4}]

   "STANDARD_ACTION_CARD"
   [{:key :fate :label "Fate" :type :stat}
    {:key :offense :label "Offense Text" :type :textarea :rows 3}
    {:key :defense :label "Defense Text" :type :textarea :rows 3}]})

;; -----------------------------------------------------------------------------
;; Field rendering
;; -----------------------------------------------------------------------------

(defui form-field
  "Wrapper for form fields with label."
  [{:keys [id label-text children]}]
  ($ :div {:class "space-y-2"}
     ($ label {:for id} label-text)
     children))

(defui render-field
  "Renders a single field based on its definition."
  [{:keys [field data update-fn]}]
  (let [{:keys [key label type placeholder rows options default]} field
        id                                                        (name key)
        value                                                     (get data key)]
    (case type
      :text
      ($ form-field {:id id :label-text label}
         ($ input {:id id
                   :value (or value "")
                   :on-change (form/field-handler update-fn key)
                   :placeholder placeholder
                   :class "max-w-md"}))

      :textarea
      ($ form-field {:id id :label-text label}
         ($ textarea {:id id
                      :value (or value "")
                      :on-change (form/field-handler update-fn key)
                      :placeholder placeholder
                      :rows rows}))

      :textarea-list
      ($ form-field {:id id :label-text label}
         ($ textarea {:id id
                      :value (if (seq value) (str/join "\n" value) "")
                      :on-change (form/textarea-list-handler update-fn key)
                      :rows rows}))

      :stat
      ($ form-field {:id id :label-text label}
         ($ input {:id id
                   :type "number"
                   :min 1
                   :max 10
                   :value (or value "")
                   :on-change (form/field-handler update-fn key js/parseInt)
                   :class "w-20"}))

      :select
      ($ form-field {:id id :label-text label}
         ($ select {:id id
                    :value (or value default)
                    :options options
                    :on-value-change #(update-fn key %)
                    :class "w-20"}))

      nil)))

(defui render-fields
  "Renders a list of field definitions."
  [{:keys [fields data update-fn]}]
  ($ :<>
     (for [field fields]
       (if (= (:type field) :row)
         ($ :div {:key (str "row-" (hash (:fields field)))
                  :class "grid grid-cols-3 gap-4"}
            (for [f (:fields field)]
              ($ render-field {:key (name (:key f))
                               :field f
                               :data data
                               :update-fn update-fn})))
         ($ render-field {:key (name (:key field))
                          :field field
                          :data data
                          :update-fn update-fn})))))

;; -----------------------------------------------------------------------------
;; Card data transformation
;; -----------------------------------------------------------------------------

(def graphql->form-key
  "Maps GraphQL field names to form field keys."
  {"imagePrompt" :image-prompt
   "deckSize" :deck-size
   "assetPower" :asset-power
   "__typename" :card-type})

(def graphql-typename->card-type
  "Maps GraphQL __typename values to card-type-fields keys."
  {"PlayerCard" "PLAYER_CARD"
   "PlayCard" "PLAY_CARD"
   "AbilityCard" "ABILITY_CARD"
   "SplitPlayCard" "SPLIT_PLAY_CARD"
   "CoachingCard" "COACHING_CARD"
   "TeamAssetCard" "TEAM_ASSET_CARD"
   "StandardActionCard" "STANDARD_ACTION_CARD"})

(def form->graphql-key
  "Maps form field keys to GraphQL field names."
  {:image-prompt "imagePrompt"
   :deck-size "deckSize"
   :asset-power "assetPower"})

(defn transform-card-data
  "Transform GraphQL card data to form data format."
  [card]
  (when card
    (reduce-kv
     (fn [acc k v]
       (let [form-key (get graphql->form-key k (keyword k))]
         (cond
           (= k "abilities") (assoc acc form-key (js->clj v))
           (= k "__typename") (assoc acc form-key (get graphql-typename->card-type v v))
           (some? v) (assoc acc form-key v)
           :else acc)))
     {}
     (js->clj card))))

(defn build-mutation-input
  "Build GraphQL mutation input from form data."
  [form-data card-type]
  (let [type-field-keys (->> (get card-type-fields card-type)
                             (mapcat (fn [f]
                                       (if (= (:type f) :row)
                                         (map :key (:fields f))
                                         [(:key f)])))
                             set)
        all-keys        (into #{:name :image-prompt} type-field-keys)]
    (reduce
     (fn [acc k]
       (let [gql-key (get form->graphql-key k (name k))
             v       (get form-data k)]
         (if (some? v)
           (assoc acc gql-key v)
           acc)))
     {}
     all-keys)))

;; -----------------------------------------------------------------------------
;; Card form
;; -----------------------------------------------------------------------------

(defui card-form
  "The main card editing form."
  [{:keys [data update-fn card-type on-submit saving? is-new?
           delete-open? set-delete-open? deleting? on-delete]}]
  (let [type-fields (get card-type-fields card-type)]
    ($ :form {:on-submit (fn [e]
                           (.preventDefault e)
                           (on-submit))
              :on-key-down (fn [e]
                             (when (and (= (.-key e) "Enter")
                                        (= (.-tagName (.-target e)) "INPUT"))
                               (.preventDefault e)))
              :class "space-y-6"}
       ($ render-fields {:fields common-fields :data data :update-fn update-fn})
       (when type-fields
         ($ render-fields {:fields type-fields :data data :update-fn update-fn}))
       ($ :div {:class "flex gap-4 pt-4"}
          ($ button {:type "submit" :disabled saving?}
             ($ Save {:className "w-4 h-4 mr-2"})
             (if saving? "Saving..." (if is-new? "Create Card" "Save Changes")))
          (when-not is-new?
            ($ alert/alert-dialog {:open delete-open? :on-open-change set-delete-open?}
               ($ alert/alert-dialog-trigger
                  ($ button {:variant :destructive :type "button"}
                     ($ Trash2 {:className "w-4 h-4 mr-2"})
                     "Delete"))
               ($ alert/alert-dialog-content
                  ($ alert/alert-dialog-header
                     ($ alert/alert-dialog-title "Delete Card")
                     ($ alert/alert-dialog-description
                        (str "Are you sure you want to delete \"" (:name data) "\"? "
                             "This action will stage the deletion. You must commit to make it permanent.")))
                  ($ alert/alert-dialog-footer
                     ($ alert/alert-dialog-cancel)
                     ($ alert/alert-dialog-action
                        {:on-click on-delete :disabled deleting?}
                        (if deleting? "Deleting..." "Delete"))))))))))

;; -----------------------------------------------------------------------------
;; Type selector for new cards
;; -----------------------------------------------------------------------------

(defui type-selector
  "Card type selection step for new cards."
  [{:keys [on-select]}]
  ($ :div {:class "space-y-6"}
     ($ :h2 {:class "text-lg font-semibold"} "Select Card Type")
     ($ :div {:class "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4"}
        (for [{:keys [value label]} card-types]
          ($ button {:key value
                     :variant :outline
                     :class "h-20 flex flex-col items-center justify-center"
                     :on-click #(on-select value)}
             ($ :span {:class "font-medium"} label)
             ($ :span {:class "text-xs text-gray-500"} value))))))

;; -----------------------------------------------------------------------------
;; Main editor view
;; -----------------------------------------------------------------------------

(defui card-editor-view
  "Main card editor view for creating and editing cards."
  []
  (let [params                          (router/use-params)
        [search-params]                 (router/use-search-params)
        navigate                        (router/use-navigate)
        client                          (useApolloClient)
        slug                            (:slug params)
        set-slug-param                  (or (:setSlug params) (.get search-params "set"))
        is-new?                         (nil? slug)

        ;; Form state
        [card-type set-card-type]       (use-state nil)
        [set-slug set-set-slug]         (use-state set-slug-param)
        {:keys [data set-data update]}  (form/use-form {})
        [saving? set-saving?]           (use-state false)
        [error set-error]               (use-state nil)
        [delete-open? set-delete-open?] (use-state false)
        [deleting? set-deleting?]       (use-state false)

        ;; Fetch sets for selector
        {:keys [sets loading?]}         (use-sets)
        set-options                     (mapv (fn [s] {:value (:slug s) :label (:name s)}) sets)

        ;; Fetch existing card if editing
        card-query                      (useQuery q/CARD_QUERY
                                                  (clj->js {:variables {:slug (or slug "")
                                                                        :setSlug (or set-slug "")}
                                                            :skip (or is-new? (nil? set-slug))}))
        card-loading?                   (:loading card-query)
        card-data                       (some-> card-query :data :card)

        ;; Update form when card data loads
        _                               (use-effect
                                         (fn []
                                           (when card-data
                                             (let [transformed (transform-card-data card-data)]
                                               (set-data transformed)
                                               (set-card-type (:card-type transformed)))))
                                         [card-data set-data])

        ;; Handle form submission
        handle-submit                   (fn []
                                          (set-saving? true)
                                          (set-error nil)
                                          (let [mutation  (if is-new?
                                                            (get q/create-mutation-for-type card-type)
                                                            q/UPDATE_CARD_MUTATION)
                                                input     (build-mutation-input data card-type)
                                                variables (if is-new?
                                                            {:setSlug set-slug :input input}
                                                            {:slug slug :setSlug set-slug :input input})]
                                            (-> (.mutate client (clj->js {:mutation mutation
                                                                          :variables variables
                                                                          :refetchQueries #js ["Cards" "SyncStatus"]}))
                                                (.then (fn [_]
                                                         (navigate (if set-slug
                                                                     (str "/?set=" set-slug)
                                                                     "/"))))
                                                (.catch (fn [e]
                                                          (set-error (:message e))))
                                                (.finally #(set-saving? false)))))

        ;; Handle card deletion
        handle-delete                   (fn []
                                          (set-deleting? true)
                                          (-> (.mutate client
                                                       (clj->js {:mutation q/DELETE_CARD_MUTATION
                                                                 :variables {:slug slug :setSlug set-slug}
                                                                 :refetchQueries #js ["Cards" "SyncStatus"]}))
                                              (.then (fn [_]
                                                       (set-delete-open? false)
                                                       (navigate (if set-slug
                                                                   (str "/?set=" set-slug)
                                                                   "/"))))
                                              (.catch (fn [e]
                                                        (set-error (str "Delete failed: " (:message e)))))
                                              (.finally #(set-deleting? false))))]

    ($ :div {:class "max-w-6xl mx-auto"}
       ;; Header
       ($ :div {:class "flex items-center gap-4 mb-6"}
          ($ button {:variant :ghost :on-click #(navigate "/")}
             ($ ArrowLeft {:className "w-4 h-4 mr-2"})
             "Back")
          ($ :h1 {:class "text-2xl font-bold"}
             (if is-new? "Create New Card" "Edit Card")))

       (cond
         (or card-loading? loading?)
         ($ :div {:class "flex justify-center py-12"}
            ($ spinner {:size :lg}))

         ;; Type selection for new cards
         (and is-new? (nil? card-type))
         ($ :div {:class "bg-white rounded-lg shadow p-6 space-y-6"}
            ($ :div {:class "space-y-2"}
               ($ label {:for "set-slug"} "Select Set")
               ($ select {:id "set-slug"
                          :placeholder "Choose a set..."
                          :value set-slug
                          :options set-options
                          :on-value-change set-set-slug
                          :class "max-w-xs"}))
            (when set-slug
              ($ type-selector {:on-select set-card-type})))

         :else
         ($ :div {:class "grid grid-cols-1 lg:grid-cols-[1fr,auto] gap-6"}
            ;; Form panel
            ($ :div {:class "bg-white rounded-lg shadow p-6"}
               (when error
                 ($ :div {:class "mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-red-700 text-sm"}
                    error))
               (when is-new?
                 ($ :div {:class "mb-4 pb-4 border-b"}
                    ($ :span {:class "text-sm text-gray-500"} "Card Type: ")
                    ($ :span {:class "font-medium"} card-type)))
               ($ card-form {:data data
                             :update-fn update
                             :card-type card-type
                             :on-submit handle-submit
                             :saving? saving?
                             :is-new? is-new?
                             :delete-open? delete-open?
                             :set-delete-open? set-delete-open?
                             :deleting? deleting?
                             :on-delete handle-delete}))
            ;; Live preview panel
            ($ :div {:class "hidden lg:block"}
               ($ :div {:class "sticky top-6"}
                  ($ :h3 {:class "text-sm font-medium text-gray-500 mb-3"} "Live Preview")
                  ($ card-preview {:card data
                                   :card-type card-type
                                   :set-slug set-slug}))))))))
