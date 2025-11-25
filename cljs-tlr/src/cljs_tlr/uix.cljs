(ns cljs-tlr.uix
  "UIx-specific testing utilities.

  Provides convenience functions for rendering UIx components
  in tests. UIx components compile to React elements, so they
  work directly with testing-library, but these helpers make
  common patterns more ergonomic."
  (:require
   [cljs-tlr.render :as render]
   [cljs-tlr.user-event :as user-event]))

(defn render
  "Renders a UIx component and returns query utilities.

  This is identical to [[cljs-tlr.render/render]] but serves as
  documentation that UIx components work directly with testing-library.

  Example:

      (ns my-app.button-test
        (:require
         [cljs.test :as t :include-macros true]
         [cljs-tlr.uix :as uix-tlr]
         [cljs-tlr.screen :as screen]
         [cljs-tlr.fixtures :as fixtures]
         [uix.core :refer [$ defui]]))

      (defui button [{:keys [on-click children]}]
        ($ :button {:on-click on-click} children))

      (t/use-fixtures :each fixtures/cleanup-fixture)

      (t/deftest button-renders-children-test
        (uix-tlr/render ($ button {} \"Click me\"))
        (t/is (some? (screen/get-by-role \"button\" {:name \"Click me\"}))))"
  (^js [element]
   (render/render element))
  (^js [element opts]
   (render/render element opts)))

(defn render-with-wrapper
  "Renders a UIx component wrapped in a context provider.

  The wrapper-component should be a UIx component that accepts
  children. This is useful for providing React Context to
  components under test.

  Example:

      (defui theme-provider [{:keys [theme children]}]
        ($ ThemeContext.Provider {:value theme} children))

      (render-with-wrapper
        ($ my-themed-button)
        {:wrapper (fn [props]
                    ($ theme-provider {:theme \"dark\"}
                       (.-children props)))})"
  (^js [element wrapper-opts]
   (render/render element wrapper-opts)))

(defn setup-user
  "Creates a user-event instance configured for UIx testing.

  This is a convenience wrapper around [[cljs-tlr.user-event/setup]]
  with sensible defaults for UIx component testing.

  Returns a user object for interaction functions."
  (^js []
   (user-event/setup))
  (^js [opts]
   (user-event/setup opts)))
