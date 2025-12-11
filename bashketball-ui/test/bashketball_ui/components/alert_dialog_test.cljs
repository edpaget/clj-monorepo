(ns bashketball-ui.components.alert-dialog-test
  "Tests for alert-dialog component structure and styling.

  Note: Radix AlertDialog Portal does not work well in JSDom environment,
  so tests focus on component structure and CSS classes rather than
  user interactions with the portal content."
  (:require
   [bashketball-ui.components.alert-dialog :as alert]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest alert-dialog-trigger-renders-test
  (uix-tlr/render ($ alert/alert-dialog {:open false :on-open-change identity}
                     ($ alert/alert-dialog-trigger
                        ($ :button "Open Dialog"))))
  (t/is (some? (screen/get-by-text "Open Dialog"))))

(t/deftest alert-dialog-trigger-renders-button-test
  (uix-tlr/render ($ alert/alert-dialog {:open false :on-open-change identity}
                     ($ alert/alert-dialog-trigger
                        ($ :button {:class "custom-btn"} "Click Me"))))
  (t/is (some? (screen/get-by-role "button" {:name "Click Me"}))))

(t/deftest alert-dialog-content-hidden-when-closed-test
  (uix-tlr/render ($ alert/alert-dialog {:open false :on-open-change identity}
                     ($ alert/alert-dialog-trigger
                        ($ :button "Open"))
                     ($ alert/alert-dialog-content
                        ($ alert/alert-dialog-title "Hidden Title"))))
  (t/is (nil? (screen/query-by-text "Hidden Title"))))

(t/deftest alert-dialog-overlay-classes-test
  (t/is (str/includes? alert/overlay-classes "fixed"))
  (t/is (str/includes? alert/overlay-classes "inset-0"))
  (t/is (str/includes? alert/overlay-classes "z-50")))

(t/deftest alert-dialog-content-classes-test
  (t/is (str/includes? alert/content-classes "fixed"))
  (t/is (str/includes? alert/content-classes "bg-white"))
  (t/is (str/includes? alert/content-classes "z-50"))
  (t/is (str/includes? alert/content-classes "shadow-lg")))
