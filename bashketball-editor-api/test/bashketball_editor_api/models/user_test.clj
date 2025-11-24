(ns bashketball-editor-api.models.user-test
  "Tests for user model and repository."
  (:require
   [bashketball-editor-api.models.protocol :as proto]
   [bashketball-editor-api.models.user :as user]
   [bashketball-editor-api.test-utils :refer [with-system with-clean-db with-db]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(deftest user-repository-create-test
  (testing "Creating a new user"
    (with-db
      (let [user-repo (user/create-user-repository)
            user-data {:github-login "octocat"
                       :email "octocat@github.com"
                       :avatar-url "https://github.com/octocat.png"
                       :name "The Octocat"}
            user      (proto/create! user-repo user-data)]
        (is (some? user))
        (is (uuid? (:id user)))
        (is (= "octocat" (:github-login user)))
        (is (= "octocat@github.com" (:email user)))
        (is (= "https://github.com/octocat.png" (:avatar-url user)))
        (is (= "The Octocat" (:name user)))
        (is (inst? (:created-at user)))
        (is (inst? (:updated-at user)))))))

(deftest user-repository-upsert-test
  (testing "Upserting an existing user updates the record"
    (with-db
      (let [user-repo   (user/create-user-repository)
            user-data-1 {:github-login "octocat"
                         :email "octocat@github.com"
                         :avatar-url "https://github.com/octocat.png"
                         :name "The Octocat"}
            user-1      (proto/create! user-repo user-data-1)
            user-data-2 {:github-login "octocat"
                         :email "newemail@github.com"
                         :avatar-url "https://github.com/octocat-new.png"
                         :name "Updated Octocat"}
            user-2      (proto/create! user-repo user-data-2)]
        (is (= (:id user-1) (:id user-2)) "ID should remain the same")
        (is (= "octocat" (:github-login user-2)))
        (is (= "newemail@github.com" (:email user-2)))
        (is (= "https://github.com/octocat-new.png" (:avatar-url user-2)))
        (is (= "Updated Octocat" (:name user-2)))
        (is (= (:created-at user-1) (:created-at user-2)) "Created-at should not change")
        (is (.after (:updated-at user-2) (:updated-at user-1)) "Updated-at should be newer")))))

(deftest user-repository-find-by-id-test
  (testing "Finding a user by ID"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:github-login "octocat"
                          :email "octocat@github.com"}
            created-user (proto/create! user-repo user-data)
            found-user   (proto/find-by user-repo {:id (:id created-user)})]
        (is (some? found-user))
        (is (= (:id created-user) (:id found-user)))
        (is (= "octocat" (:github-login found-user)))))))

(deftest user-repository-find-by-github-login-test
  (testing "Finding a user by GitHub login"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:github-login "octocat"
                          :email "octocat@github.com"}
            created-user (proto/create! user-repo user-data)
            found-user   (proto/find-by user-repo {:github-login "octocat"})]
        (is (some? found-user))
        (is (= (:id created-user) (:id found-user)))
        (is (= "octocat" (:github-login found-user)))))))

(deftest user-repository-find-by-nonexistent-test
  (testing "Finding a nonexistent user returns nil"
    (with-db
      (let [user-repo  (user/create-user-repository)
            found-user (proto/find-by user-repo {:github-login "nonexistent"})]
        (is (nil? found-user))))))

(deftest user-repository-find-all-test
  (testing "Finding all users"
    (with-db
      (let [user-repo (user/create-user-repository)
            _         (proto/create! user-repo {:github-login "user1" :email "user1@example.com"})
            _         (proto/create! user-repo {:github-login "user2" :email "user2@example.com"})
            _         (proto/create! user-repo {:github-login "user3" :email "user3@example.com"})
            users     (proto/find-all user-repo {})]
        (is (= 3 (count users)))
        (is (every? #(uuid? (:id %)) users))))))

(deftest user-repository-find-all-with-limit-test
  (testing "Finding all users with limit"
    (with-db
      (let [user-repo (user/create-user-repository)
            _         (proto/create! user-repo {:github-login "user1" :email "user1@example.com"})
            _         (proto/create! user-repo {:github-login "user2" :email "user2@example.com"})
            _         (proto/create! user-repo {:github-login "user3" :email "user3@example.com"})
            users     (proto/find-all user-repo {:limit 2})]
        (is (= 2 (count users)))))))

(deftest user-repository-update-test
  (testing "Updating a user"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:github-login "octocat"
                          :email "octocat@github.com"
                          :name "The Octocat"}
            created-user (proto/create! user-repo user-data)
            updated-user (proto/update! user-repo (:id created-user) {:name "Updated Name"})]
        (is (some? updated-user))
        (is (= (:id created-user) (:id updated-user)))
        (is (= "Updated Name" (:name updated-user)))
        (is (= "octocat@github.com" (:email updated-user)))))))

(deftest user-repository-delete-test
  (testing "Deleting a user"
    (with-db
      (let [user-repo    (user/create-user-repository)
            user-data    {:github-login "octocat"
                          :email "octocat@github.com"}
            created-user (proto/create! user-repo user-data)
            deleted?     (proto/delete! user-repo (:id created-user))
            found-user   (proto/find-by user-repo {:id (:id created-user)})]
        (is (true? deleted?))
        (is (nil? found-user))))))
