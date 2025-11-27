(ns bashketball-editor-ui.views.card-editor
  "Card editor view for creating and editing cards."
  (:require
   ["@apollo/client" :refer [useApolloClient useQuery]]
   ["lucide-react" :refer [ArrowLeft Save]]
   [bashketball-editor-ui.components.cards.card-type-selector :refer [card-types]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.components.ui.input :refer [input]]
   [bashketball-editor-ui.components.ui.label :refer [label]]
   [bashketball-editor-ui.components.ui.loading :refer [spinner]]
   [bashketball-editor-ui.components.ui.select :refer [select]]
   [bashketball-editor-ui.components.ui.textarea :refer [textarea]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.hooks.sets :refer [use-sets]]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui use-state use-effect]]))

;; -----------------------------------------------------------------------------
;; Form field components
;; -----------------------------------------------------------------------------

(defui form-field
  "A form field with label and input."
  [{:keys [id label-text children]}]
  ($ :div {:class "space-y-2"}
     ($ label {:for id} label-text)
     children))

(defui stat-input
  "Number input for stats (1-10)."
  [{:keys [id label-text value on-change]}]
  ($ form-field {:id id :label-text label-text}
     ($ input {:id id
               :type "number"
               :min 1
               :max 10
               :value (or value "")
               :on-change on-change
               :class "w-20"})))

;; -----------------------------------------------------------------------------
;; Type-specific form sections
;; -----------------------------------------------------------------------------

(def size-options
  [{:value "XS" :label "XS"}
   {:value "SM" :label "SM"}
   {:value "MD" :label "MD"}
   {:value "LG" :label "LG"}
   {:value "XL" :label "XL"}])

