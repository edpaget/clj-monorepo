(ns bashketball-editor-ui.components.sync-button-test
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   [bashketball-editor-ui.components.sync-button :refer [sync-button]]
   [bashketball-editor-ui.graphql.queries :as q]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-success-response
  #js {:request #js {:query q/PULL_FROM_REMOTE_MUTATION}
       :result #js {:data #js {:pullFromRemote #js {:status "success"
                                                     :message "Pulled successfully"
                                                     :error nil
                                                     :conflicts nil}}}})

(def mock-error-response
  #js {:request #js {:query q/PULL_FROM_REMOTE_MUTATION}
       :result #js {:data #js {:pullFromRemote #js {:status "error"
                                                     :message "Pull failed"
                                                     :error "Conflict detected"
                                                     :conflicts #js ["file1.edn"]}}}})

(def mock-network-error
  #js {:request #js {:query q/PULL_FROM_REMOTE_MUTATION}
       :error (js/Error. "Network error")})

(defn with-mock-provider
  [mocks component]
  ($ MockedProvider {:mocks mocks}
     component))

;; -----------------------------------------------------------------------------
;; Render tests
;; -----------------------------------------------------------------------------

(t/deftest sync-button-renders-test
  (t/testing "renders button"
    (tlr/render (with-mock-provider #js [mock-success-response]
                                    ($ sync-button)))
    (t/is (some? (tlr/get-by-role "button")))))

(t/deftest sync-button-has-title-test
  (t/testing "has 'Pull from remote' title"
    (tlr/render (with-mock-provider #js [mock-success-response]
                                    ($ sync-button)))
    (let [btn (tlr/get-by-role "button")]
      (t/is (= "Pull from remote" (.getAttribute btn "title"))))))

(t/deftest sync-button-has-svg-icon-test
  (t/testing "renders svg icon"
    (tlr/render (with-mock-provider #js [mock-success-response]
                                    ($ sync-button)))
    (t/is (some? (js/document.querySelector "button svg")))))

;; -----------------------------------------------------------------------------
;; Success flow test
;; -----------------------------------------------------------------------------

(t/deftest sync-button-success-flow-test
  (t/async done
    (t/testing "shows success state after successful pull"
      (let [user (tlr/setup)]
        (tlr/render (with-mock-provider #js [mock-success-response]
                                        ($ sync-button)))
        (-> (tlr/click user (tlr/get-by-role "button"))
            (.then #(tlr/wait-for
                     (fn []
                       (let [btn (tlr/get-by-role "button")]
                         (= "Pull successful" (.getAttribute btn "title"))))
                     #js {:timeout 3000}))
            (.then (fn []
                     (let [btn (tlr/get-by-role "button")]
                       (t/is (= "Pull successful" (.getAttribute btn "title"))))
                     (done)))
            (.catch (fn [e]
                      (t/is false (str e))
                      (done))))))))

;; -----------------------------------------------------------------------------
;; Error flow test
;; -----------------------------------------------------------------------------

(t/deftest sync-button-error-flow-test
  (t/async done
    (t/testing "shows error state after failed pull"
      (let [user (tlr/setup)]
        (tlr/render (with-mock-provider #js [mock-error-response]
                                        ($ sync-button)))
        (-> (tlr/click user (tlr/get-by-role "button"))
            (.then #(tlr/wait-for
                     (fn []
                       (let [btn (tlr/get-by-role "button")]
                         (= "Conflict detected" (.getAttribute btn "title"))))
                     #js {:timeout 3000}))
            (.then (fn []
                     (let [btn (tlr/get-by-role "button")]
                       (t/is (= "Conflict detected" (.getAttribute btn "title"))))
                     (done)))
            (.catch (fn [e]
                      (t/is false (str e))
                      (done))))))))

;; -----------------------------------------------------------------------------
;; Network error test
;; -----------------------------------------------------------------------------

(t/deftest sync-button-network-error-test
  (t/async done
    (t/testing "shows error state on network failure"
      (let [user (tlr/setup)]
        (tlr/render (with-mock-provider #js [mock-network-error]
                                        ($ sync-button)))
        (-> (tlr/click user (tlr/get-by-role "button"))
            (.then #(tlr/wait-for
                     (fn []
                       (let [btn (tlr/get-by-role "button")]
                         (= "Network error" (.getAttribute btn "title"))))
                     #js {:timeout 3000}))
            (.then (fn []
                     (let [btn (tlr/get-by-role "button")]
                       (t/is (= "Network error" (.getAttribute btn "title"))))
                     (done)))
            (.catch (fn [e]
                      (t/is false (str e))
                      (done))))))))
