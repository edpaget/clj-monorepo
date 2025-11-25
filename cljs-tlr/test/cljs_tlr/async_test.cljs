(ns cljs-tlr.async-test
  (:require
   ["react" :refer [createElement useEffect useState]]
   [cljs-tlr.async :as async]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs-tlr.screen :as screen]
   [cljs.test :as t :include-macros true]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest wait-for-element-test
  (t/async done
           (let [component (fn []
                             (let [[show set-show] (useState false)]
                               (useEffect (fn []
                                            (let [timer (js/setTimeout #(set-show true) 50)]
                                              (fn [] (js/clearTimeout timer))))
                                          #js [])
                               (when show
                                 (createElement "div" nil "Loaded!"))))
                 _         (render/render (createElement component))]
             (-> (async/wait-for #(screen/get-by-text "Loaded!"))
                 (.then (fn [element]
                          (t/is (some? element))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest wait-for-with-timeout-test
  (t/async done
           (let [component (fn []
                             (createElement "div" nil "Static content"))
                 _         (render/render (createElement component))]
             (-> (async/wait-for #(screen/get-by-text "Never appears") {:timeout 100})
                 (.then (fn []
                          (t/is false "Should have timed out")
                          (done)))
                 (.catch (fn [_e]
                           (t/is true "Correctly timed out")
                           (done)))))))

(t/deftest wait-for-element-to-be-removed-test
  (t/async done
           (let [component (fn []
                             (let [[loading set-loading] (useState true)]
                               (useEffect (fn []
                                            (let [timer (js/setTimeout #(set-loading false) 50)]
                                              (fn [] (js/clearTimeout timer))))
                                          #js [])
                               (when loading
                                 (createElement "div" nil "Loading..."))))
                 _         (render/render (createElement component))]
             (-> (async/wait-for-element-to-be-removed #(screen/query-by-text "Loading..."))
                 (.then (fn []
                          (t/is (nil? (screen/query-by-text "Loading...")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest find-by-returns-promise-test
  (t/async done
           (let [component (fn []
                             (let [[show set-show] (useState false)]
                               (useEffect (fn []
                                            (let [timer (js/setTimeout #(set-show true) 50)]
                                              (fn [] (js/clearTimeout timer))))
                                          #js [])
                               (when show
                                 (createElement "button" nil "Async Button"))))
                 _         (render/render (createElement component))]
             (-> (screen/find-by-role "button")
                 (.then (fn [button]
                          (t/is (some? button))
                          (t/is (= "Async Button" (.-textContent button)))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
