(ns bashketball-game-api.models.game-utils-test
  "Tests for game-utils namespace."
  (:require
   [bashketball-game-api.models.game :as game]
   [bashketball-game-api.models.game-utils :as game-utils]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db
                                            create-test-user create-test-deck]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(deftest start-game-sets-player2-details-test
  (testing "start-game! sets player2 id and deck"
    (with-db
      (let [user1     (create-test-user "user1")
            user2     (create-test-user "user2")
            deck1     (create-test-deck (:id user1))
            deck2     (create-test-deck (:id user2))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user1)
                                                :player-1-deck-id (:id deck1)})
            started   (game-utils/start-game! game-repo (:id created)
                                              (:id user2) (:id deck2)
                                              {} (:id user1))]
        (is (= (:id user2) (:player-2-id started)))
        (is (= (:id deck2) (:player-2-deck-id started)))))))

(deftest start-game-sets-active-status-test
  (testing "start-game! transitions status to ACTIVE"
    (with-db
      (let [user1     (create-test-user "user1")
            user2     (create-test-user "user2")
            deck1     (create-test-deck (:id user1))
            deck2     (create-test-deck (:id user2))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user1)
                                                :player-1-deck-id (:id deck1)})
            _         (is (= :game-status/WAITING (:status created)))
            started   (game-utils/start-game! game-repo (:id created)
                                              (:id user2) (:id deck2)
                                              {} (:id user1))]
        (is (= :game-status/ACTIVE (:status started)))))))

(deftest start-game-sets-game-state-test
  (testing "start-game! sets initial game state"
    (with-db
      (let [user1      (create-test-user "user1")
            user2      (create-test-user "user2")
            deck1      (create-test-deck (:id user1))
            deck2      (create-test-deck (:id user2))
            game-repo  (game/create-game-repository)
            created    (proto/create! game-repo {:player-1-id (:id user1)
                                                 :player-1-deck-id (:id deck1)})
            game-state {:turn 1 :phase :tipoff :score {:home 0 :away 0}}
            started    (game-utils/start-game! game-repo (:id created)
                                               (:id user2) (:id deck2)
                                               game-state (:id user1))]
        (is (= {:turn 1 :phase "tipoff" :score {:home 0 :away 0}}
               (:game-state started)))))))

(deftest start-game-sets-current-player-test
  (testing "start-game! sets current player"
    (with-db
      (let [user1     (create-test-user "user1")
            user2     (create-test-user "user2")
            deck1     (create-test-deck (:id user1))
            deck2     (create-test-deck (:id user2))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user1)
                                                :player-1-deck-id (:id deck1)})
            started   (game-utils/start-game! game-repo (:id created)
                                              (:id user2) (:id deck2)
                                              {} (:id user2))]
        (is (= (:id user2) (:current-player-id started)))))))

(deftest start-game-sets-started-at-test
  (testing "start-game! timestamps the game start"
    (with-db
      (let [user1     (create-test-user "user1")
            user2     (create-test-user "user2")
            deck1     (create-test-deck (:id user1))
            deck2     (create-test-deck (:id user2))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user1)
                                                :player-1-deck-id (:id deck1)})
            _         (is (nil? (:started-at created)))
            started   (game-utils/start-game! game-repo (:id created)
                                              (:id user2) (:id deck2)
                                              {} (:id user1))]
        (is (inst? (:started-at started)))))))

(deftest end-game-completed-test
  (testing "end-game! transitions to COMPLETED status with winner"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            ended     (game-utils/end-game! game-repo (:id created)
                                            :game-status/COMPLETED (:id user))]
        (is (= :game-status/COMPLETED (:status ended)))
        (is (= (:id user) (:winner-id ended)))))))

(deftest end-game-abandoned-test
  (testing "end-game! transitions to ABANDONED status"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            ended     (game-utils/end-game! game-repo (:id created)
                                            :game-status/ABANDONED nil)]
        (is (= :game-status/ABANDONED (:status ended)))
        (is (nil? (:winner-id ended)))))))

(deftest end-game-sets-ended-at-test
  (testing "end-game! timestamps the game end"
    (with-db
      (let [user      (create-test-user)
            deck      (create-test-deck (:id user))
            game-repo (game/create-game-repository)
            created   (proto/create! game-repo {:player-1-id (:id user)
                                                :player-1-deck-id (:id deck)})
            _         (is (nil? (:ended-at created)))
            ended     (game-utils/end-game! game-repo (:id created)
                                            :game-status/COMPLETED (:id user))]
        (is (inst? (:ended-at ended)))))))
