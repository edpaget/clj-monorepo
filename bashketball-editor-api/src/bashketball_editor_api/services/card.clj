(ns bashketball-editor-api.services.card
  "Card business logic service.

  Implements business rules and validation for card operations.")

(defrecord CardService [card-repo])

(defn create-card-service
  "Creates a card service instance."
  [card-repo]
  (->CardService card-repo))

;; TODO: Implement business logic methods
