(ns bashketball-editor-api.services.set-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bashketball-editor-api.services.set :as set-svc]))

(deftest create-set-service-test
  (testing "creates a SetService record"
    (let [mock-set-repo  {:type :set-repo}
          mock-card-repo {:type :card-repo}
          service        (set-svc/create-set-service mock-set-repo mock-card-repo)]
      (is (instance? bashketball_editor_api.services.set.SetService service))
      (is (= mock-set-repo (:set-repo service)))
      (is (= mock-card-repo (:card-repo service))))))
