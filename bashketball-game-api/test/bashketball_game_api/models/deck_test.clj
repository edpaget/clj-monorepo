(ns bashketball-game-api.models.deck-test
  "Tests for deck model and repository."
  (:require
   [bashketball-game-api.models.deck :as deck]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db create-test-user]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(deftest deck-repository-create-test
  (testing "Creating a new deck"
    (with-db
      (let [user      (create-test-user)
            deck-repo (deck/create-deck-repository)
            deck-data {:user-id (:id user)
                       :name "My Deck"
                       :card-slugs ["card-1" "card-2"]}
            created   (proto/create! deck-repo deck-data)]
        (is (some? created))
        (is (uuid? (:id created)))
        (is (= (:id user) (:user-id created)))
        (is (= "My Deck" (:name created)))
        (is (= ["card-1" "card-2"] (:card-slugs created)))
        (is (false? (:is-valid created)))
        (is (inst? (:created-at created)))
        (is (inst? (:updated-at created)))))))

(deftest deck-repository-create-with-validation-test
  (testing "Creating a deck with validation state"
    (with-db
      (let [user      (create-test-user)
            deck-repo (deck/create-deck-repository)
            deck-data {:user-id (:id user)
                       :name "Valid Deck"
                       :card-slugs ["player-1" "player-2"]
                       :is-valid true
                       :validation-errors nil}
            created   (proto/create! deck-repo deck-data)]
        (is (true? (:is-valid created)))
        (is (nil? (:validation-errors created)))))))

(deftest deck-repository-find-by-id-test
  (testing "Finding a deck by ID"
    (with-db
      (let [user      (create-test-user)
            deck-repo (deck/create-deck-repository)
            deck-data {:user-id (:id user)
                       :name "Test Deck"}
            created   (proto/create! deck-repo deck-data)
            found     (proto/find-by deck-repo {:id (:id created)})]
        (is (some? found))
        (is (= (:id created) (:id found)))
        (is (= "Test Deck" (:name found)))))))

(deftest deck-repository-find-by-user-test
  (testing "Finding decks by user ID"
    (with-db
      (let [user1       (create-test-user "user1")
            user2       (create-test-user "user2")
            deck-repo   (deck/create-deck-repository)
            _           (proto/create! deck-repo {:user-id (:id user1) :name "User1 Deck 1"})
            _           (proto/create! deck-repo {:user-id (:id user1) :name "User1 Deck 2"})
            _           (proto/create! deck-repo {:user-id (:id user2) :name "User2 Deck"})
            user1-decks (deck/find-by-user deck-repo (:id user1))]
        (is (= 2 (count user1-decks)))
        (is (every? #(= (:id user1) (:user-id %)) user1-decks))))))

(deftest deck-repository-find-valid-by-user-test
  (testing "Finding valid decks by user ID"
    (with-db
      (let [user        (create-test-user)
            deck-repo   (deck/create-deck-repository)
            _           (proto/create! deck-repo {:user-id (:id user) :name "Valid 1" :is-valid true})
            _           (proto/create! deck-repo {:user-id (:id user) :name "Invalid" :is-valid false})
            _           (proto/create! deck-repo {:user-id (:id user) :name "Valid 2" :is-valid true})
            valid-decks (deck/find-valid-by-user deck-repo (:id user))]
        (is (= 2 (count valid-decks)))
        (is (every? :is-valid valid-decks))))))

(deftest deck-repository-find-all-test
  (testing "Finding all decks"
    (with-db
      (let [user      (create-test-user)
            deck-repo (deck/create-deck-repository)
            _         (proto/create! deck-repo {:user-id (:id user) :name "Deck 1"})
            _         (proto/create! deck-repo {:user-id (:id user) :name "Deck 2"})
            _         (proto/create! deck-repo {:user-id (:id user) :name "Deck 3"})
            decks     (proto/find-all deck-repo {})]
        (is (= 3 (count decks)))))))

(deftest deck-repository-update-test
  (testing "Updating a deck"
    (with-db
      (let [user      (create-test-user)
            deck-repo (deck/create-deck-repository)
            created   (proto/create! deck-repo {:user-id (:id user) :name "Original"})
            updated   (proto/update! deck-repo (:id created) {:name "Updated"
                                                              :card-slugs ["new-card"]
                                                              :is-valid true})]
        (is (some? updated))
        (is (= "Updated" (:name updated)))
        (is (= ["new-card"] (:card-slugs updated)))
        (is (true? (:is-valid updated)))))))

(deftest deck-repository-update-validation-errors-test
  (testing "Updating a deck with validation errors"
    (with-db
      (let [user      (create-test-user)
            deck-repo (deck/create-deck-repository)
            created   (proto/create! deck-repo {:user-id (:id user) :name "Deck"})
            updated   (proto/update! deck-repo (:id created)
                                     {:is-valid false
                                      :validation-errors ["Not enough cards" "Missing player"]})]
        (is (false? (:is-valid updated)))
        (is (= ["Not enough cards" "Missing player"] (:validation-errors updated)))))))

(deftest deck-repository-delete-test
  (testing "Deleting a deck"
    (with-db
      (let [user      (create-test-user)
            deck-repo (deck/create-deck-repository)
            created   (proto/create! deck-repo {:user-id (:id user) :name "To Delete"})
            deleted?  (proto/delete! deck-repo (:id created))
            found     (proto/find-by deck-repo {:id (:id created)})]
        (is (true? deleted?))
        (is (nil? found))))))
