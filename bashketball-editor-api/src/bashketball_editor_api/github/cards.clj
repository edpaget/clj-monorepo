(ns bashketball-editor-api.github.cards
  "Card repository backed by GitHub.

  Stores cards as EDN files in a GitHub repository under `cards/<set-id>/<card-id>.edn`.")

(def Card
  "Malli schema for card entity."
  [:map
   [:id {:optional true} :uuid]
   [:set-id :uuid]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:attributes {:optional true} :map]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defrecord CardRepository [github-client])

(defn create-card-repository
  "Creates a new GitHub-backed card repository."
  [github-client]
  (->CardRepository github-client))

;; TODO: Implement Repository protocol methods for GitHub-backed storage
