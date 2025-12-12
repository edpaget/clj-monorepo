(ns bashketball-ui.components.avatar-test
  (:require
   [bashketball-ui.components.avatar :refer [avatar avatar-fallback]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest avatar-fallback-renders-initials-from-name-test
  (uix-tlr/render ($ avatar-fallback {:name "John Doe" :email "john@example.com"}))
  (t/is (some? (screen/get-by-text "JD"))))

(t/deftest avatar-fallback-renders-single-initial-for-single-name-test
  (uix-tlr/render ($ avatar-fallback {:name "John"}))
  (t/is (some? (screen/get-by-text "J"))))

(t/deftest avatar-fallback-uses-email-when-no-name-test
  (uix-tlr/render ($ avatar-fallback {:email "jane@example.com"}))
  (t/is (some? (screen/get-by-text "J"))))

(t/deftest avatar-fallback-has-img-role-test
  (uix-tlr/render ($ avatar-fallback {:name "Test User"}))
  (t/is (some? (screen/get-by-role "img"))))

(t/deftest avatar-fallback-has-aria-label-test
  (uix-tlr/render ($ avatar-fallback {:name "Test User"}))
  (let [el (screen/get-by-role "img")]
    (t/is (= "Test User" (.getAttribute el "aria-label")))))

(t/deftest avatar-fallback-md-size-default-test
  (uix-tlr/render ($ avatar-fallback {:name "Test"}))
  (let [el (screen/get-by-role "img")]
    (t/is (str/includes? (.-className el) "w-8"))))

(t/deftest avatar-fallback-sm-size-test
  (uix-tlr/render ($ avatar-fallback {:name "Test" :size :sm}))
  (let [el (screen/get-by-role "img")]
    (t/is (str/includes? (.-className el) "w-6"))))

(t/deftest avatar-fallback-lg-size-test
  (uix-tlr/render ($ avatar-fallback {:name "Test" :size :lg}))
  (let [el (screen/get-by-role "img")]
    (t/is (str/includes? (.-className el) "w-12"))))

(t/deftest avatar-renders-image-when-src-provided-test
  (uix-tlr/render ($ avatar {:src "/api/avatars/123" :name "Test User"}))
  (t/is (some? (screen/get-by-role "img"))))

(t/deftest avatar-image-has-correct-src-test
  (uix-tlr/render ($ avatar {:src "/api/avatars/123" :name "Test User"}))
  (let [img (screen/get-by-role "img")]
    (t/is (str/ends-with? (.getAttribute img "src") "/api/avatars/123"))))

(t/deftest avatar-image-has-alt-text-test
  (uix-tlr/render ($ avatar {:src "/api/avatars/123" :name "Test User"}))
  (let [img (screen/get-by-role "img")]
    (t/is (= "Test User" (.getAttribute img "alt")))))

(t/deftest avatar-shows-fallback-when-no-src-test
  (uix-tlr/render ($ avatar {:name "John Doe"}))
  (t/is (some? (screen/get-by-text "JD"))))

(t/deftest avatar-md-size-default-test
  (uix-tlr/render ($ avatar {:src "/test.png" :name "Test"}))
  (let [container (.. (screen/get-by-role "img") -parentElement)]
    (t/is (str/includes? (.-className container) "w-8"))))
