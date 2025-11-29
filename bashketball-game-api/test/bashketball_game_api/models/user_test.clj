(ns bashketball-game-api.models.user-test
  "Tests for user model and repository."
  (:require
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(deftest user-repository-create-test
  (testing "Creating a new user"
    (with-db
      (let [user-repo (user/create-user-repository)
            user-data {:google-id "google-123"
                       :email "test@example.com"
                       :avatar-url "https://example.com/avatar.png"
                       :name "Test User"}
            user      (proto/create! user-repo user-data)]
        (is (some? user))
        (is (uuid? (:id user)))
        (is (= "google-123" (:google-id user)))
        (is (= "test@example.com" (:email user)))
        (is (= "https://example.com/avatar.png" (:avatar-url user)))
        (is (= "Test User" (:name user)))
        (is (inst? (:created-at user)))
        (is (inst? (:updated-at user)))))))

(deftest user-repository-upsert-test
  (testing "Upserting an existing user updates the record"
    (with-db
      (let [user-repo   (user/create-user-repository)
            user-data-1 {:google-id "google-123"
                         :email "test@example.com"
                         :avatar-url "https://example.com/avatar.png"
                         :name "Test User"}
            user-1      (proto/create! user-repo user-data-1)
            user-data-2 {:google-id "google-123"
                         :email "newemail@example.com"
                         :avatar-url "https://example.com/new-avatar.png"
                         :name "Updated User"}
            user-2      (proto/create! user-repo user-data-2)]
        (is (= (:id user-1) (:id user-2)) "ID should remain the same")
        (is (= "google-123" (:google-id user-2)))
        (is (= "newemail@example.com" (:email user-2)))
        (is (= "https://example.com/new-avatar.png" (:avatar-url user-2)))
        (is (= "Updated User" (:name user-2)))
        (is (= (:created-at user-1) (:created-at user-2)) "Created-at should not change")
        (is (.after (:updated-at user-2) (:updated-at user-1)) "Updated-at should be newer")))))

(deftest user-repository-find-by-id-test
  (testing "Finding a user by ID"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:google-id "google-123"
                          :email "test@example.com"}
            created-user (proto/create! user-repo user-data)
            found-user   (proto/find-by user-repo {:id (:id created-user)})]
        (is (some? found-user))
        (is (= (:id created-user) (:id found-user)))
        (is (= "google-123" (:google-id found-user)))))))

(deftest user-repository-find-by-google-id-test
  (testing "Finding a user by Google ID"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:google-id "google-123"
                          :email "test@example.com"}
            created-user (proto/create! user-repo user-data)
            found-user   (user/find-by-google-id user-repo "google-123")]
        (is (some? found-user))
        (is (= (:id created-user) (:id found-user)))
        (is (= "google-123" (:google-id found-user)))))))

(deftest user-repository-find-by-nonexistent-test
  (testing "Finding a nonexistent user returns nil"
    (with-db
      (let [user-repo  (user/create-user-repository)
            found-user (proto/find-by user-repo {:google-id "nonexistent"})]
        (is (nil? found-user))))))

(deftest user-repository-find-all-test
  (testing "Finding all users"
    (with-db
      (let [user-repo (user/create-user-repository)
            _         (proto/create! user-repo {:google-id "user1" :email "user1@example.com"})
            _         (proto/create! user-repo {:google-id "user2" :email "user2@example.com"})
            _         (proto/create! user-repo {:google-id "user3" :email "user3@example.com"})
            users     (proto/find-all user-repo {})]
        (is (= 3 (count users)))
        (is (every? #(uuid? (:id %)) users))))))

(deftest user-repository-find-all-with-limit-test
  (testing "Finding all users with limit"
    (with-db
      (let [user-repo (user/create-user-repository)
            _         (proto/create! user-repo {:google-id "user1" :email "user1@example.com"})
            _         (proto/create! user-repo {:google-id "user2" :email "user2@example.com"})
            _         (proto/create! user-repo {:google-id "user3" :email "user3@example.com"})
            users     (proto/find-all user-repo {:limit 2})]
        (is (= 2 (count users)))))))

(deftest user-repository-update-test
  (testing "Updating a user"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:google-id "google-123"
                          :email "test@example.com"
                          :name "Test User"}
            created-user (proto/create! user-repo user-data)
            updated-user (proto/update! user-repo (:id created-user) {:name "Updated Name"})]
        (is (some? updated-user))
        (is (= (:id created-user) (:id updated-user)))
        (is (= "Updated Name" (:name updated-user)))
        (is (= "test@example.com" (:email updated-user)))))))

(deftest user-repository-delete-test
  (testing "Deleting a user"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:google-id "google-123"
                          :email "test@example.com"}
            created-user (proto/create! user-repo user-data)
            deleted?     (proto/delete! user-repo (:id created-user))
            found-user   (proto/find-by user-repo {:id (:id created-user)})]
        (is (true? deleted?))
        (is (nil? found-user))))))

(deftest upsert-from-google-test
  (testing "Upserting a user from Google OIDC claims"
    (with-db
      (let [user-repo (user/create-user-repository)
            claims    {:sub "google-456"
                       :email "google@example.com"
                       :name "Google User"
                       :picture "https://google.com/avatar.png"}
            user      (user/upsert-from-google! user-repo claims)]
        (is (some? user))
        (is (= "google-456" (:google-id user)))
        (is (= "google@example.com" (:email user)))
        (is (= "Google User" (:name user)))
        (is (= "https://google.com/avatar.png" (:avatar-url user)))))))
