(ns bashketball-editor-api.system-test
  "System integration tests.

  Tests that the system starts up correctly and basic functionality works."
  (:require
   [bashketball-editor-api.system :as system]
   [bashketball-editor-api.test-utils :as utils]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [db.core :as db]))

(use-fixtures :once utils/with-system)
(use-fixtures :each utils/with-clean-db)

(deftest system-starts
  (testing "System components are initialized"
    (is (some? utils/*system*))
    (is (some? (::system/config utils/*system*)))
    (is (some? (::system/db-pool utils/*system*)))
    (is (some? (::system/resolver-map utils/*system*)))))

(deftest database-connection
  (testing "Database is accessible"
    (utils/with-db
      (let [result (db/execute-one! ["SELECT 1 as value"])]
        (is (= 1 (:value result)))))))

(deftest user-creation
  (testing "Can create a test user"
    (let [user (utils/create-test-user "alice")]
      (is (some? user))
      (is (= "alice" (:github-login user)))
      (is (some? (:id user))))))
