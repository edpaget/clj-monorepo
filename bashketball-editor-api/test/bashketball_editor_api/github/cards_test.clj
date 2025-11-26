(ns bashketball-editor-api.github.cards-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bashketball-editor-api.github.cards :as cards]
   [malli.core :as m]))

(deftest card-schema-test
  (testing "validates a complete card"
    (let [card {:id (random-uuid)
                :set-id (random-uuid)
                :name "Test Card"
                :description "A test card"
                :attributes {:power 10 :speed 5}
                :created-at (java.util.Date.)
                :updated-at (java.util.Date.)}]
      (is (m/validate cards/Card card))))

  (testing "validates a minimal card"
    (let [card {:set-id (random-uuid)
                :name "Test Card"}]
      (is (m/validate cards/Card card))))

  (testing "rejects card without set-id"
    (let [card {:name "Test Card"}]
      (is (not (m/validate cards/Card card)))))

  (testing "rejects card without name"
    (let [card {:set-id (random-uuid)}]
      (is (not (m/validate cards/Card card))))))

(deftest create-card-repository-test
  (testing "creates a CardRepository record"
    (let [mock-client {:type :mock}
          repo        (cards/create-card-repository mock-client)]
      (is (instance? bashketball_editor_api.github.cards.CardRepository repo))
      (is (= mock-client (:github-client repo))))))
