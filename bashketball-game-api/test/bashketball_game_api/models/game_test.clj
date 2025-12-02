(ns bashketball-game-api.models.game-test
  "Tests for game model and repository."
  (:require
   [bashketball-game-api.models.game :as game]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db
                                            create-test-user create-test-deck]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(deftest game-repository-create-test
  (testing "Creating a new game"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            game-data {:player-1-id (:id user)
                       :player-1-deck-id (:id deck)}
            created   (proto/create! game-repo game-data)]
        (is (some? created))
        (is (uuid? (:id created)))
        (is (= (:id user) (:player-1-id created)))
        (is (= (:id deck) (:player-1-deck-id created)))
        (is (= :game-status/WAITING (:status created)))
        (is (= {} (:game-state created)))
        (is (inst? (:created-at created)))))))

(deftest game-repository-find-by-id-test
  (testing "Finding a game by ID"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            found     (proto/find-by game-repo {:id (:id created)})]
        (is (some? found))
        (is (= (:id created) (:id found)))))))

(deftest game-repository-find-waiting-games-test
  (testing "Finding waiting games"
    (with-db
      (let [user1     (create-test-user "user1")
            user2     (create-test-user "user2")
            deck1     (create-test-deck (:id user1))
            deck2     (create-test-deck (:id user2))
            game-repo (game/create-game-repository)
            _         (proto/create! game-repo {:player-1-id (:id user1)
                                                :player-1-deck-id (:id deck1)})
            _         (proto/create! game-repo {:player-1-id (:id user2)
                                                :player-1-deck-id (:id deck2)})
            waiting   (game/find-waiting-games game-repo)]
        (is (= 2 (count waiting)))
        (is (every? #(= :game-status/WAITING (:status %)) waiting))))))

(deftest game-repository-find-by-player-test
  (testing "Finding games by player"
    (with-db
      (let [user1       (create-test-user "user1")
            user2       (create-test-user "user2")
            deck1       (create-test-deck (:id user1))
            deck2       (create-test-deck (:id user2))
            game-repo   (game/create-game-repository)
            game1       (proto/create! game-repo {:player-1-id (:id user1)
                                                  :player-1-deck-id (:id deck1)})
            _           (proto/update! game-repo (:id game1) {:player-2-id (:id user2)
                                                              :player-2-deck-id (:id deck2)})
            _           (proto/create! game-repo {:player-1-id (:id user2)
                                                  :player-1-deck-id (:id deck2)})
            user1-games (game/find-by-player game-repo (:id user1))]
        (is (= 1 (count user1-games)))))))

(deftest game-repository-update-test
  (testing "Updating a game"
    (with-db
      (let [user1     (create-test-user "user1")
            user2     (create-test-user "user2")
            deck1     (create-test-deck (:id user1))
            deck2     (create-test-deck (:id user2))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user1)
                                                :player-1-deck-id (:id deck1)})
            updated   (proto/update! game-repo (:id created)
                                     {:player-2-id (:id user2)
                                      :player-2-deck-id (:id deck2)
                                      :status :game-status/ACTIVE})]
        (is (some? updated))
        (is (= (:id user2) (:player-2-id updated)))
        (is (= (:id deck2) (:player-2-deck-id updated)))
        (is (= :game-status/ACTIVE (:status updated)))))))

(deftest game-repository-start-game-test
  (testing "Starting a game"
    (with-db
      (let [user1      (create-test-user "user1")
            user2      (create-test-user "user2")
            deck1      (create-test-deck (:id user1))
            deck2      (create-test-deck (:id user2))
            game-repo  (game/create-game-repository)
            created    (proto/create! game-repo {:player-1-id (:id user1)
                                                 :player-1-deck-id (:id deck1)})
            game-state {:turn 1 :phase :setup}
            started    (game/start-game! game-repo (:id created)
                                         (:id user2) (:id deck2)
                                         game-state (:id user1))]
        (is (some? started))
        (is (= :game-status/ACTIVE (:status started)))
        (is (= (:id user2) (:player-2-id started)))
        (is (= (:id deck2) (:player-2-deck-id started)))
        ;; Keywords are converted to strings when stored in JSONB
        (is (= {:turn 1 :phase "setup"} (:game-state started)))
        (is (= (:id user1) (:current-player-id started)))
        (is (inst? (:started-at started)))))))

(deftest game-repository-end-game-test
  (testing "Ending a game"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            ended     (game/end-game! game-repo (:id created) :game-status/COMPLETED (:id user))]
        (is (some? ended))
        (is (= :game-status/COMPLETED (:status ended)))
        (is (= (:id user) (:winner-id ended)))
        (is (inst? (:ended-at ended)))))))

