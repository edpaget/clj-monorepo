(ns bashketball-game-ui.components.game.create-token-modal-test
  (:require
   [bashketball-game-ui.components.game.create-token-modal :refer [create-token-modal]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-players
  [{:id "player-1" :name "Player One"}
   {:id "player-2" :name "Player Two"}])

(t/deftest modal-not-visible-when-closed-test
  (uix-tlr/render ($ create-token-modal {:open?     false
                                         :players   sample-players
                                         :on-create (fn [_])
                                         :on-close  (fn [])}))
  (t/is (nil? (screen/query-by-text "Create Token"))))

(t/deftest modal-visible-when-open-test
  (uix-tlr/render ($ create-token-modal {:open?     true
                                         :players   sample-players
                                         :on-create (fn [_])
                                         :on-close  (fn [])}))
  (t/is (some? (screen/get-by-text "Create Token"))))

(t/deftest modal-shows-name-input-test
  (uix-tlr/render ($ create-token-modal {:open?     true
                                         :players   sample-players
                                         :on-create (fn [_])
                                         :on-close  (fn [])}))
  (t/is (some? (screen/get-by-text "Token Name")))
  (t/is (some? (screen/get-by-placeholder-text "Enter token name..."))))

(t/deftest modal-shows-placement-options-test
  (uix-tlr/render ($ create-token-modal {:open?     true
                                         :players   sample-players
                                         :on-create (fn [_])
                                         :on-close  (fn [])}))
  (t/is (some? (screen/get-by-text "Asset")))
  (t/is (some? (screen/get-by-text "Attach to Player"))))

(t/deftest modal-shows-players-when-attach-selected-test
  (t/async done
           (let [_          (uix-tlr/render ($ create-token-modal {:open?     true
                                                                   :players   sample-players
                                                                   :on-create (fn [_])
                                                                   :on-close  (fn [])}))
                 usr        (user/setup)
                 attach-btn (screen/get-by-text "Attach to Player")]
             (-> (user/click usr attach-btn)
                 (.then (fn []
                          (t/is (some? (screen/get-by-text "Player One")))
                          (t/is (some? (screen/get-by-text "Player Two")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest cancel-button-calls-on-close-test
  (t/async done
           (let [closed?    (atom false)
                 _          (uix-tlr/render ($ create-token-modal {:open?     true
                                                                   :players   sample-players
                                                                   :on-create (fn [_])
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

(t/deftest create-button-disabled-without-name-test
  (uix-tlr/render ($ create-token-modal {:open?     true
                                         :players   sample-players
                                         :on-create (fn [_])
                                         :on-close  (fn [])}))
  (let [create-btn (screen/get-by-text "Create")]
    (t/is (.-disabled create-btn))))

(t/deftest create-calls-handler-with-asset-placement-test
  (t/async done
           (let [result     (atom nil)
                 _          (uix-tlr/render ($ create-token-modal {:open?     true
                                                                   :players   sample-players
                                                                   :on-create #(reset! result %)
                                                                   :on-close  (fn [])}))
                 usr        (user/setup)
                 input      (screen/get-by-placeholder-text "Enter token name...")
                 create-btn (screen/get-by-text "Create")]
             (-> (user/type-text usr input "My Token")
                 (.then #(user/click usr create-btn))
                 (.then (fn []
                          (t/is (= "My Token" (:name @result)))
                          (t/is (= :placement/ASSET (:placement @result)))
                          (t/is (nil? (:target-player-id @result)))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest create-calls-handler-with-attach-placement-test
  (t/async done
           (let [result     (atom nil)
                 _          (uix-tlr/render ($ create-token-modal {:open?     true
                                                                   :players   sample-players
                                                                   :on-create #(reset! result %)
                                                                   :on-close  (fn [])}))
                 usr        (user/setup)
                 input      (screen/get-by-placeholder-text "Enter token name...")
                 attach-btn (screen/get-by-text "Attach to Player")]
             (-> (user/type-text usr input "Attached Token")
                 (.then #(user/click usr attach-btn))
                 (.then #(user/click usr (screen/get-by-text "Player One")))
                 (.then #(user/click usr (screen/get-by-text "Create")))
                 (.then (fn []
                          (t/is (= "Attached Token" (:name @result)))
                          (t/is (= :placement/ATTACH (:placement @result)))
                          (t/is (= "player-1" (:target-player-id @result)))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
