(ns bashketball-game-api.services.game-test
  "Tests for game service."
  (:require
   [bashketball-game-api.services.game :as game-svc]
   [bashketball-game-api.system :as system]
   [bashketball-game-api.test-utils :refer [*system* create-test-user
                                            create-valid-test-deck create-test-deck
                                            with-clean-db with-db with-system]]
   [clojure.core.async :as async]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [graphql-server.subscriptions :as subs]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(defn- test-game-service []
  (::system/game-service *system*))

(defn- test-subscription-manager []
  (::system/subscription-manager *system*))

;; ---------------------------------------------------------------------------
;; Game Creation Tests

(deftest create-game-with-valid-deck-test
  (testing "Creates game with valid deck"
    (with-db
      (let [user         (create-test-user)
            deck         (create-valid-test-deck (:id user))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user) (:id deck))]
        (is (some? (:id game)))
        (is (= (:id user) (:player-1-id game)))
        (is (= (:id deck) (:player-1-deck-id game)))
        (is (= :game-status/WAITING (:status game)))
        (is (nil? (:player-2-id game)))))))

(deftest create-game-with-invalid-deck-test
  (testing "Returns nil for invalid deck"
    (with-db
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "Empty Deck" [])
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user) (:id deck))]
        (is (nil? game))))))

(deftest create-game-with-wrong-user-deck-test
  (testing "Returns nil when deck belongs to different user"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck         (create-valid-test-deck (:id user1))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user2) (:id deck))]
        (is (nil? game))))))

(deftest create-game-publishes-to-lobby-test
  (testing "Creating game publishes to lobby topic"
    (with-db
      (let [user         (create-test-user)
            deck         (create-valid-test-deck (:id user))
            game-service (test-game-service)
            sub-mgr      (test-subscription-manager)
            lobby-ch     (subs/subscribe! sub-mgr [:lobby])
            _game        (game-svc/create-game! game-service (:id user) (:id deck))
            [msg _]      (async/alts!! [lobby-ch (async/timeout 1000)])]
        (is (= :game-created (:type msg)))
        (subs/unsubscribe! sub-mgr [:lobby] lobby-ch)))))

;; ---------------------------------------------------------------------------
;; Game Joining Tests

(deftest join-game-test
  (testing "Player 2 can join waiting game"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1) "Deck 1")
            deck2        (create-valid-test-deck (:id user2) "Deck 2")
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            joined       (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))]
        (is (some? joined))
        (is (= :game-status/ACTIVE (:status joined)))
        (is (= (:id user2) (:player-2-id joined)))
        (is (= (:id deck2) (:player-2-deck-id joined)))
        (is (some? (:game-state joined)))
        (is (some? (:started-at joined)))))))

(deftest join-game-initializes-state-test
  (testing "Joining game initializes game state correctly"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            joined       (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))
            state        (:game-state joined)]
        ;; DB layer uses Malli schema-driven decoding, so enum values
        ;; are proper namespaced keywords.
        (is (= :phase/SETUP (:phase state)))
        (is (= 1 (:turn-number state)))
        (is (= :team/HOME (:active-player state)))
        ;; Score keys are namespaced keywords
        (is (= {:team/HOME 0 :team/AWAY 0} (:score state)))
        ;; Player keys are namespaced keywords
        (is (some? (get-in state [:players :team/HOME])))
        (is (some? (get-in state [:players :team/AWAY])))))))

(deftest join-own-game-fails-test
  (testing "Cannot join your own game"
    (with-db
      (let [user         (create-test-user)
            deck         (create-valid-test-deck (:id user))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user) (:id deck))
            joined       (game-svc/join-game! game-service (:id game) (:id user) (:id deck))]
        (is (nil? joined))))))

(deftest join-active-game-fails-test
  (testing "Cannot join a game that's already active"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            user3        (create-test-user "user3")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            deck3        (create-valid-test-deck (:id user3))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            _            (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))
            join3        (game-svc/join-game! game-service (:id game) (:id user3) (:id deck3))]
        (is (nil? join3))))))

(deftest join-with-invalid-deck-fails-test
  (testing "Cannot join with invalid deck"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-test-deck (:id user2) "Invalid" [])
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            joined       (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))]
        (is (nil? joined))))))

;; ---------------------------------------------------------------------------
;; Game Query Tests

