(ns bashketball-editor-api.github.sets
  "Card set repository backed by GitHub.

  Stores card sets as EDN files in a GitHub repository under `sets/<set-id>/metadata.edn`."
  (:require
   [bashketball-editor-api.github.client :as client]
   [bashketball-editor-api.models.protocol :as proto]
   [malli.core :as m]))

(def CardSet
  "Malli schema for card set entity."
  [:map
   [:id {:optional true} :uuid]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defrecord SetRepository [github-client])

(defn create-set-repository
  "Creates a new GitHub-backed card set repository."
  [github-client]
  (->SetRepository github-client))

;; TODO: Implement Repository protocol methods for GitHub-backed storage
