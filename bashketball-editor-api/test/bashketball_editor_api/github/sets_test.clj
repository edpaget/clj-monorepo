(ns bashketball-editor-api.github.sets-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bashketball-editor-api.github.sets :as sets]
   [malli.core :as m]))

(deftest card-set-schema-test
  (testing "validates a complete card set"
    (let [card-set {:id (random-uuid)
                    :name "Test Set"
                    :description "A test card set"
                    :created-at (java.util.Date.)
                    :updated-at (java.util.Date.)}]
      (is (m/validate sets/CardSet card-set))))

  (testing "validates a minimal card set"
    (let [card-set {:name "Test Set"}]
      (is (m/validate sets/CardSet card-set))))

  (testing "rejects card set without name"
    (let [card-set {:description "Missing name"}]
      (is (not (m/validate sets/CardSet card-set))))))

(deftest create-set-repository-test
  (testing "creates a SetRepository record"
    (let [mock-client {:type :mock}
          repo        (sets/create-set-repository mock-client)]
      (is (instance? bashketball_editor_api.github.sets.SetRepository repo))
      (is (= mock-client (:github-client repo))))))
