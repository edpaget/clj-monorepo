(ns bashketball-editor-ui.hooks.form
  "Form state management hooks.

  Provides declarative form handling with field definitions, reducing
  boilerplate in form components."
  (:require
   [clojure.string :as str]
   [uix.core :refer [use-state use-callback]]))

(defn use-form
  "Hook for managing form state with a simple update function.

  Returns a map with `:data` (current form values), `:set-data` (replace all),
  `:update` (update single field), and `:reset` (restore to initial values).

  Example:
      (let [{:keys [data update reset]} (use-form {:name \"\" :email \"\"})]
        ($ input {:value (:name data)
                  :on-change #(update :name (.. % -target -value))}))"
  [initial-data]
  (let [[data set-data] (use-state initial-data)
        update-field    (use-callback
                         (fn [field value]
                           (set-data #(assoc % field value)))
                         [])
        reset           (use-callback
                         (fn []
                           (set-data initial-data))
                         [initial-data])]
    {:data data
     :set-data set-data
     :update update-field
     :reset reset}))

(defn field-handler
  "Creates an on-change handler that extracts target value and calls update.

  Supports optional transform function for parsing (e.g., js/parseInt).

  Example:
      ($ input {:on-change (field-handler update :name)})
      ($ input {:on-change (field-handler update :age js/parseInt)})"
  ([update field]
   (fn [e]
     (update field (.. e -target -value))))
  ([update field transform]
   (fn [e]
     (update field (transform (.. e -target -value))))))

(defn textarea-list-handler
  "Creates handler for textarea that stores values as a vector of lines.

  Example:
      ($ textarea {:value (str/join \"\\n\" (:abilities data))
                   :on-change (textarea-list-handler update :abilities)})"
  [update field]
  (fn [e]
    (update field (-> (.. e -target -value)
                      (str/split #"\n")
                      vec))))
