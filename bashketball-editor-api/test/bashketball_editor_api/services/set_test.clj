(ns bashketball-editor-api.services.set-test
  (:require
   [bashketball-editor-api.models.protocol :as repo]
   [bashketball-editor-api.services.set :as set-svc]
   [clojure.test :refer [deftest is testing]]))

(defrecord MockSetRepo [deleted-slugs]
  repo/Repository
  (find-by [_ _] nil)
  (find-all [_ _] [])
  (create! [_ _] nil)
  (update! [_ _ _] nil)
  (delete! [_ slug]
    (swap! deleted-slugs conj slug)
    true))

(defrecord MockCardRepo [cards deleted-cards]
  repo/Repository
  (find-by [_ _] nil)
  (find-all [_ opts]
    (let [set-slug (get-in opts [:where :set-slug])]
      (filter #(= (:set-slug %) set-slug) @cards)))
  (create! [_ _] nil)
  (update! [_ _ _] nil)
  (delete! [_ criteria]
    (swap! deleted-cards conj criteria)
    true))

(deftest create-set-service-test
  (testing "creates a SetService record"
    (let [mock-set-repo  {:type :set-repo}
          mock-card-repo {:type :card-repo}
          service        (set-svc/create-set-service mock-set-repo mock-card-repo)]
      (is (instance? bashketball_editor_api.services.set.SetService service))
      (is (= mock-set-repo (:set-repo service)))
      (is (= mock-card-repo (:card-repo service))))))

(deftest delete-set-cascade-deletes-cards-test
  (testing "delete-set! deletes all cards in the set before deleting the set"
    (let [cards         (atom [{:slug "card-1" :set-slug "test-set"}
                               {:slug "card-2" :set-slug "test-set"}
                               {:slug "other-card" :set-slug "other-set"}])
          deleted-cards (atom [])
          deleted-slugs (atom [])
          card-repo     (->MockCardRepo cards deleted-cards)
          set-repo      (->MockSetRepo deleted-slugs)
          service       (set-svc/create-set-service set-repo card-repo)]
      (set-svc/delete-set! service "test-set")
      (is (= 2 (count @deleted-cards)) "Should delete both cards in the set")
      (is (some #(= {:slug "card-1" :set-slug "test-set"} %) @deleted-cards))
      (is (some #(= {:slug "card-2" :set-slug "test-set"} %) @deleted-cards))
      (is (= ["test-set"] @deleted-slugs) "Should delete the set metadata"))))

(deftest delete-set-empty-set-test
  (testing "delete-set! works for sets with no cards"
    (let [cards         (atom [])
          deleted-cards (atom [])
          deleted-slugs (atom [])
          card-repo     (->MockCardRepo cards deleted-cards)
          set-repo      (->MockSetRepo deleted-slugs)
          service       (set-svc/create-set-service set-repo card-repo)]
      (set-svc/delete-set! service "empty-set")
      (is (empty? @deleted-cards) "Should not delete any cards")
      (is (= ["empty-set"] @deleted-slugs) "Should still delete the set metadata"))))

(deftest delete-set-only-deletes-cards-in-target-set-test
  (testing "delete-set! only deletes cards belonging to the specified set"
    (let [cards         (atom [{:slug "card-1" :set-slug "set-a"}
                               {:slug "card-2" :set-slug "set-b"}
                               {:slug "card-3" :set-slug "set-a"}])
          deleted-cards (atom [])
          deleted-slugs (atom [])
          card-repo     (->MockCardRepo cards deleted-cards)
          set-repo      (->MockSetRepo deleted-slugs)
          service       (set-svc/create-set-service set-repo card-repo)]
      (set-svc/delete-set! service "set-a")
      (is (= 2 (count @deleted-cards)) "Should only delete cards from set-a")
      (is (every? #(= "set-a" (:set-slug %)) @deleted-cards)))))
