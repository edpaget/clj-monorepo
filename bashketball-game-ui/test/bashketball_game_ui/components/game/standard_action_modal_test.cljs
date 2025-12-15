(ns bashketball-game-ui.components.game.standard-action-modal-test
  (:require
   [bashketball-game-ui.components.game.standard-action-modal :refer [standard-action-modal]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest modal-not-visible-when-closed-test
  (uix-tlr/render ($ standard-action-modal {:open?     false
                                            :on-select (fn [_])
                                            :on-close  (fn [])}))
  (t/is (nil? (screen/query-by-text "Select Standard Action"))))

(t/deftest modal-visible-when-open-test
  (uix-tlr/render ($ standard-action-modal {:open?     true
                                            :on-select (fn [_])
                                            :on-close  (fn [])}))
  (t/is (some? (screen/get-by-text "Select Standard Action"))))

(t/deftest modal-shows-three-standard-actions-test
  (uix-tlr/render ($ standard-action-modal {:open?     true
                                            :on-select (fn [_])
                                            :on-close  (fn [])}))
  (t/is (some? (screen/get-by-text "Shoot / Block")))
  (t/is (some? (screen/get-by-text "Pass / Steal")))
  (t/is (some? (screen/get-by-text "Screen / Check"))))

(t/deftest clicking-shoot-block-calls-on-select-test
  (t/async done
           (let [selected (atom nil)
                 _        (uix-tlr/render ($ standard-action-modal {:open?     true
                                                                    :on-select #(reset! selected %)
                                                                    :on-close  (fn [])}))
                 usr      (user/setup)
                 btn      (screen/get-by-text "Shoot / Block")]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= "shoot-block" @selected))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest clicking-pass-steal-calls-on-select-test
  (t/async done
           (let [selected (atom nil)
                 _        (uix-tlr/render ($ standard-action-modal {:open?     true
                                                                    :on-select #(reset! selected %)
                                                                    :on-close  (fn [])}))
                 usr      (user/setup)
                 btn      (screen/get-by-text "Pass / Steal")]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= "pass-steal" @selected))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest clicking-screen-check-calls-on-select-test
  (t/async done
           (let [selected (atom nil)
                 _        (uix-tlr/render ($ standard-action-modal {:open?     true
                                                                    :on-select #(reset! selected %)
                                                                    :on-close  (fn [])}))
                 usr      (user/setup)
                 btn      (screen/get-by-text "Screen / Check")]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= "screen-check" @selected))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest cancel-button-calls-on-close-test
  (t/async done
           (let [closed?    (atom false)
                 _          (uix-tlr/render ($ standard-action-modal {:open?     true
                                                                      :on-select (fn [_])
                                                                      :on-close  #(reset! closed? true)}))
                 usr        (user/setup)
                 cancel-btn (screen/get-by-text "Cancel")]
             (-> (user/click usr cancel-btn)
                 (.then (fn []
                          (t/is @closed?)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