(deftest game-repository-update-game-state-test
  (testing "Updating game state"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            new-state {:turn 5 :score {:home 10 :away 8}}
            updated   (game/update-game-state! game-repo (:id created) new-state)]
        (is (some? updated))
        (is (= new-state (:game-state updated)))))))

(deftest game-repository-delete-test
  (testing "Deleting a game"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            deleted?  (proto/delete! game-repo (:id created))
            found     (proto/find-by game-repo {:id (:id created)})]
        (is (true? deleted?))
        (is (nil? found))))))

(deftest game-events-create-test
  (testing "Creating a game event"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            game-rec  (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            event     (game/create-event! (:id game-rec) (:id user)
                                          "move" {:player-id "p1" :to [3 5]} 1)]
        (is (some? event))
        (is (uuid? (:id event)))
        (is (= (:id game-rec) (:game-id event)))
        (is (= (:id user) (:player-id event)))
        (is (= "move" (:event-type event)))
        (is (= {:player-id "p1" :to [3 5]} (:event-data event)))
        (is (= 1 (:sequence-num event)))))))

(deftest game-events-find-by-game-test
  (testing "Finding events by game"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            game-rec  (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            _         (game/create-event! (:id game-rec) nil "start" {} 1)
            _         (game/create-event! (:id game-rec) (:id user) "move" {} 2)
            _         (game/create-event! (:id game-rec) (:id user) "score" {} 3)
            events    (game/find-events-by-game (:id game-rec))]
        (is (= 3 (count events)))
        (is (= [1 2 3] (mapv :sequence-num events)))))))

(deftest game-events-find-since-test
  (testing "Finding events since a sequence number"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            game-rec  (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            _         (game/create-event! (:id game-rec) nil "start" {} 1)
            _         (game/create-event! (:id game-rec) nil "move" {} 2)
            _         (game/create-event! (:id game-rec) nil "score" {} 3)
            events    (game/find-events-since (:id game-rec) 1)]
        (is (= 2 (count events)))
        (is (= [2 3] (mapv :sequence-num events)))))))

(deftest game-events-next-sequence-num-test
  (testing "Getting next sequence number"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            game-rec  (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            next1     (game/get-next-sequence-num (:id game-rec))
            _         (game/create-event! (:id game-rec) nil "start" {} next1)
            next2     (game/get-next-sequence-num (:id game-rec))]
        (is (= 1 next1))
        (is (= 2 next2))))))

(deftest game-status-enum-create-test
  (testing "PostgreSQL game_status enum is properly handled on create"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            ;; Create game with default status (waiting)
            waiting   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            ;; Create game with explicit status
            active    (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)
                                                :status :game-status/ACTIVE})]
        (is (= :game-status/WAITING (:status waiting))
            "Default status should be :game-status/WAITING")
        (is (= :game-status/ACTIVE (:status active))
            "Explicit status should be stored correctly")))))

(deftest game-status-enum-filter-test
  (testing "Filtering by status enum works correctly"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            _         (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            _         (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)
                                                :status :game-status/ACTIVE})
            waiting   (game/find-by-player game-repo (:id user)
                                           {:status :game-status/WAITING})
            active    (game/find-by-player game-repo (:id user)
                                           {:status :game-status/ACTIVE})]
        (is (= 1 (count waiting)) "Should find 1 waiting game")
        (is (= 1 (count active)) "Should find 1 active game")
        (is (= :game-status/WAITING (:status (first waiting))))
        (is (= :game-status/ACTIVE (:status (first active))))))))

(deftest game-status-enum-transitions-test
  (testing "All game status transitions work"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user))
            game-repo    (game/create-game-repository)
            game-rec     (proto/create! game-repo {:player-1-id (:id user)
                                                   :player-1-deck-id (:id deck)})
            ;; Transition through all statuses
            to-active    (proto/update! game-repo (:id game-rec)
                                        {:status :game-status/ACTIVE})
            to-completed (proto/update! game-repo (:id game-rec)
                                        {:status :game-status/COMPLETED})
            to-abandoned (proto/update! game-repo (:id game-rec)
                                        {:status :game-status/ABANDONED})]
        (is (= :game-status/ACTIVE (:status to-active)))
        (is (= :game-status/COMPLETED (:status to-completed)))
        (is (= :game-status/ABANDONED (:status to-abandoned)))))))
