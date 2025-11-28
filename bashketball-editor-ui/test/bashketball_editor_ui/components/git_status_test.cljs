(ns bashketball-editor-ui.components.git-status-test
  "Tests for git status component and workflow buttons."
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.components.git-status :refer [git-status commit-button
                                                        push-button pull-button
                                                        clean-indicator]]
   [bashketball-editor-ui.graphql.queries :as q]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

;; -----------------------------------------------------------------------------
;; Mock Responses - Sync Status Query
;; -----------------------------------------------------------------------------

(defn mock-sync-status
  "Creates a mock sync status query response."
  [{:keys [ahead behind uncommitted]
    :or {ahead 0 behind 0 uncommitted 0}}]
  #js {:request #js {:query q/SYNC_STATUS_QUERY}
       :result #js {:data #js {:syncStatus #js {:ahead ahead
                                                :behind behind
                                                :uncommittedChanges uncommitted
                                                :isClean (and (zero? ahead)
                                                              (zero? behind)
                                                              (zero? uncommitted))}}}})

(def mock-clean-status
  (mock-sync-status {:ahead 0 :behind 0 :uncommitted 0}))

(def mock-dirty-status
  (mock-sync-status {:ahead 0 :behind 0 :uncommitted 3}))

(def mock-ahead-status
  (mock-sync-status {:ahead 2 :behind 0 :uncommitted 0}))

(def mock-behind-status
  (mock-sync-status {:ahead 0 :behind 1 :uncommitted 0}))

(def mock-mixed-status
  (mock-sync-status {:ahead 2 :behind 1 :uncommitted 3}))

;; -----------------------------------------------------------------------------
;; Mock Responses - Push Mutation
;; -----------------------------------------------------------------------------

(def mock-push-success
  #js {:request #js {:query q/PUSH_TO_REMOTE_MUTATION}
       :result #js {:data #js {:pushToRemote #js {:status "success"
                                                   :message "Pushed successfully"
                                                   :error nil
                                                   :conflicts nil}}}})

(def mock-push-error
  #js {:request #js {:query q/PUSH_TO_REMOTE_MUTATION}
       :result #js {:data #js {:pushToRemote #js {:status "error"
                                                   :message "Push failed"
                                                   :error "Authentication failed"
                                                   :conflicts nil}}}})

;; -----------------------------------------------------------------------------
;; Mock Responses - Pull Mutation
;; -----------------------------------------------------------------------------

(def mock-pull-success
  #js {:request #js {:query q/PULL_FROM_REMOTE_MUTATION}
       :result #js {:data #js {:pullFromRemote #js {:status "success"
                                                     :message "Pulled successfully"
                                                     :error nil
                                                     :conflicts nil}}}})