(deftest get-game-test
  (testing "Gets game by ID"
    (with-db
      (let [user         (create-test-user)
            deck         (create-valid-test-deck (:id user))
            game-service (test-game-service)
            created      (game-svc/create-game! game-service (:id user) (:id deck))
            fetched      (game-svc/get-game game-service (:id created))]
        (is (= (:id created) (:id fetched)))))))

(deftest get-game-for-player-test
  (testing "Gets game only for participants"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck         (create-valid-test-deck (:id user1))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck))]
        (is (some? (game-svc/get-game-for-player game-service (:id game) (:id user1))))
        (is (nil? (game-svc/get-game-for-player game-service (:id game) (:id user2))))))))

(deftest list-user-games-test
  (testing "Lists games for a user"
    (with-db
      (let [user         (create-test-user)
            deck         (create-valid-test-deck (:id user))
            game-service (test-game-service)
            _            (game-svc/create-game! game-service (:id user) (:id deck))
            _            (game-svc/create-game! game-service (:id user) (:id deck))
            games        (game-svc/list-user-games game-service (:id user))]
        (is (= 2 (count games)))))))

(deftest list-available-games-test
  (testing "Lists waiting games excluding user's own"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            game-service (test-game-service)
            _            (game-svc/create-game! game-service (:id user1) (:id deck1))
            _            (game-svc/create-game! game-service (:id user2) (:id deck2))
            available    (game-svc/list-available-games game-service (:id user1))]
        (is (= 1 (count available)))
        (is (= (:id user2) (:player-1-id (first available))))))))

;; ---------------------------------------------------------------------------
;; Action Submission Tests

(deftest submit-action-valid-test
  (testing "Valid action is applied"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            joined       (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))
            ;; Game engine uses namespaced enum values
            action       {:type :bashketball/set-phase :phase :phase/ACTIONS}
            result       (game-svc/submit-action! game-service (:id joined) (:id user1) action)]
        (is (:success result) (str "Expected success but got: " (:error result)))
        ;; DB uses Malli schema-driven decoding, so enum values are namespaced keywords
        (is (= :phase/ACTIONS (get-in result [:game :game-state :phase])))))))

(deftest submit-action-wrong-turn-test
  (testing "Action fails if not your turn"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            joined       (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))
            ;; Game engine uses namespaced enum values
            action       {:type :bashketball/set-phase :phase :phase/ACTIONS}
            result       (game-svc/submit-action! game-service (:id joined) (:id user2) action)]
        (is (false? (:success result)))
        (is (= "Not your turn" (:error result)))))))

(deftest submit-action-invalid-schema-test
  (testing "Invalid action schema fails"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            joined       (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))
            action       {:type :invalid/action}
            result       (game-svc/submit-action! game-service (:id joined) (:id user1) action)]
        (is (false? (:success result)))
        (is (some? (:error result)))))))

;; ---------------------------------------------------------------------------
;; Game Ending Tests

(deftest forfeit-game-test
  (testing "Forfeiting ends game and sets opponent as winner"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            _            (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))
            forfeited    (game-svc/forfeit-game! game-service (:id game) (:id user1))]
        (is (= :game-status/COMPLETED (:status forfeited)))
        (is (= (:id user2) (:winner-id forfeited)))))))

(deftest leave-waiting-game-test
  (testing "Player 1 can leave waiting game"
    (with-db
      (let [user         (create-test-user)
            deck         (create-valid-test-deck (:id user))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user) (:id deck))
            left?        (game-svc/leave-game! game-service (:id game) (:id user))]
        (is (true? left?))
        (is (nil? (game-svc/get-game game-service (:id game))))))))

(deftest leave-active-game-fails-test
  (testing "Cannot leave active game (must forfeit)"
    (with-db
      (let [user1        (create-test-user "user1")
            user2        (create-test-user "user2")
            deck1        (create-valid-test-deck (:id user1))
            deck2        (create-valid-test-deck (:id user2))
            game-service (test-game-service)
            game         (game-svc/create-game! game-service (:id user1) (:id deck1))
            _            (game-svc/join-game! game-service (:id game) (:id user2) (:id deck2))
            left?        (game-svc/leave-game! game-service (:id game) (:id user1))]
        (is (nil? left?))))))
