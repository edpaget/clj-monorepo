(ns cljs-tlr.core
  "ClojureScript wrapper for @testing-library/react.

  This namespace re-exports the most commonly used functions from
  the library's sub-namespaces for convenient single-require usage.

  For full functionality, require individual namespaces:

  - [[cljs-tlr.render]] - Component rendering
  - [[cljs-tlr.screen]] - DOM queries
  - [[cljs-tlr.events]] - Low-level event firing
  - [[cljs-tlr.user-event]] - High-level user simulation
  - [[cljs-tlr.async]] - Async utilities
  - [[cljs-tlr.fixtures]] - Test fixtures
  - [[cljs-tlr.uix]] - UIx-specific utilities

  Example usage:

      (ns my-app.component-test
        (:require
         [cljs.test :as t :include-macros true]
         [cljs-tlr.core :as tlr]
         [cljs-tlr.fixtures :as fixtures]
         [uix.core :refer [$ defui]]))

      (t/use-fixtures :each fixtures/cleanup-fixture)

      (t/deftest my-component-test
        (tlr/render ($ my-component {:name \"test\"}))
        (t/is (some? (tlr/get-by-text \"Hello, test\"))))"
  (:require
   [cljs-tlr.async :as async]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.user-event :as user-event]))

;; Re-export render functions

(def ^{:doc "See [[cljs-tlr.render/render]]"} render render/render)
(def ^{:doc "See [[cljs-tlr.render/render-hook]]"} render-hook render/render-hook)
(def ^{:doc "See [[cljs-tlr.render/rerender]]"} rerender render/rerender)
(def ^{:doc "See [[cljs-tlr.render/unmount]]"} unmount render/unmount)

;; Re-export most common screen queries

(def ^{:doc "See [[cljs-tlr.screen/get-by-role]]"} get-by-role screen/get-by-role)
(def ^{:doc "See [[cljs-tlr.screen/query-by-role]]"} query-by-role screen/query-by-role)
(def ^{:doc "See [[cljs-tlr.screen/find-by-role]]"} find-by-role screen/find-by-role)
(def ^{:doc "See [[cljs-tlr.screen/get-all-by-role]]"} get-all-by-role screen/get-all-by-role)

(def ^{:doc "See [[cljs-tlr.screen/get-by-text]]"} get-by-text screen/get-by-text)
(def ^{:doc "See [[cljs-tlr.screen/query-by-text]]"} query-by-text screen/query-by-text)
(def ^{:doc "See [[cljs-tlr.screen/find-by-text]]"} find-by-text screen/find-by-text)
(def ^{:doc "See [[cljs-tlr.screen/get-all-by-text]]"} get-all-by-text screen/get-all-by-text)

(def ^{:doc "See [[cljs-tlr.screen/get-by-label-text]]"} get-by-label-text screen/get-by-label-text)
(def ^{:doc "See [[cljs-tlr.screen/query-by-label-text]]"} query-by-label-text screen/query-by-label-text)
(def ^{:doc "See [[cljs-tlr.screen/find-by-label-text]]"} find-by-label-text screen/find-by-label-text)

(def ^{:doc "See [[cljs-tlr.screen/get-by-placeholder-text]]"} get-by-placeholder-text screen/get-by-placeholder-text)
(def ^{:doc "See [[cljs-tlr.screen/query-by-placeholder-text]]"} query-by-placeholder-text screen/query-by-placeholder-text)

(def ^{:doc "See [[cljs-tlr.screen/get-by-display-value]]"} get-by-display-value screen/get-by-display-value)
(def ^{:doc "See [[cljs-tlr.screen/query-by-display-value]]"} query-by-display-value screen/query-by-display-value)

(def ^{:doc "See [[cljs-tlr.screen/get-by-test-id]]"} get-by-test-id screen/get-by-test-id)
(def ^{:doc "See [[cljs-tlr.screen/query-by-test-id]]"} query-by-test-id screen/query-by-test-id)

(def ^{:doc "See [[cljs-tlr.screen/debug]]"} debug screen/debug)

;; Re-export user-event functions

(def ^{:doc "See [[cljs-tlr.user-event/setup]]"} setup user-event/setup)
(def ^{:doc "See [[cljs-tlr.user-event/click]]"} click user-event/click)
(def ^{:doc "See [[cljs-tlr.user-event/dbl-click]]"} dbl-click user-event/dbl-click)
(def ^{:doc "See [[cljs-tlr.user-event/type-text]]"} type-text user-event/type-text)
(def ^{:doc "See [[cljs-tlr.user-event/clear]]"} clear user-event/clear)
(def ^{:doc "See [[cljs-tlr.user-event/select-options]]"} select-options user-event/select-options)
(def ^{:doc "See [[cljs-tlr.user-event/tab]]"} tab user-event/tab)
(def ^{:doc "See [[cljs-tlr.user-event/keyboard]]"} keyboard user-event/keyboard)
(def ^{:doc "See [[cljs-tlr.user-event/hover]]"} hover user-event/hover)

;; Re-export async utilities

(def ^{:doc "See [[cljs-tlr.async/wait-for]]"} wait-for async/wait-for)
(def ^{:doc "See [[cljs-tlr.async/wait-for-element-to-be-removed]]"} wait-for-element-to-be-removed async/wait-for-element-to-be-removed)
(def ^{:doc "See [[cljs-tlr.async/act]]"} act async/act)

;; Re-export fixtures

(def ^{:doc "See [[cljs-tlr.fixtures/cleanup-fixture]]"} cleanup-fixture fixtures/cleanup-fixture)
(def ^{:doc "See [[cljs-tlr.fixtures/configure!]]"} configure! fixtures/configure!)
