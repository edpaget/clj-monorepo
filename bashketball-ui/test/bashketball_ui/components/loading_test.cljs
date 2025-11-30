(ns bashketball-ui.components.loading-test
  (:require
   [bashketball-ui.components.loading :refer [spinner skeleton loading-overlay loading-dots button-spinner]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest spinner-renders-with-status-role-test
  (uix-tlr/render ($ spinner {}))
  (t/is (some? (screen/get-by-role "status"))))

(t/deftest spinner-has-aria-label-test
  (uix-tlr/render ($ spinner {}))
  (let [el (screen/get-by-role "status")]
    (t/is (= "Loading" (.getAttribute el "aria-label")))))

(t/deftest spinner-custom-label-test
  (uix-tlr/render ($ spinner {:label "Processing"}))
  (let [el (screen/get-by-role "status")]
    (t/is (= "Processing" (.getAttribute el "aria-label")))))

(t/deftest spinner-md-size-default-test
  (uix-tlr/render ($ spinner {}))
  (let [el (screen/get-by-role "status")]
    (t/is (str/includes? (.-className el) "h-6"))))

(t/deftest spinner-sm-size-test
  (uix-tlr/render ($ spinner {:size :sm}))
  (let [el (screen/get-by-role "status")]
    (t/is (str/includes? (.-className el) "h-4"))))

(t/deftest spinner-lg-size-test
  (uix-tlr/render ($ spinner {:size :lg}))
  (let [el (screen/get-by-role "status")]
    (t/is (str/includes? (.-className el) "h-8"))))

(t/deftest spinner-xl-size-test
  (uix-tlr/render ($ spinner {:size :xl}))
  (let [el (screen/get-by-role "status")]
    (t/is (str/includes? (.-className el) "h-12"))))

(t/deftest spinner-has-animation-test
  (uix-tlr/render ($ spinner {}))
  (let [el (screen/get-by-role "status")]
    (t/is (str/includes? (.-className el) "animate-spin"))))

(t/deftest skeleton-renders-hidden-test
  (uix-tlr/render ($ :div {:data-testid "container"} ($ skeleton {:class "h-12 w-full"})))
  (let [container (screen/get-by-test-id "container")
        child     (.-firstChild container)]
    (t/is (= "true" (.getAttribute child "aria-hidden")))))

(t/deftest skeleton-default-variant-test
  (uix-tlr/render ($ :div {:data-testid "container"} ($ skeleton {})))
  (let [container (screen/get-by-test-id "container")
        child     (.-firstChild container)]
    (t/is (str/includes? (.-className child) "rounded-md"))))

(t/deftest skeleton-circle-variant-test
  (uix-tlr/render ($ :div {:data-testid "container"} ($ skeleton {:variant :circle})))
  (let [container (screen/get-by-test-id "container")
        child     (.-firstChild container)]
    (t/is (str/includes? (.-className child) "rounded-full"))))

(t/deftest skeleton-has-animation-test
  (uix-tlr/render ($ :div {:data-testid "container"} ($ skeleton {})))
  (let [container (screen/get-by-test-id "container")
        child     (.-firstChild container)]
    (t/is (str/includes? (.-className child) "animate-pulse"))))

(t/deftest loading-overlay-renders-status-test
  (uix-tlr/render ($ loading-overlay {}))
  (let [elements (screen/get-all-by-role "status")]
    (t/is (= 2 (.-length elements)))))

(t/deftest loading-overlay-busy-state-test
  (uix-tlr/render ($ loading-overlay {}))
  (let [elements (screen/get-all-by-role "status")
        overlay  (aget elements 0)]
    (t/is (= "true" (.getAttribute overlay "aria-busy")))))

(t/deftest loading-overlay-with-message-test
  (uix-tlr/render ($ loading-overlay {:message "Please wait..."}))
  (t/is (some? (screen/get-by-text "Please wait..."))))

(t/deftest loading-dots-renders-status-test
  (uix-tlr/render ($ loading-dots {}))
  (t/is (some? (screen/get-by-role "status"))))

(t/deftest loading-dots-has-aria-label-test
  (uix-tlr/render ($ loading-dots {}))
  (let [el (screen/get-by-role "status")]
    (t/is (= "Loading" (.getAttribute el "aria-label")))))

(t/deftest button-spinner-renders-small-test
  (uix-tlr/render ($ button-spinner {}))
  (let [el (screen/get-by-role "status")]
    (t/is (str/includes? (.-className el) "h-4"))))
