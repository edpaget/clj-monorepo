(ns bashketball-game-api.services.starter-deck-test
  "Tests for starter deck service.

  Uses mock starter deck definitions to isolate tests from the actual
  starter-decks.edn configuration."
  (:require
   [bashketball-game-api.models.deck :as deck]
   [bashketball-game-api.services.deck :as deck-svc]
   [bashketball-game-api.services.starter-deck :as starter-deck-svc]
   [bashketball-game-api.test-fixtures.cards :as test-cards]
   [bashketball-game-api.test-fixtures.starter-decks :as test-starter-decks]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db
                                            create-test-user]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(defn- test-starter-deck-service
  "Creates a starter deck service with mock configuration."
  []
  (with-db
    (let [deck-repo (deck/create-deck-repository)]
      (starter-deck-svc/create-starter-deck-service
       deck-repo
       test-cards/mock-card-catalog
       deck-svc/default-validation-rules
       test-starter-decks/mock-starter-decks-config))))

(deftest get-starter-deck-definitions-test
  (testing "Returns all starter deck definitions from config"
    (let [service (test-starter-deck-service)
          defs    (starter-deck-svc/get-starter-deck-definitions service)]
      (is (= 3 (count defs)))
      (is (every? :id defs))
      (is (every? :name defs))
      (is (every? :card-slugs defs)))))

(deftest get-available-starter-decks-new-user-test
  (testing "New user has all starter decks available"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            avail   (starter-deck-svc/get-available-starter-decks service (:id user))]
        (is (= 3 (count avail)))))))

(deftest get-claimed-starter-decks-empty-test
  (testing "New user has no claimed starter decks"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            claimed (starter-deck-svc/get-claimed-starter-decks service (:id user))]
        (is (empty? claimed))))))

(deftest claim-starter-deck-success-test
  (testing "Claiming a starter deck creates deck and claim record"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            result  (starter-deck-svc/claim-starter-deck! service (:id user) "speed-demons")]
        (is (some? result))
        (is (= "speed-demons" (:starter-deck-id result)))
        (is (some? (:deck-id result)))
        (is (some? (:deck result)))
        (is (= "Speed Demons" (:name (:deck result))))))))

(deftest claim-starter-deck-creates-valid-deck-test
  (testing "Claimed deck passes validation"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            result  (starter-deck-svc/claim-starter-deck! service (:id user) "balanced-attack")]
        (is (true? (:is-valid (:deck result))))))))

(deftest claim-starter-deck-idempotent-test
  (testing "Second claim of same deck returns nil"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            first   (starter-deck-svc/claim-starter-deck! service (:id user) "speed-demons")
            second  (starter-deck-svc/claim-starter-deck! service (:id user) "speed-demons")]
        (is (some? first))
        (is (nil? second))))))

(deftest claim-different-decks-test
  (testing "User can claim different starter decks"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            first   (starter-deck-svc/claim-starter-deck! service (:id user) "speed-demons")
            second  (starter-deck-svc/claim-starter-deck! service (:id user) "post-dominance")]
        (is (some? first))
        (is (some? second))
        (is (= 2 (count (starter-deck-svc/get-claimed-starter-decks service (:id user)))))))))

(deftest get-available-after-claim-test
  (testing "Available decks excludes claimed ones"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            _       (starter-deck-svc/claim-starter-deck! service (:id user) "speed-demons")
            avail   (starter-deck-svc/get-available-starter-decks service (:id user))]
        (is (= 2 (count avail)))
        (is (not (some #(= :speed-demons (:id %)) avail)))))))

(deftest claim-invalid-starter-deck-test
  (testing "Claiming non-existent starter deck returns nil"
    (with-db
      (let [user    (create-test-user)
            service (test-starter-deck-service)
            result  (starter-deck-svc/claim-starter-deck! service (:id user) "nonexistent")]
        (is (nil? result))))))