(defui player-card-fields
  "Form fields specific to player cards."
  [{:keys [form-data update-field]}]
  ($ :<>
     ($ :div {:class "grid grid-cols-3 gap-4"}
        ($ stat-input {:id "sht"
                       :label-text "Shooting"
                       :value (:sht form-data)
                       :on-change #(update-field :sht (js/parseInt (.. % -target -value)))})
        ($ stat-input {:id "pss"
                       :label-text "Passing"
                       :value (:pss form-data)
                       :on-change #(update-field :pss (js/parseInt (.. % -target -value)))})
        ($ stat-input {:id "def"
                       :label-text "Defense"
                       :value (:def form-data)
                       :on-change #(update-field :def (js/parseInt (.. % -target -value)))}))
     ($ :div {:class "grid grid-cols-3 gap-4"}
        ($ stat-input {:id "speed"
                       :label-text "Speed"
                       :value (:speed form-data)
                       :on-change #(update-field :speed (js/parseInt (.. % -target -value)))})
        ($ stat-input {:id "deck-size"
                       :label-text "Deck Size"
                       :value (:deck-size form-data)
                       :on-change #(update-field :deck-size (js/parseInt (.. % -target -value)))})
        ($ form-field {:id "size" :label-text "Size"}
           ($ select {:id "size"
                      :value (or (:size form-data) "MD")
                      :options size-options
                      :on-value-change #(update-field :size %)
                      :class "w-20"})))
     ($ form-field {:id "abilities" :label-text "Abilities (one per line)"}
        ($ textarea {:id "abilities"
                     :value (or (some-> (:abilities form-data) seq (->> (clojure.string/join "\n"))) "")
                     :on-change #(update-field :abilities (-> (.. % -target -value)
                                                              (clojure.string/split #"\n")
                                                              vec))
                     :rows 4}))))

(defui play-card-fields
  "Form fields specific to play cards."
  [{:keys [form-data update-field]}]
  ($ :<>
     ($ stat-input {:id "fate"
                    :label-text "Fate"
                    :value (:fate form-data)
                    :on-change #(update-field :fate (js/parseInt (.. % -target -value)))})
     ($ form-field {:id "play" :label-text "Play Text"}
        ($ textarea {:id "play"
                     :value (or (:play form-data) "")
                     :on-change #(update-field :play (.. % -target -value))
                     :rows 4}))))

(defui ability-card-fields
  "Form fields specific to ability cards."
  [{:keys [form-data update-field]}]
  ($ form-field {:id "abilities" :label-text "Abilities (one per line)"}
     ($ textarea {:id "abilities"
                  :value (or (some-> (:abilities form-data) seq (->> (clojure.string/join "\n"))) "")
                  :on-change #(update-field :abilities (-> (.. % -target -value)
                                                           (clojure.string/split #"\n")
                                                           vec))
                  :rows 4})))

(defui split-play-card-fields
  "Form fields specific to split play cards."
  [{:keys [form-data update-field]}]
  ($ :<>
     ($ stat-input {:id "fate"
                    :label-text "Fate"
                    :value (:fate form-data)
                    :on-change #(update-field :fate (js/parseInt (.. % -target -value)))})
     ($ form-field {:id "offense" :label-text "Offense Text"}
        ($ textarea {:id "offense"
                     :value (or (:offense form-data) "")
                     :on-change #(update-field :offense (.. % -target -value))
                     :rows 3}))
     ($ form-field {:id "defense" :label-text "Defense Text"}
        ($ textarea {:id "defense"
                     :value (or (:defense form-data) "")
                     :on-change #(update-field :defense (.. % -target -value))
                     :rows 3}))))

(defui coaching-card-fields
  "Form fields specific to coaching cards."
  [{:keys [form-data update-field]}]
  ($ :<>
     ($ stat-input {:id "fate"
                    :label-text "Fate"
                    :value (:fate form-data)
                    :on-change #(update-field :fate (js/parseInt (.. % -target -value)))})
     ($ form-field {:id "coaching" :label-text "Coaching Text"}
        ($ textarea {:id "coaching"
                     :value (or (:coaching form-data) "")
                     :on-change #(update-field :coaching (.. % -target -value))
                     :rows 4}))))

(defui team-asset-card-fields
  "Form fields specific to team asset cards."
  [{:keys [form-data update-field]}]
  ($ :<>
     ($ stat-input {:id "fate"
                    :label-text "Fate"
                    :value (:fate form-data)
                    :on-change #(update-field :fate (js/parseInt (.. % -target -value)))})
     ($ form-field {:id "asset-power" :label-text "Asset Power Text"}
        ($ textarea {:id "asset-power"
                     :value (or (:asset-power form-data) "")
                     :on-change #(update-field :asset-power (.. % -target -value))
                     :rows 4}))))

(defui standard-action-card-fields
  "Form fields specific to standard action cards."
  [{:keys [form-data update-field]}]
  ($ :<>
     ($ stat-input {:id "fate"
                    :label-text "Fate"
                    :value (:fate form-data)
                    :on-change #(update-field :fate (js/parseInt (.. % -target -value)))})
     ($ form-field {:id "offense" :label-text "Offense Text"}
        ($ textarea {:id "offense"
                     :value (or (:offense form-data) "")
                     :on-change #(update-field :offense (.. % -target -value))
                     :rows 3}))
     ($ form-field {:id "defense" :label-text "Defense Text"}
        ($ textarea {:id "defense"
                     :value (or (:defense form-data) "")
                     :on-change #(update-field :defense (.. % -target -value))
                     :rows 3}))))

;; -----------------------------------------------------------------------------
;; Card form
;; -----------------------------------------------------------------------------

(def type-specific-fields
  {"PLAYER_CARD" player-card-fields
   "PLAY_CARD" play-card-fields
   "ABILITY_CARD" ability-card-fields
   "SPLIT_PLAY_CARD" split-play-card-fields
   "COACHING_CARD" coaching-card-fields
   "TEAM_ASSET_CARD" team-asset-card-fields
   "STANDARD_ACTION_CARD" standard-action-card-fields})

(defui card-form
  "The main card editing form."
  [{:keys [form-data update-field card-type on-submit saving? is-new?]}]
  (let [type-fields (get type-specific-fields card-type)]
    ($ :form {:on-submit (fn [e]
                           (.preventDefault e)
                           (on-submit))
              :class "space-y-6"}
       ($ form-field {:id "name" :label-text "Card Name"}
          ($ input {:id "name"
                    :value (or (:name form-data) "")
                    :on-change #(update-field :name (.. % -target -value))
                    :placeholder "Enter card name..."
                    :class "max-w-md"}))
       ($ form-field {:id "image-prompt" :label-text "Image Prompt"}
          ($ textarea {:id "image-prompt"
                       :value (or (:image-prompt form-data) "")
                       :on-change #(update-field :image-prompt (.. % -target -value))
                       :placeholder "Describe the card image for AI generation..."
                       :rows 3}))
       (when type-fields
         ($ type-fields {:form-data form-data :update-field update-field}))
       ($ :div {:class "flex gap-4 pt-4"}
          ($ button {:type "submit"
                     :disabled saving?}
             ($ Save {:className "w-4 h-4 mr-2"})
             (if saving? "Saving..." (if is-new? "Create Card" "Save Changes")))))))

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

(defn transform-card-data
  "Transform GraphQL card data to form data format."
  [^js card]
  (when card
    (let [typename (.-__typename card)]
      (cond-> {:name (.-name card)
               :image-prompt (.-imagePrompt card)
               :card-type typename}
        (= typename "PlayerCard")
        (assoc :sht (.-sht card)
               :pss (.-pss card)
               :def (.-def card)
               :speed (.-speed card)
               :size (.-size card)
               :deck-size (.-deckSize card)
               :abilities (js->clj (.-abilities card)))

        (= typename "PlayCard")
        (assoc :fate (.-fate card)
               :play (.-play card))

        (= typename "AbilityCard")
        (assoc :abilities (js->clj (.-abilities card)))

        (= typename "SplitPlayCard")
        (assoc :fate (.-fate card)
               :offense (.-offense card)
               :defense (.-defense card))

        (= typename "CoachingCard")
        (assoc :fate (.-fate card)
               :coaching (.-coaching card))

        (= typename "TeamAssetCard")
        (assoc :fate (.-fate card)
               :asset-power (.-assetPower card))

        (= typename "StandardActionCard")
        (assoc :fate (.-fate card)
               :offense (.-offense card)
               :defense (.-defense card))))))

(defn build-mutation-input
  "Build GraphQL mutation input from form data."
  [form-data card-type]
  (let [base {:name (:name form-data)
              :imagePrompt (:image-prompt form-data)}]
    (case card-type
      "PLAYER_CARD" (assoc base
                           :sht (:sht form-data)
                           :pss (:pss form-data)
                           :def (:def form-data)
                           :speed (:speed form-data)
                           :size (:size form-data)
                           :deckSize (:deck-size form-data)
                           :abilities (:abilities form-data))
      "PLAY_CARD" (assoc base
                         :fate (:fate form-data)
                         :play (:play form-data))
      "ABILITY_CARD" (assoc base
                            :abilities (:abilities form-data))
      "SPLIT_PLAY_CARD" (assoc base
                               :fate (:fate form-data)
                               :offense (:offense form-data)
                               :defense (:defense form-data))
      "COACHING_CARD" (assoc base
                             :fate (:fate form-data)
                             :coaching (:coaching form-data))
      "TEAM_ASSET_CARD" (assoc base
                               :fate (:fate form-data)
                               :assetPower (:asset-power form-data))
      "STANDARD_ACTION_CARD" (assoc base
                                    :fate (:fate form-data)
                                    :offense (:offense form-data)
                                    :defense (:defense form-data))
      base)))

(defui card-editor-view
  "Main card editor view for creating and editing cards."
  []
  (let [^js params                (router/use-params)
        [search-params]           (router/use-search-params)
        navigate                  (router/use-navigate)
        client                    (useApolloClient)
        slug                      (.-slug params)
        set-slug-param            (or (.-setSlug params) (.get search-params "set"))
        is-new?                   (nil? slug)

        ;; Form state
        [card-type set-card-type] (use-state nil)
        [set-slug set-set-slug]   (use-state set-slug-param)
        [form-data set-form-data] (use-state {})
        [saving? set-saving?]     (use-state false)
        [error set-error]         (use-state nil)

        ;; Fetch sets for selector
        {:keys [sets loading?]}   (use-sets)
        set-options               (mapv (fn [{:keys [slug name]}]
                                          {:value slug :label name})
                                        sets)

        ;; Fetch existing card if editing
        card-query                (useQuery q/CARD_QUERY
                                            (clj->js {:variables {:slug (or slug "")
                                                                  :setSlug (or set-slug "")}
                                                      :skip (or is-new? (nil? set-slug))}))
        card-loading?             (.-loading card-query)
        card-data                 (some-> card-query .-data .-card)

        ;; Update form when card data loads
        _                         (use-effect
                                   (fn []
                                     (when card-data
                                       (let [transformed (transform-card-data card-data)]
                                         (set-form-data transformed)
                                         (set-card-type (:card-type transformed)))))
                                   [card-data])

        update-field              (fn [field value]
                                    (set-form-data #(assoc % field value)))

        ;; Handle form submission
        handle-submit             (fn []
                                    (set-saving? true)
                                    (set-error nil)
                                    (let [mutation  (if is-new?
                                                      (get q/create-mutation-for-type card-type)
                                                      q/UPDATE_CARD_MUTATION)
                                          input     (build-mutation-input form-data card-type)
                                          variables (if is-new?
                                                      {:setSlug set-slug :input input}
                                                      {:slug slug :setSlug set-slug :input input})]
                                      (-> (.mutate client (clj->js {:mutation mutation
                                                                    :variables variables
                                                                    :refetchQueries #js ["Cards"]}))
                                          (.then (fn [_result]
                                                   (navigate (if set-slug
                                                               (str "/?set=" set-slug)
                                                               "/"))))
                                          (.catch (fn [^js e]
                                                    (set-error (.-message e))))
                                          (.finally #(set-saving? false)))))]

    ($ :div {:class "max-w-4xl mx-auto"}
       ;; Header
       ($ :div {:class "flex items-center gap-4 mb-6"}
          ($ button {:variant :ghost
                     :on-click #(navigate "/")}
             ($ ArrowLeft {:className "w-4 h-4 mr-2"})
             "Back")
          ($ :h1 {:class "text-2xl font-bold"}
             (if is-new? "Create New Card" "Edit Card")))

       ;; Loading state
       (cond
         (or card-loading? loading?)
         ($ :div {:class "flex justify-center py-12"}
            ($ spinner {:size :lg}))

         ;; Type selection for new cards
         (and is-new? (nil? card-type))
         ($ :div {:class "bg-white rounded-lg shadow p-6 space-y-6"}
            ;; Set selector first
            ($ form-field {:id "set-slug" :label-text "Select Set"}
               ($ select {:id "set-slug"
                          :placeholder "Choose a set..."
                          :value set-slug
                          :options set-options
                          :on-value-change set-set-slug
                          :class "max-w-xs"}))
            (when set-slug
              ($ type-selector {:on-select set-card-type})))

         ;; Show form once type is selected
         :else
         ($ :div {:class "bg-white rounded-lg shadow p-6"}
            (when error
              ($ :div {:class "mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-red-700 text-sm"}
                 error))
            (when is-new?
              ($ :div {:class "mb-4 pb-4 border-b"}
                 ($ :span {:class "text-sm text-gray-500"} "Card Type: ")
                 ($ :span {:class "font-medium"} card-type)))
            ($ card-form {:form-data form-data
                          :update-field update-field
                          :card-type card-type
                          :on-submit handle-submit
                          :saving? saving?
                          :is-new? is-new?}))))))
