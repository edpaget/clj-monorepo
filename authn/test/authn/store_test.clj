(ns authn.store-test
  (:require
   [authn.protocol :as proto]
   [authn.store :as store]
   [clojure.test :refer [deftest is testing]]))

(deftest create-session-store-test
  (testing "creates in-memory session store"
    (let [store (store/create-session-store)]
      (is (satisfies? proto/SessionStore store)))))

(deftest session-lifecycle-test
  (let [store (store/create-session-store (* 1000 60))
        claims {:sub "user-123" :email "user@example.com"}]

    (testing "creates and retrieves session"
      (let [session-id (proto/create-session store "user-123" claims)
            session (proto/get-session store session-id)]
        (is (string? session-id))
        (is (= "user-123" (:user-id session)))
        (is (= claims (:claims session)))
        (is (number? (:created-at session)))
        (is (number? (:expires-at session)))))

    (testing "returns nil for non-existent session"
      (is (nil? (proto/get-session store "non-existent"))))

    (testing "updates existing session"
      (let [session-id (proto/create-session store "user-456" claims)
            updated? (proto/update-session store session-id {:extra-data "test"})
            session (proto/get-session store session-id)]
        (is (true? updated?))
        (is (= "test" (:extra-data session)))))

    (testing "cannot update non-existent session"
      (is (false? (proto/update-session store "non-existent" {:data "test"}))))

    (testing "deletes session"
      (let [session-id (proto/create-session store "user-789" claims)]
        (is (true? (proto/delete-session store session-id)))
        (is (nil? (proto/get-session store session-id)))))))

(deftest session-expiration-test
  (testing "expired sessions are not returned"
    (let [store (store/create-session-store 100)
          session-id (proto/create-session store "user-123" {:sub "user-123"})]
      (is (some? (proto/get-session store session-id)))
      (Thread/sleep 150)
      (is (nil? (proto/get-session store session-id))))))

(deftest cleanup-expired-test
  (testing "cleanup removes expired sessions"
    (let [store (store/create-session-store 100)]
      (proto/create-session store "user-1" {:sub "user-1"})
      (proto/create-session store "user-2" {:sub "user-2"})
      (Thread/sleep 150)
      (let [count (proto/cleanup-expired store)]
        (is (= 2 count))))))
