(ns bashketball-editor-ui.views.card-editor-test
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.views.card-editor :refer [card-editor-view card-form]]
   [bashketball-ui.hooks.form :as form]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.events :as events]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-card-sets-response
  #js {:request #js {:query q/CARD_SETS_QUERY}
       :result #js {:data #js {:cardSets #js {:data #js [#js {:slug "core-set"
                                                              :name "Core Set"
                                                              :createdAt "2024-01-01"
                                                              :updatedAt "2024-01-01"}]}}}})

(defn with-providers
  "Wrap component with required providers for testing."
  ([component]
   (with-providers component "/cards/new"))
  ([component initial-path]
   ($ rr/MemoryRouter {:initialEntries #js [initial-path]}
      ($ MockedProvider {:mocks #js [mock-card-sets-response]}
         ($ rr/Routes
            ($ rr/Route {:path "cards/new" :element component})
            ($ rr/Route {:path "cards/:setSlug/:slug/edit" :element component}))))))

;; -----------------------------------------------------------------------------
;; Create new card tests
;; -----------------------------------------------------------------------------

(t/deftest card-editor-renders-title-test
  (t/testing "shows Create New Card title for new cards"
    (tlr/render (with-providers ($ card-editor-view)))
    (t/is (some? (tlr/get-by-text "Create New Card")))))

(t/deftest card-editor-has-back-button-test
  (t/testing "shows back button"
    (tlr/render (with-providers ($ card-editor-view)))
    (t/is (some? (tlr/get-by-role "button" {:name "Back"})))))

(t/deftest card-editor-shows-set-selector-test
  (t/async done
           (t/testing "shows set selector for new cards"
             (tlr/render (with-providers ($ card-editor-view)))
             (-> (tlr/wait-for #(tlr/get-by-text "Select Set"))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-text "Select Set")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-editor-has-set-selector-combobox-test
  (t/async done
           (t/testing "has set selector combobox"
             (tlr/render (with-providers ($ card-editor-view)))
             (-> (tlr/wait-for (fn [] (tlr/get-by-role "combobox")))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-role "combobox")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

;; -----------------------------------------------------------------------------
;; Card form Enter key behavior tests
;; -----------------------------------------------------------------------------

(defui test-card-form [{:keys [on-submit]}]
  (let [{:keys [data update]} (form/use-form {:name "" :abilities []})]
    ($ card-form {:data data
                  :update-fn update
                  :card-type "PLAYER_CARD"
                  :on-submit on-submit
                  :saving? false
                  :is-new? true})))

(t/deftest card-form-enter-in-input-does-not-submit-test
  (t/async done
           (let [submitted? (atom false)
                 on-submit  #(reset! submitted? true)
                 _          (uix-tlr/render ($ test-card-form {:on-submit on-submit}))
                 usr        (user/setup)
                 inp        (screen/get-by-placeholder-text "Enter card name...")]
             (-> (user/type-text usr inp "Test Card{Enter}")
                 (.then (fn []
                          (t/is (false? @submitted?) "Form should not submit on Enter in input")
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-form-textarea-accepts-newlines-test
  (t/async done
           (let [_   (uix-tlr/render ($ test-card-form {:on-submit identity}))
                 usr (user/setup)
                 ta  (screen/get-by-placeholder-text "Describe the card image for AI generation...")]
             (-> (user/type-text usr ta "line1{Enter}line2")
                 (.then (fn []
                          (t/is (= "line1\nline2" (.-value ta)))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-form-abilities-textarea-newlines-test
  (t/testing "abilities textarea stores and displays newlines correctly"
    (uix-tlr/render ($ test-card-form {:on-submit identity}))
    (let [ta (screen/get-by-label-text "Abilities (one per line)")]
      (events/change ta "ability 1\nability 2")
      (t/is (= "ability 1\nability 2" (.-value ta))))))

;; -----------------------------------------------------------------------------
;; Card form delete button tests
;; Note: Radix AlertDialog Portal does not work well in JSDom environment,
;; so tests focus on button visibility rather than dialog interactions.
;; -----------------------------------------------------------------------------

(defui test-card-form-edit
  "Test wrapper for card form in edit mode (not new)."
  [{:keys [on-submit on-delete]}]
  (let [[delete-open? set-delete-open?] (uix.core/use-state false)
        [deleting? _set-deleting?]      (uix.core/use-state false)
        {:keys [data update]}           (form/use-form {:name "Test Card" :abilities []})]
    ($ card-form {:data data
                  :update-fn update
                  :card-type "PLAYER_CARD"
                  :on-submit on-submit
                  :saving? false
                  :is-new? false
                  :delete-open? delete-open?
                  :set-delete-open? set-delete-open?
                  :deleting? deleting?
                  :on-delete on-delete})))

(t/deftest card-form-shows-delete-button-when-editing-test
  (uix-tlr/render ($ test-card-form-edit {:on-submit identity :on-delete identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Delete"}))))

(t/deftest card-form-hides-delete-button-for-new-cards-test
  (uix-tlr/render ($ test-card-form {:on-submit identity}))
  (t/is (nil? (screen/query-by-role "button" {:name "Delete"}))))

(t/deftest card-form-delete-button-is-destructive-variant-test
  (uix-tlr/render ($ test-card-form-edit {:on-submit identity :on-delete identity}))
  (let [btn (screen/get-by-role "button" {:name "Delete"})]
    (t/is (str/includes? (.-className btn) "bg-red"))))
