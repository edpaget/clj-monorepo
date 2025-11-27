(ns bashketball-editor-api.services.card-test
  (:require
   [bashketball-editor-api.services.card :as card-svc]
   [clojure.test :refer [deftest is testing]]))

(deftest create-card-service-test
  (testing "creates a CardService record"
    (let [mock-repo {:type :mock}
          service   (card-svc/create-card-service mock-repo)]
      (is (instance? bashketball_editor_api.services.card.CardService service))
      (is (= mock-repo (:card-repo service))))))