(def mock-pull-error
  #js {:request #js {:query q/PULL_FROM_REMOTE_MUTATION}
       :result #js {:data #js {:pullFromRemote #js {:status "error"
                                                     :message "Pull failed"
                                                     :error "Conflict detected"
                                                     :conflicts #js ["cards/test.edn"]}}}})

;; -----------------------------------------------------------------------------
;; Test Providers
;; -----------------------------------------------------------------------------

(defn with-providers
  "Wraps component with MockedProvider and MemoryRouter."
  [component mocks]
  ($ rr/MemoryRouter
     ($ MockedProvider {:mocks mocks}
        component)))

;; -----------------------------------------------------------------------------
;; Clean Indicator Tests
;; -----------------------------------------------------------------------------

(t/deftest clean-indicator-renders-test
  (t/testing "renders checkmark and text"
    (tlr/render ($ clean-indicator))
    (t/is (some? (tlr/get-by-text "All synced")))))

(t/deftest clean-indicator-has-title-test
  (t/testing "has descriptive title"
    (tlr/render ($ clean-indicator))
    (let [elem (.. (tlr/get-by-text "All synced") -parentElement)]
      (t/is (= "All changes saved and synced" (.getAttribute elem "title"))))))

;; -----------------------------------------------------------------------------
;; Commit Button Tests
;; -----------------------------------------------------------------------------

(t/deftest commit-button-renders-count-test
  (t/testing "displays count to commit"
    (tlr/render (with-providers ($ commit-button {:count 5}) #js []))
    (t/is (some? (tlr/get-by-text "5 to commit")))))

(t/deftest commit-button-is-clickable-test
  (t/testing "button is clickable"
    (tlr/render (with-providers ($ commit-button {:count 3}) #js []))
    (let [btn (tlr/get-by-role "button")]
      (t/is (not (.-disabled btn))))))

;; -----------------------------------------------------------------------------
;; Push Button Tests
;; -----------------------------------------------------------------------------

(t/deftest push-button-renders-count-test
  (t/testing "displays count to push"
    (tlr/render (with-providers ($ push-button {:count 2}) #js [mock-push-success]))
    (t/is (some? (tlr/get-by-text "2 to push")))))

(t/deftest push-button-has-title-test
  (t/testing "has descriptive title"
    (tlr/render (with-providers ($ push-button {:count 2}) #js [mock-push-success]))
    (let [btn (tlr/get-by-role "button")]
      (t/is (= "Share your commits with the remote repository" (.getAttribute btn "title"))))))

(t/deftest push-button-success-flow-test
  (t/async done
    (t/testing "shows success state after push"
      (let [user (tlr/setup)
            on-success (atom false)]
        (tlr/render (with-providers
                      ($ push-button {:count 2 :on-success #(reset! on-success true)})
                      #js [mock-push-success]))
        (-> (tlr/click user (tlr/get-by-role "button"))
            (.then #(tlr/wait-for
                     (fn [] (some? (tlr/query-by-text "Pushed!")))
                     #js {:timeout 3000}))
            (.then (fn []
                     (t/is (some? (tlr/query-by-text "Pushed!")))
                     (done)))
            (.catch (fn [e]
                      (t/is false (str e))
                      (done))))))))

(t/deftest push-button-error-flow-test
  (t/async done
    (t/testing "shows error state after failed push"
      (let [user (tlr/setup)]
        (tlr/render (with-providers ($ push-button {:count 2}) #js [mock-push-error]))
        (-> (tlr/click user (tlr/get-by-role "button"))
            (.then #(tlr/wait-for
                     (fn [] (some? (tlr/query-by-text "Failed")))
                     #js {:timeout 3000}))
            (.then (fn []
                     (t/is (some? (tlr/query-by-text "Failed")))
                     (done)))
            (.catch (fn [e]
                      (t/is false (str e))
                      (done))))))))

;; -----------------------------------------------------------------------------
;; Pull Button Tests
;; -----------------------------------------------------------------------------

(t/deftest pull-button-renders-count-test
  (t/testing "displays count to pull"
    (tlr/render (with-providers ($ pull-button {:count 1}) #js [mock-pull-success]))
    (t/is (some? (tlr/get-by-text "1 to pull")))))

(t/deftest pull-button-has-title-test
  (t/testing "has descriptive title"
    (tlr/render (with-providers ($ pull-button {:count 1}) #js [mock-pull-success]))
    (let [btn (tlr/get-by-role "button")]
      (t/is (= "Get updates from the remote repository" (.getAttribute btn "title"))))))

(t/deftest pull-button-success-flow-test
  (t/async done
    (t/testing "shows success state after pull"
      (let [user (tlr/setup)]
        (tlr/render (with-providers ($ pull-button {:count 1}) #js [mock-pull-success]))
        (-> (tlr/click user (tlr/get-by-role "button"))
            (.then #(tlr/wait-for
                     (fn [] (some? (tlr/query-by-text "Pulled!")))
                     #js {:timeout 3000}))
            (.then (fn []
                     (t/is (some? (tlr/query-by-text "Pulled!")))
                     (done)))
            (.catch (fn [e]
                      (t/is false (str e))
                      (done))))))))

(t/deftest pull-button-error-flow-test
  (t/async done
    (t/testing "shows error state after failed pull"
      (let [user (tlr/setup)]
        (tlr/render (with-providers ($ pull-button {:count 1}) #js [mock-pull-error]))
        (-> (tlr/click user (tlr/get-by-role "button"))
            (.then #(tlr/wait-for
                     (fn [] (some? (tlr/query-by-text "Failed")))
                     #js {:timeout 3000}))
            (.then (fn []
                     (t/is (some? (tlr/query-by-text "Failed")))
                     (done)))
            (.catch (fn [e]
                      (t/is false (str e))
                      (done))))))))

;; -----------------------------------------------------------------------------
;; Git Status Component Tests
;; -----------------------------------------------------------------------------

(t/deftest git-status-shows-clean-state-test
  (t/async done
    (t/testing "shows 'All synced' when clean"
      (tlr/render (with-providers ($ git-status) #js [mock-clean-status]))
      (-> (tlr/wait-for
           (fn [] (some? (tlr/query-by-text "All synced")))
           #js {:timeout 3000})
          (.then (fn []
                   (t/is (some? (tlr/query-by-text "All synced")))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest git-status-shows-commit-button-when-dirty-test
  (t/async done
    (t/testing "shows commit button when there are uncommitted changes"
      (tlr/render (with-providers ($ git-status) #js [mock-dirty-status]))
      (-> (tlr/wait-for
           (fn [] (some? (tlr/query-by-text "3 to commit")))
           #js {:timeout 3000})
          (.then (fn []
                   (t/is (some? (tlr/query-by-text "3 to commit")))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest git-status-shows-push-button-when-ahead-test
  (t/async done
    (t/testing "shows push button when ahead of remote"
      (tlr/render (with-providers ($ git-status) #js [mock-ahead-status mock-push-success]))
      (-> (tlr/wait-for
           (fn [] (some? (tlr/query-by-text "2 to push")))
           #js {:timeout 3000})
          (.then (fn []
                   (t/is (some? (tlr/query-by-text "2 to push")))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest git-status-shows-pull-button-when-behind-test
  (t/async done
    (t/testing "shows pull button when behind remote"
      (tlr/render (with-providers ($ git-status) #js [mock-behind-status mock-pull-success]))
      (-> (tlr/wait-for
           (fn [] (some? (tlr/query-by-text "1 to pull")))
           #js {:timeout 3000})
          (.then (fn []
                   (t/is (some? (tlr/query-by-text "1 to pull")))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest git-status-shows-all-buttons-when-mixed-test
  (t/async done
    (t/testing "shows all buttons when there are mixed states"
      (tlr/render (with-providers ($ git-status)
                    #js [mock-mixed-status mock-push-success mock-pull-success]))
      (-> (tlr/wait-for
           (fn [] (some? (tlr/query-by-text "3 to commit")))
           #js {:timeout 3000})
          (.then (fn []
                   (t/is (some? (tlr/query-by-text "3 to commit")))
                   (t/is (some? (tlr/query-by-text "2 to push")))
                   (t/is (some? (tlr/query-by-text "1 to pull")))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))
