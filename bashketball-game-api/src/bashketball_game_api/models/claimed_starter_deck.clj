(ns bashketball-game-api.models.claimed-starter-deck
  "Claimed starter deck model.

  Tracks which starter decks each user has claimed. Links the starter deck
  definition ID to the actual deck created for the user."
  (:require
   [db.core :as db]
   [malli.core :as m]))

(def ClaimedStarterDeck
  "Malli schema for claimed starter deck entity."
  [:map
   [:user-id :uuid]
   [:starter-deck-id :string]
   [:deck-id :uuid]
   [:claimed-at {:optional true} inst?]])

(defn find-by-user
  "Returns all claimed starter decks for a given user."
  [user-id]
  (vec (db/execute!
        {:select [:*]
         :from [:claimed-starter-decks]
         :where [:= :user-id [:cast user-id :uuid]]
         :order-by [[:claimed-at :desc]]})))

(defn has-claimed?
  "Returns true if the user has claimed the specified starter deck."
  [user-id starter-deck-id]
  (some? (db/execute-one!
          {:select [1]
           :from [:claimed-starter-decks]
           :where [:and
                   [:= :user-id [:cast user-id :uuid]]
                   [:= :starter-deck-id starter-deck-id]]})))

(defn record-claim!
  "Records a starter deck claim for a user.

  Takes the user ID, starter deck definition ID, and the created deck ID.
  Returns the created claim record."
  [user-id starter-deck-id deck-id]
  {:pre [(m/validate ClaimedStarterDeck {:user-id user-id
                                         :starter-deck-id starter-deck-id
                                         :deck-id deck-id})]}
  (db/execute-one!
   {:insert-into :claimed-starter-decks
    :values [{:user-id [:cast user-id :uuid]
              :starter-deck-id starter-deck-id
              :deck-id [:cast deck-id :uuid]}]
    :returning [:*]}))
