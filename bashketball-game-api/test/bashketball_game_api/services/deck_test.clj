(ns bashketball-game-api.services.deck-test
  "Tests for deck service."
  (:require
   [bashketball-game-api.services.deck :as deck-svc]
   [bashketball-game-api.system :as system]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db
                                            create-test-user create-test-deck
                                            *system*]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(defn- test-deck-service []
  (::system/deck-service *system*))

(defn- test-card-catalog []
  (::system/card-catalog *system*))

;; ---------------------------------------------------------------------------
;; Validation Tests

(deftest validate-deck-empty-test
  (testing "Empty deck fails validation"
    (let [card-catalog (test-card-catalog)
          deck         {:card-slugs []}
          result       (deck-svc/validate-deck card-catalog deck)]
      (is (false? (:is-valid result)))
      (is (some #(re-find #"player cards" %) (:validation-errors result)))
      (is (some #(re-find #"action cards" %) (:validation-errors result))))))

(deftest validate-deck-invalid-slugs-test
  (testing "Unknown card slugs cause validation errors"
    (let [card-catalog (test-card-catalog)
          deck         {:card-slugs ["nonexistent-card" "another-fake"]}
          result       (deck-svc/validate-deck card-catalog deck)]
      (is (false? (:is-valid result)))
      (is (some #(re-find #"Unknown cards" %) (:validation-errors result))))))

(deftest validate-deck-too-few-players-test
  (testing "Deck with too few players fails validation"
    (let [card-catalog (test-card-catalog)
          deck         {:card-slugs ["michael-jordan" "scottie-pippen"]}
          result       (deck-svc/validate-deck card-catalog deck)]
      (is (false? (:is-valid result)))
      (is (some #(re-find #"at least 3 player cards" %) (:validation-errors result))))))

(deftest validate-deck-too-many-players-test
  (testing "Deck with too many players fails validation"
    (let [card-catalog (test-card-catalog)
          deck         {:card-slugs ["michael-jordan" "shaq" "mugsy-bogues"
                                     "elf-point-guard" "dwarf-power-forward" "orc-center"]}
          result       (deck-svc/validate-deck card-catalog deck)]
      (is (false? (:is-valid result)))
      (is (some #(re-find #"Maximum 5 player cards" %) (:validation-errors result))))))

(deftest validate-deck-duplicate-cards-test
  (testing "Cards over copy limit cause validation errors"
    (let [card-catalog (test-card-catalog)
          deck         {:card-slugs (vec (repeat 10 "basic-shot"))}
          rules        (assoc deck-svc/default-validation-rules :max-copies-per-card 4)
          result       (deck-svc/validate-deck card-catalog deck rules)]
      (is (false? (:is-valid result)))
      (is (some #(re-find #"copy limit" %) (:validation-errors result))))))

;; ---------------------------------------------------------------------------
;; CRUD Tests

(deftest list-user-decks-test
  (testing "Lists decks for a user"
    (with-db
      (let [user         (create-test-user)
            _            (create-test-deck (:id user) "Deck 1")
            _            (create-test-deck (:id user) "Deck 2")
            deck-service (test-deck-service)
            decks        (deck-svc/list-user-decks deck-service (:id user))]
        (is (= 2 (count decks)))
        (is (every? #(= (:id user) (:user-id %)) decks))))))

(deftest list-user-decks-empty-test
  (testing "Returns empty list for user with no decks"
    (with-db
      (let [user         (create-test-user)
            deck-service (test-deck-service)
            decks        (deck-svc/list-user-decks deck-service (:id user))]
        (is (empty? decks))))))

(deftest get-deck-test
  (testing "Gets a deck by ID"
    (with-db
      (let [user         (create-test-user)
            created      (create-test-deck (:id user) "My Deck")
            deck-service (test-deck-service)
            deck         (deck-svc/get-deck deck-service (:id created))]
        (is (some? deck))
        (is (= "My Deck" (:name deck)))))))

(deftest get-deck-for-user-test
  (testing "Gets a deck only if owned by user"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck         (create-test-deck (:id user1) "User1 Deck")
            deck-service (test-deck-service)]
        (is (some? (deck-svc/get-deck-for-user deck-service (:id deck) (:id user1))))
        (is (nil? (deck-svc/get-deck-for-user deck-service (:id deck) (:id user2))))))))

(deftest create-deck-test
  (testing "Creates a new deck"
    (with-db
      (let [user         (create-test-user)
            deck-service (test-deck-service)
            deck         (deck-svc/create-deck! deck-service (:id user) "New Deck")]
        (is (some? (:id deck)))
        (is (= "New Deck" (:name deck)))
        (is (= [] (:card-slugs deck)))
        (is (false? (:is-valid deck)))))))

(deftest update-deck-name-test
  (testing "Updates deck name"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "Original")
            deck-service (test-deck-service)
            updated      (deck-svc/update-deck! deck-service (:id deck) (:id user)
                                                {:name "Updated"})]
        (is (= "Updated" (:name updated)))))))

(deftest update-deck-cards-test
  (testing "Updates deck cards and triggers validation"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "My Deck")
            deck-service (test-deck-service)
            updated      (deck-svc/update-deck! deck-service (:id deck) (:id user)
                                                {:card-slugs ["michael-jordan"]})]
        (is (= ["michael-jordan"] (:card-slugs updated)))
        (is (false? (:is-valid updated)))))))

(deftest update-deck-wrong-user-test
  (testing "Cannot update deck owned by another user"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck         (create-test-deck (:id user1) "User1 Deck")
            deck-service (test-deck-service)
            result       (deck-svc/update-deck! deck-service (:id deck) (:id user2)
                                                {:name "Hacked"})]
        (is (nil? result))))))

(deftest delete-deck-test
  (testing "Deletes a deck"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "To Delete")
            deck-service (test-deck-service)
            deleted?     (deck-svc/delete-deck! deck-service (:id deck) (:id user))]
        (is (true? deleted?))
        (is (nil? (deck-svc/get-deck deck-service (:id deck))))))))

(deftest delete-deck-wrong-user-test
  (testing "Cannot delete deck owned by another user"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck         (create-test-deck (:id user1) "User1 Deck")
            deck-service (test-deck-service)]
        (is (nil? (deck-svc/delete-deck! deck-service (:id deck) (:id user2))))
        (is (some? (deck-svc/get-deck deck-service (:id deck))))))))

(deftest validate-deck-service-test
  (testing "Validates deck and updates state"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "Test Deck" ["michael-jordan"])
            deck-service (test-deck-service)
            validated    (deck-svc/validate-deck! deck-service (:id deck) (:id user))]
        (is (false? (:is-valid validated)))
        (is (some? (:validation-errors validated)))))))

(deftest add-cards-to-deck-test
  (testing "Adds cards to a deck"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "Test Deck" ["basic-shot"])
            deck-service (test-deck-service)
            updated      (deck-svc/add-cards-to-deck! deck-service (:id deck) (:id user)
                                                      ["jump-shot" "layup"])]
        (is (= ["basic-shot" "jump-shot" "layup"] (:card-slugs updated)))))))

(deftest remove-cards-from-deck-test
  (testing "Removes cards from a deck"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "Test Deck"
                                           ["basic-shot" "jump-shot" "layup"])
            deck-service (test-deck-service)
            updated      (deck-svc/remove-cards-from-deck! deck-service (:id deck) (:id user)
                                                           ["jump-shot"])]
        (is (= ["basic-shot" "layup"] (:card-slugs updated)))))))
