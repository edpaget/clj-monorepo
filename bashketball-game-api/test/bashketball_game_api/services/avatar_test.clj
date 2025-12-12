(ns bashketball-game-api.services.avatar-test
  "Tests for avatar service."
  (:require
   [bashketball-game-api.models.avatar :as avatar-model]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.services.avatar :as avatar-svc]
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

(deftest store-avatar-test
  (testing "store-avatar! stores avatar data"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar-model/create-avatar-repository)
            result      (avatar-svc/store-avatar! avatar-repo
                                                  (:id user)
                                                  {:data         test-image-bytes
                                                   :content-type "image/png"
                                                   :etag         "test-etag"})]
        (is (some? result))
        (is (= "image/png" (:content-type result)))))))

(deftest get-avatar-test
  (testing "get-avatar retrieves stored avatar"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar-model/create-avatar-repository)
            _           (avatar-svc/store-avatar! avatar-repo
                                                  (:id user)
                                                  {:data         test-image-bytes
                                                   :content-type "image/jpeg"
                                                   :etag         "get-test"})
            result      (avatar-svc/get-avatar avatar-repo (:id user))]
        (is (some? result))
        (is (= "image/jpeg" (:content-type result)))
        (is (= "get-test" (:etag result)))))))

(deftest avatar-service-protocol-test
  (testing "AvatarService protocol implementation"
    (with-db
      (let [user           (create-test-user)
            avatar-repo    (avatar-model/create-avatar-repository)
            avatar-service (avatar-svc/create-avatar-service avatar-repo)]
        (is (nil? (avatar-svc/get-user-avatar avatar-service (:id user))))
        (avatar-svc/store-avatar! avatar-repo
                                  (:id user)
                                  {:data         test-image-bytes
                                   :content-type "image/png"
                                   :etag         "svc-test"})
        (let [result (avatar-svc/get-user-avatar avatar-service (:id user))]
          (is (some? result))
          (is (= "image/png" (:content-type result))))))))

(deftest fetch-image-invalid-url-test
  (testing "fetch-image returns nil for invalid URL"
    (let [result (avatar-svc/fetch-image "http://invalid.nonexistent.url/image.png")]
      (is (nil? result)))))

(deftest fetch-and-store-invalid-url-test
  (testing "fetch-and-store! returns nil for invalid URL"
    (with-db
      (let [user        (create-test-user)
            avatar-repo (avatar-model/create-avatar-repository)
            result      (avatar-svc/fetch-and-store! avatar-repo
                                                     (:id user)
                                                     "http://invalid.nonexistent.url/image.png")]
        (is (nil? result))))))
