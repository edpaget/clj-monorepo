(ns bashketball-editor-ui.views.set-management-test
  "Tests for set management view.

  Note: Radix AlertDialog Portal does not work well in JSDom environment,
  so tests focus on view structure rather than dialog interactions."
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.views.set-management :refer [set-management-view]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-card-sets-response
  #js {:request #js {:query q/CARD_SETS_QUERY}
       :result #js {:data #js {:cardSets #js {:data #js [#js {:slug "core-set"
                                                              :name "Core Set"
                                                              :createdAt "2024-01-01"
                                                              :updatedAt "2024-01-01"}
                                                         #js {:slug "expansion-one"
                                                              :name "Expansion One"
                                                              :createdAt "2024-02-01"
                                                              :updatedAt "2024-02-01"}]}}}})

(def mock-empty-sets-response
  #js {:request #js {:query q/CARD_SETS_QUERY}
       :result #js {:data #js {:cardSets #js {:data #js []}}}})

(defn with-providers
  "Wrap component with required providers for testing."
  ([component]
   (with-providers component #js [mock-card-sets-response]))
  ([component mocks]
   ($ rr/MemoryRouter {:initialEntries #js ["/sets/manage"]}
      ($ MockedProvider {:mocks mocks}
         ($ rr/Routes
            ($ rr/Route {:path "sets/manage" :element component})
            ($ rr/Route {:path "/" :element ($ :div "Home")}))))))

(t/deftest set-management-renders-title-test
  (tlr/render (with-providers ($ set-management-view)))
  (t/is (some? (tlr/get-by-text "Manage Sets"))))

(t/deftest set-management-has-back-button-test
  (tlr/render (with-providers ($ set-management-view)))
  (t/is (some? (tlr/get-by-role "button" {:name "Back"}))))

(t/deftest set-management-shows-sets-test
  (t/async done
           (tlr/render (with-providers ($ set-management-view)))
           (-> (tlr/wait-for #(tlr/get-by-text "Core Set"))
               (.then (fn []
                        (t/is (some? (tlr/get-by-text "Core Set")))
                        (t/is (some? (tlr/get-by-text "core-set")))
                        (t/is (some? (tlr/get-by-text "Expansion One")))
                        (t/is (some? (tlr/get-by-text "expansion-one")))
                        (done)))
               (.catch (fn [e]
                         (t/is false (str e))
                         (done))))))

(t/deftest set-management-shows-empty-message-test
  (t/async done
           (tlr/render (with-providers ($ set-management-view)
                         #js [mock-empty-sets-response]))
           (-> (tlr/wait-for #(tlr/get-by-text "No sets found. Create a set to get started."))
               (.then (fn []
                        (t/is (some? (tlr/get-by-text "No sets found. Create a set to get started.")))
                        (done)))
               (.catch (fn [e]
                         (t/is false (str e))
                         (done))))))

(t/deftest set-management-has-delete-buttons-test
  (t/async done
           (tlr/render (with-providers ($ set-management-view)))
           (-> (tlr/wait-for #(tlr/get-by-text "Core Set"))
               (.then (fn []
                        (let [delete-btns (screen/get-all-by-title "Delete set")]
                          (t/is (= 2 (count delete-btns))))
                        (done)))
               (.catch (fn [e]
                         (t/is false (str e))
                         (done))))))
