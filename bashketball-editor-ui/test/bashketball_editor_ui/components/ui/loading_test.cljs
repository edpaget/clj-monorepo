(ns bashketball-editor-ui.components.ui.loading-test
  (:require
   [bashketball-editor-ui.components.ui.loading :refer [button-spinner
                                                        loading-dots
                                                        loading-overlay
                                                        skeleton
                                                        spinner]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

;; -----------------------------------------------------------------------------
;; Spinner tests
;; -----------------------------------------------------------------------------

(t/deftest spinner-renders-test
  (t/testing "renders spinner with status role"
    (tlr/render ($ spinner))
    (t/is (some? (tlr/get-by-role "status")))))

(t/deftest spinner-has-default-label-test
  (t/testing "has accessible Loading label by default"
    (tlr/render ($ spinner))
    (t/is (some? (tlr/get-by-text "Loading")))))

(t/deftest spinner-custom-label-test
  (t/testing "accepts custom label"
    (tlr/render ($ spinner {:label "Please wait..."}))
    (let [status (tlr/get-by-role "status")]
      (t/is (= "Please wait..." (.getAttribute status "aria-label"))))))

(t/deftest spinner-size-classes-test
  (t/testing "applies size classes"
    (tlr/render ($ spinner {:size :lg}))
    (let [status (tlr/get-by-role "status")]
      (t/is (.includes (.-className status) "h-8"))
      (t/is (.includes (.-className status) "w-8")))))

(t/deftest spinner-has-animation-test
  (t/testing "has animate-spin class"
    (tlr/render ($ spinner))
    (let [status (tlr/get-by-role "status")]
      (t/is (.includes (.-className status) "animate-spin")))))

(t/deftest spinner-custom-class-test
  (t/testing "accepts custom class"
    (tlr/render ($ spinner {:class "text-blue-500"}))
    (let [status (tlr/get-by-role "status")]
      (t/is (.includes (.-className status) "text-blue-500")))))

;; -----------------------------------------------------------------------------
;; Skeleton tests
;; -----------------------------------------------------------------------------

(t/deftest skeleton-renders-test
  (t/testing "renders skeleton element"
    (tlr/render ($ skeleton {:class "h-4 w-32"}))
    (let [skel (js/document.querySelector ".animate-pulse")]
      (t/is (some? skel)))))

(t/deftest skeleton-is-aria-hidden-test
  (t/testing "skeleton is hidden from screen readers"
    (tlr/render ($ skeleton))
    (let [skel (js/document.querySelector ".animate-pulse")]
      (t/is (= "true" (.getAttribute skel "aria-hidden"))))))

(t/deftest skeleton-default-variant-test
  (t/testing "default variant has rounded-md"
    (tlr/render ($ skeleton))
    (let [skel (js/document.querySelector ".animate-pulse")]
      (t/is (.includes (.-className skel) "rounded-md")))))

(t/deftest skeleton-circle-variant-test
  (t/testing "circle variant has rounded-full"
    (tlr/render ($ skeleton {:variant :circle}))
    (let [skel (js/document.querySelector ".animate-pulse")]
      (t/is (.includes (.-className skel) "rounded-full")))))

(t/deftest skeleton-text-variant-test
  (t/testing "text variant has h-4 and rounded"
    (tlr/render ($ skeleton {:variant :text}))
    (let [skel (js/document.querySelector ".animate-pulse")]
      (t/is (.includes (.-className skel) "h-4"))
      (t/is (.includes (.-className skel) "rounded")))))

(t/deftest skeleton-custom-class-test
  (t/testing "accepts custom size classes"
    (tlr/render ($ skeleton {:class "h-20 w-full"}))
    (let [skel (js/document.querySelector ".animate-pulse")]
      (t/is (.includes (.-className skel) "h-20"))
      (t/is (.includes (.-className skel) "w-full")))))

;; -----------------------------------------------------------------------------
;; Loading overlay tests
;; -----------------------------------------------------------------------------

(t/deftest loading-overlay-renders-test
  (t/testing "renders loading overlay with status role"
    (tlr/render ($ loading-overlay))
    (let [statuses (tlr/get-all-by-role "status")]
      (t/is (= 2 (count statuses))))))

(t/deftest loading-overlay-has-aria-busy-test
  (t/testing "has aria-busy attribute"
    (tlr/render ($ loading-overlay))
    (let [overlay (js/document.querySelector "[aria-busy='true']")]
      (t/is (some? overlay)))))

(t/deftest loading-overlay-shows-message-test
  (t/testing "displays optional message"
    (tlr/render ($ loading-overlay {:message "Loading cards..."}))
    (t/is (some? (tlr/get-by-text "Loading cards...")))))

(t/deftest loading-overlay-no-message-test
  (t/testing "works without message"
    (tlr/render ($ loading-overlay))
    (t/is (nil? (tlr/query-by-text "Loading cards...")))))

;; -----------------------------------------------------------------------------
;; Loading dots tests
;; -----------------------------------------------------------------------------

(t/deftest loading-dots-renders-test
  (t/testing "renders loading dots with status role"
    (tlr/render ($ loading-dots))
    (t/is (some? (tlr/get-by-role "status")))))

(t/deftest loading-dots-has-three-dots-test
  (t/testing "renders three dots"
    (tlr/render ($ loading-dots))
    (let [dots (js/document.querySelectorAll ".animate-bounce")]
      (t/is (= 3 (.-length dots))))))

(t/deftest loading-dots-has-sr-label-test
  (t/testing "has screen reader label"
    (tlr/render ($ loading-dots))
    (t/is (some? (tlr/get-by-text "Loading")))))

;; -----------------------------------------------------------------------------
;; Button spinner tests
;; -----------------------------------------------------------------------------

(t/deftest button-spinner-renders-test
  (t/testing "renders small spinner for buttons"
    (tlr/render ($ button-spinner))
    (let [status (tlr/get-by-role "status")]
      (t/is (.includes (.-className status) "h-4"))
      (t/is (.includes (.-className status) "w-4")))))

(t/deftest button-spinner-has-margin-test
  (t/testing "has margin classes for button layout"
    (tlr/render ($ button-spinner))
    (let [status (tlr/get-by-role "status")]
      (t/is (.includes (.-className status) "-ml-1"))
      (t/is (.includes (.-className status) "mr-2")))))
