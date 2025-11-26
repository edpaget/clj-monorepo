(ns bashketball-editor-api.models.session-test
  (:require
   [authn.protocol :as proto]
   [bashketball-editor-api.models.session :as session]
   [bashketball-editor-api.test-utils :refer [with-system with-clean-db with-db create-test-user]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(deftest session-repository-create-test
  (testing "Creating a session returns a session-id"
    (with-db
      (let [user         (create-test-user)
            session-repo (session/create-session-repository 86400000)
            claims       {:name "Test User" :email "test@example.com"}
            session-id   (proto/create-session session-repo (str (:id user)) claims)]
        (is (string? session-id))
        (is (uuid? (parse-uuid session-id)))))))

(deftest session-repository-get-test
  (testing "Getting an existing session returns session data"
    (with-db
      (let [user         (create-test-user)
            user-id      (str (:id user))
            session-repo (session/create-session-repository 86400000)
            claims       {:name "Test User" :email "test@example.com"}
            session-id   (proto/create-session session-repo user-id claims)
            session-data (proto/get-session session-repo session-id)]
        (is (some? session-data))
        (is (= user-id (:user-id session-data)))
        (is (= claims (:claims session-data)))
        (is (number? (:created-at session-data)))
        (is (number? (:expires-at session-data)))
        (is (> (:expires-at session-data) (:created-at session-data))))))

  (testing "Getting a nonexistent session returns nil"
    (with-db
      (let [session-repo (session/create-session-repository 86400000)
            session-data (proto/get-session session-repo "nonexistent-session-id")]
        (is (nil? session-data))))))

(deftest session-repository-update-test
  (testing "Updating a session's expiration"
    (with-db
      (let [user           (create-test-user)
            session-repo   (session/create-session-repository 86400000)
            session-id     (proto/create-session session-repo (str (:id user)) {})
            original       (proto/get-session session-repo session-id)
            new-expires-at (+ (:expires-at original) 3600000)
            updated?       (proto/update-session session-repo session-id {:expires-at new-expires-at})
            updated        (proto/get-session session-repo session-id)]
        (is (true? updated?))
        (is (= new-expires-at (:expires-at updated))))))

  (testing "Updating a session's claims"
    (with-db
      (let [user         (create-test-user)
            session-repo (session/create-session-repository 86400000)
            session-id   (proto/create-session session-repo (str (:id user)) {:old "claims"})
            updated?     (proto/update-session session-repo session-id {:claims {:new "claims"}})
            updated      (proto/get-session session-repo session-id)]
        (is (true? updated?))
        (is (= {:new "claims"} (:claims updated)))))))

(deftest session-repository-delete-test
  (testing "Deleting a session"
    (with-db
      (let [user         (create-test-user)
            session-repo (session/create-session-repository 86400000)
            session-id   (proto/create-session session-repo (str (:id user)) {})
            deleted?     (proto/delete-session session-repo session-id)
            session-data (proto/get-session session-repo session-id)]
        (is (true? deleted?))
        (is (nil? session-data)))))

  (testing "Deleting a nonexistent session returns false"
    (with-db
      (let [session-repo (session/create-session-repository 86400000)
            deleted?     (proto/delete-session session-repo "nonexistent")]
        (is (false? deleted?))))))

(deftest session-repository-expired-session-test
  (testing "Expired sessions are not returned by get-session"
    (with-db
      (let [user         (create-test-user)
            session-repo (session/create-session-repository 1)
            session-id   (proto/create-session session-repo (str (:id user)) {})]
        (Thread/sleep 50)
        (is (nil? (proto/get-session session-repo session-id)))))))
