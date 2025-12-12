(ns bashketball-game-api.models.avatar-test
  "Tests for avatar model and repository."
  (:require
   [bashketball-game-api.models.avatar :as avatar]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(defn- create-test-user []
  (with-db
    (let [user-repo (user/create-user-repository)]
      (proto/create! user-repo {:google-id "test-google-id"
                                :email "test@example.com"
                                :name "Test User"}))))

(def test-image-bytes
  (.getBytes "fake image data"))

(deftest avatar-repository-upsert-test
  (testing "Upserting an avatar for a user"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar/create-avatar-repository)
            avatar-data {:user-id      (:id user)
                         :data         test-image-bytes
                         :content-type "image/png"
                         :etag         "abc123"}
            result      (avatar/upsert! avatar-repo avatar-data)]
        (is (some? result))
        (is (= (:id user) (:user-id result)))
        (is (= "image/png" (:content-type result)))
        (is (= "abc123" (:etag result)))
        (is (inst? (:fetched-at result)))))))

(deftest avatar-repository-get-avatar-test
  (testing "Retrieving an avatar by user ID"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar/create-avatar-repository)
            _           (avatar/upsert! avatar-repo {:user-id      (:id user)
                                                     :data         test-image-bytes
                                                     :content-type "image/jpeg"
                                                     :etag         "xyz789"})
            result      (avatar/get-avatar avatar-repo (:id user))]
        (is (some? result))
        (is (= (:id user) (:user-id result)))
        (is (= "image/jpeg" (:content-type result)))
        (is (= "xyz789" (:etag result)))
        (is (java.util.Arrays/equals test-image-bytes (:data result)))))))

(deftest avatar-repository-get-nonexistent-test
  (testing "Retrieving a nonexistent avatar returns nil"
    (with-db
      (let [avatar-repo (avatar/create-avatar-repository)
            random-uuid (java.util.UUID/randomUUID)
            result      (avatar/get-avatar avatar-repo random-uuid)]
        (is (nil? result))))))

(deftest avatar-repository-upsert-updates-existing-test
  (testing "Upserting updates an existing avatar"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar/create-avatar-repository)
            _           (avatar/upsert! avatar-repo {:user-id      (:id user)
                                                     :data         test-image-bytes
                                                     :content-type "image/png"
                                                     :etag         "first"})
            new-bytes   (.getBytes "new image data")
            result      (avatar/upsert! avatar-repo {:user-id      (:id user)
                                                     :data         new-bytes
                                                     :content-type "image/gif"
                                                     :etag         "second"})]
        (is (= "image/gif" (:content-type result)))
        (is (= "second" (:etag result)))
        (is (java.util.Arrays/equals new-bytes (:data result)))))))

(deftest avatar-repository-delete-test
  (testing "Deleting an avatar"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar/create-avatar-repository)
            _           (avatar/upsert! avatar-repo {:user-id      (:id user)
                                                     :data         test-image-bytes
                                                     :content-type "image/png"})
            deleted?    (avatar/delete! avatar-repo (:id user))
            result      (avatar/get-avatar avatar-repo (:id user))]
        (is (true? deleted?))
        (is (nil? result))))))

(deftest avatar-exists-test
  (testing "avatar-exists? returns correct value"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar/create-avatar-repository)]
        (is (false? (avatar/avatar-exists? avatar-repo (:id user))))
        (avatar/upsert! avatar-repo {:user-id      (:id user)
                                     :data         test-image-bytes
                                     :content-type "image/png"})
        (is (true? (avatar/avatar-exists? avatar-repo (:id user))))))))
