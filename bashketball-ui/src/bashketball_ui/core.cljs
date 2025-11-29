(ns bashketball-ui.core
  "Core namespace for bashketball-ui shared library.

  Extends JavaScript objects with ILookup for idiomatic keyword access
  and provides convenient re-exports of commonly used components."
  (:require
   [bashketball-ui.cards.card-list-item]
   [bashketball-ui.cards.card-preview]
   [bashketball-ui.components.button]
   [bashketball-ui.components.input]
   [bashketball-ui.components.label]
   [bashketball-ui.components.loading]
   [bashketball-ui.components.protected-route]
   [bashketball-ui.components.select]
   [bashketball-ui.components.textarea]
   [bashketball-ui.context.auth]
   [bashketball-ui.hooks.form]
   [bashketball-ui.router]
   [bashketball-ui.utils]
   [goog.object :as obj]))

(extend-type object
  ILookup
  (-lookup ([o k] (obj/get o (name k)))
    ([o k not-found] (obj/get o (name k) not-found))))
