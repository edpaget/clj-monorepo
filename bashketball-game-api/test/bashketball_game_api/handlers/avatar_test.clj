(ns bashketball-game-api.handlers.avatar-test
  "Tests for avatar HTTP handler."
  (:require
   [bashketball-game-api.handlers.avatar :as avatar-handler]
   [bashketball-game-api.models.avatar :as avatar-model]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.services.avatar :as avatar-svc]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db]]
   [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
   [java.io ByteArrayOutputStream]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(defn- create-test-user []
  (with-db
    (let [user-repo (user/create-user-repository)]
      (proto/create! user-repo {:google-id "handler-test-user"
                                :email "handler@example.com"
                                :name "Handler Test User"}))))

(def test-image-bytes
  (.getBytes "test image content"))

(defn- read-body-bytes [body]
  (let [baos (ByteArrayOutputStream.)]
    (.transferTo body baos)
    (.toByteArray baos)))

(deftest avatar-handler-returns-avatar-test
  (testing "Handler returns avatar with correct headers"
    (with-db
      (let [user           (create-test-user)
            avatar-repo    (avatar-model/create-avatar-repository)
            avatar-service (avatar-svc/create-avatar-service avatar-repo)
            _              (avatar-svc/store-avatar! avatar-repo
                                                     (:id user)
                                                     {:data         test-image-bytes
                                                      :content-type "image/png"
                                                      :etag         "test-etag-123"})
            request        {:avatar-service avatar-service
                            :path-params    {:user-id (str (:id user))}
                            :headers        {}}
            response       (avatar-handler/avatar-handler request)]
        (is (= 200 (:status response)))
        (is (= "image/png" (get-in response [:headers "Content-Type"])))
        (is (= "test-etag-123" (get-in response [:headers "ETag"])))
        (is (= "public, max-age=86400" (get-in response [:headers "Cache-Control"])))
        (is (java.util.Arrays/equals test-image-bytes (read-body-bytes (:body response))))))))

(deftest avatar-handler-returns-304-on-etag-match-test
  (testing "Handler returns 304 when If-None-Match matches ETag"
    (with-db
      (let [user           (create-test-user)
            avatar-repo    (avatar-model/create-avatar-repository)
            avatar-service (avatar-svc/create-avatar-service avatar-repo)
            _              (avatar-svc/store-avatar! avatar-repo
                                                     (:id user)
                                                     {:data         test-image-bytes
                                                      :content-type "image/png"
                                                      :etag         "matching-etag"})
            request        {:avatar-service avatar-service
                            :path-params    {:user-id (str (:id user))}
                            :headers        {"if-none-match" "matching-etag"}}
            response       (avatar-handler/avatar-handler request)]
        (is (= 304 (:status response)))
        (is (= "matching-etag" (get-in response [:headers "ETag"])))))))

(deftest avatar-handler-returns-404-for-missing-avatar-test
  (testing "Handler returns 404 when avatar does not exist"
    (with-db
      (let [user           (create-test-user)
            avatar-repo    (avatar-model/create-avatar-repository)
            avatar-service (avatar-svc/create-avatar-service avatar-repo)
            request        {:avatar-service avatar-service
                            :path-params    {:user-id (str (:id user))}
                            :headers        {}}
            response       (avatar-handler/avatar-handler request)]
        (is (= 404 (:status response)))))))

(deftest avatar-handler-returns-400-for-invalid-uuid-test
  (testing "Handler returns 400 for invalid user ID"
    (with-db
      (let [avatar-repo    (avatar-model/create-avatar-repository)
            avatar-service (avatar-svc/create-avatar-service avatar-repo)
            request        {:avatar-service avatar-service
                            :path-params    {:user-id "not-a-valid-uuid"}
                            :headers        {}}
            response       (avatar-handler/avatar-handler request)]
        (is (= 400 (:status response)))))))

(deftest wrap-avatar-service-test
  (testing "wrap-avatar-service middleware attaches service to request"
    (let [avatar-repo    (avatar-model/create-avatar-repository)
          avatar-service (avatar-svc/create-avatar-service avatar-repo)
          handler        (fn [req] {:status 200 :body (:avatar-service req)})
          wrapped        (avatar-handler/wrap-avatar-service handler avatar-service)
          response       (wrapped {})]
      (is (= 200 (:status response)))
      (is (= avatar-service (:body response))))))
