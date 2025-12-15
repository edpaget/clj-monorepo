(ns bashketball-game-ui.components.game.player-token-test
  (:require
   [bashketball-game-ui.components.game.player-token :refer [player-token]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def base-player
  {:id         "player-1"
   :name       "Guard"
   :position   [2 5]
   :exhausted  false
   :attachments []})

(defn wrap-in-svg
  "Wraps player-token in an SVG element for testing."
  [props]
  ($ :svg {:viewBox "0 0 800 600"}
     ($ player-token props)))

(t/deftest player-token-renders-circle-test
  (let [{:keys [container]} (uix-tlr/render
                             (wrap-in-svg {:player     base-player
                                           :team       :team/HOME
                                           :player-num 1}))]
    (t/is (>= (.-length (.querySelectorAll container "circle")) 1))))

(t/deftest player-token-shows-jersey-number-test
  (let [{:keys [container]} (uix-tlr/render
                             (wrap-in-svg {:player     base-player
                                           :team       :team/HOME
                                           :player-num 1}))
        texts               (.querySelectorAll container "text")
        text-contents       (map #(.-textContent %) (array-seq texts))]
    (t/is (some #(= "G1" %) text-contents))))

(t/deftest player-token-no-exhaust-badge-when-not-exhausted-test
  (let [{:keys [container]} (uix-tlr/render
                             (wrap-in-svg {:player     base-player
                                           :team       :team/HOME
                                           :player-num 1}))
        texts               (.querySelectorAll container "text")
        text-contents       (map #(.-textContent %) (array-seq texts))]
    (t/is (not (some #(= "E" %) text-contents)))))

(t/deftest player-token-shows-exhaust-badge-when-exhausted-test
  (let [exhausted-player    (assoc base-player :exhausted true)
        {:keys [container]} (uix-tlr/render
                             (wrap-in-svg {:player     exhausted-player
                                           :team       :team/HOME
                                           :player-num 1}))
        texts               (.querySelectorAll container "text")
        text-contents       (map #(.-textContent %) (array-seq texts))]
    (t/is (some #(= "E" %) text-contents))))

(t/deftest player-token-exhaust-badge-has-gray-circle-test
  (let [exhausted-player    (assoc base-player :exhausted true)
        {:keys [container]} (uix-tlr/render
                             (wrap-in-svg {:player     exhausted-player
                                           :team       :team/HOME
                                           :player-num 1}))
        circles             (.querySelectorAll container "circle")
        fills               (map #(.getAttribute % "fill") (array-seq circles))]
    (t/is (some #(= "#64748b" %) fills))))

(t/deftest player-token-has-reduced-opacity-when-exhausted-test
  (let [exhausted-player    (assoc base-player :exhausted true)
        {:keys [container]} (uix-tlr/render
                             (wrap-in-svg {:player     exhausted-player
                                           :team       :team/HOME
                                           :player-num 1}))
        circles             (.querySelectorAll container "circle")
        opacities           (keep #(let [op (.getAttribute % "opacity")]
                                     (when op (js/parseFloat op)))
                                  (array-seq circles))]
    (t/is (some #(= 0.5 %) opacities))))

(t/deftest player-token-context-menu-calls-handler-test
  (t/async done
           (let [called-id           (atom nil)
                 {:keys [container]} (uix-tlr/render
                                      (wrap-in-svg {:player              base-player
                                                    :team                :team/HOME
                                                    :player-num          1
                                                    :on-toggle-exhausted #(reset! called-id %)}))
                 g-elements          (.querySelectorAll container "g")
                 player-group        (first (filter #(= "cursor-pointer"
                                                        (.getAttribute % "class"))
                                                    (array-seq g-elements)))
                 event               (js/MouseEvent. "contextmenu"
                                                     #js {:bubbles    true
                                                          :cancelable true})]
             (if player-group
               (do
                 (.dispatchEvent player-group event)
                 (js/setTimeout
                  (fn []
                    (t/is (= "player-1" @called-id))
                    (done))
                  50))
               (do
                 (t/is false "No player group found")
                 (done))))))
