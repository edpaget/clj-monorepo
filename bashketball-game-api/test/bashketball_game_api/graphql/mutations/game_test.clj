(ns bashketball-game-api.graphql.mutations.game-test
  "Tests for game GraphQL mutations.

  Tests createGame, joinGame, submitAction, forfeitGame, and leaveGame
  mutations via GraphQL."
  (:require
   [bashketball-game-api.services.game :as game-svc]
   [bashketball-game-api.system :as system]
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(defn- game-service []
  (::system/game-service tu/*system*))

;; ---------------------------------------------------------------------------
;; Create Game

(deftest create-game-test
  (testing "createGame creates a new game with valid deck"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-valid-test-deck (:id user))
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request
                        "mutation CreateGame($deckId: Uuid!) {
                         createGame(deckId: $deckId) { id status player1Id createdAt }
                       }"
                        :variables {:deckId (str (:id deck))}
                        :session-id session-id)
            game       (get-in (tu/graphql-data response) [:createGame])]
        (is (some? (:id game)))
        (is (= "waiting" (:status game)))
        (is (= (str (:id user)) (:player1Id game)))
        (is (some? (:createdAt game)))))))

(deftest create-game-invalid-deck-test
  (testing "createGame returns null with invalid deck"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-test-deck (:id user) "Empty Deck" [])
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request
                        "mutation CreateGame($deckId: Uuid!) {
                         createGame(deckId: $deckId) { id }
                       }"
                        :variables {:deckId (str (:id deck))}
                        :session-id session-id)]
        (is (nil? (get-in (tu/graphql-data response) [:createGame])))))))

(deftest create-game-unauthenticated-test
  (testing "createGame returns errors when not authenticated"
    (tu/with-db
      (let [user     (tu/create-test-user)
            deck     (tu/create-valid-test-deck (:id user))
            response (tu/graphql-request
                      "mutation CreateGame($deckId: Uuid!) {
                       createGame(deckId: $deckId) { id }
                     }"
                      :variables {:deckId (str (:id deck))})]
        (is (seq (tu/graphql-errors response)))))))

;; ---------------------------------------------------------------------------
;; Join Game

(deftest join-game-test
  (testing "joinGame allows player 2 to join a waiting game"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            session-id (tu/create-authenticated-session! (:id user2) :user user2)
            response   (tu/graphql-request
                        "mutation JoinGame($gameId: Uuid!, $deckId: Uuid!) {
                         joinGame(gameId: $gameId, deckId: $deckId) { id status }
                       }"
                        :variables {:gameId (str (:id game))
                                    :deckId (str (:id deck2))}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:joinGame])]
        (is (some? result))
        (is (= "active" (:status result)))))))

(deftest join-own-game-test
  (testing "joinGame returns null when trying to join own game"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-valid-test-deck (:id user))
            game       (game-svc/create-game! (game-service) (:id user) (:id deck))
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request
                        "mutation JoinGame($gameId: Uuid!, $deckId: Uuid!) {
                         joinGame(gameId: $gameId, deckId: $deckId) { id }
                       }"
                        :variables {:gameId (str (:id game))
                                    :deckId (str (:id deck))}
                        :session-id session-id)]
        (is (nil? (get-in (tu/graphql-data response) [:joinGame])))))))

(deftest join-game-unauthenticated-test
  (testing "joinGame returns errors when not authenticated"
    (tu/with-db
      (let [user     (tu/create-test-user)
            deck     (tu/create-valid-test-deck (:id user))
            game     (game-svc/create-game! (game-service) (:id user) (:id deck))
            response (tu/graphql-request
                      "mutation JoinGame($gameId: Uuid!, $deckId: Uuid!) {
                       joinGame(gameId: $gameId, deckId: $deckId) { id }
                     }"
                      :variables {:gameId (str (:id game))
                                  :deckId (str (:id deck))})]
        (is (seq (tu/graphql-errors response)))))))

;; ---------------------------------------------------------------------------
;; Forfeit Game

(deftest forfeit-game-test
  (testing "forfeitGame ends active game and sets opponent as winner"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            response   (tu/graphql-request
                        "mutation ForfeitGame($gameId: Uuid!) {
                         forfeitGame(gameId: $gameId) { id status }
                       }"
                        :variables {:gameId (str (:id game))}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:forfeitGame])]
        (is (some? result))
        (is (= "completed" (:status result)))))))

(deftest forfeit-game-unauthenticated-test
  (testing "forfeitGame returns errors when not authenticated"
    (tu/with-db
      (let [user1    (tu/create-test-user "user-1")
            user2    (tu/create-test-user "user-2")
            deck1    (tu/create-valid-test-deck (:id user1))
            deck2    (tu/create-valid-test-deck (:id user2))
            game     (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _        (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            response (tu/graphql-request
                      "mutation ForfeitGame($gameId: Uuid!) {
                       forfeitGame(gameId: $gameId) { id }
                     }"
                      :variables {:gameId (str (:id game))})]
        (is (seq (tu/graphql-errors response)))))))

;; ---------------------------------------------------------------------------
;; Leave Game

(deftest leave-game-test
  (testing "leaveGame allows player 1 to leave a waiting game"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-valid-test-deck (:id user))
            game       (game-svc/create-game! (game-service) (:id user) (:id deck))
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request
                        "mutation LeaveGame($gameId: Uuid!) { leaveGame(gameId: $gameId) }"
                        :variables {:gameId (str (:id game))}
                        :session-id session-id)]
        (is (true? (get-in (tu/graphql-data response) [:leaveGame])))))))

(deftest leave-active-game-test
  (testing "leaveGame returns false for active game"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            response   (tu/graphql-request
                        "mutation LeaveGame($gameId: Uuid!) { leaveGame(gameId: $gameId) }"
                        :variables {:gameId (str (:id game))}
                        :session-id session-id)]
        (is (false? (get-in (tu/graphql-data response) [:leaveGame])))))))

(deftest leave-game-unauthenticated-test
  (testing "leaveGame returns errors when not authenticated"
    (tu/with-db
      (let [user     (tu/create-test-user)
            deck     (tu/create-valid-test-deck (:id user))
            game     (game-svc/create-game! (game-service) (:id user) (:id deck))
            response (tu/graphql-request
                      "mutation LeaveGame($gameId: Uuid!) { leaveGame(gameId: $gameId) }"
                      :variables {:gameId (str (:id game))})]
        (is (seq (tu/graphql-errors response)))))))

;; ---------------------------------------------------------------------------
;; Submit Action

(deftest submit-action-test
  (testing "submitAction applies valid action to game"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            response   (tu/graphql-request
                        "mutation SubmitAction($gameId: Uuid!, $action: ActionInput!) {
                         submitAction(gameId: $gameId, action: $action) { success }
                       }"
                        :variables {:gameId (str (:id game))
                                    :action {:type "bashketball/set-phase" :phase "actions"}}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:submitAction])]
        (is (true? (:success result)))))))

(deftest submit-action-unauthenticated-test
  (testing "submitAction returns errors when not authenticated"
    (tu/with-db
      (let [user1    (tu/create-test-user "user-1")
            user2    (tu/create-test-user "user-2")
            deck1    (tu/create-valid-test-deck (:id user1))
            deck2    (tu/create-valid-test-deck (:id user2))
            game     (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _        (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            response (tu/graphql-request
                      "mutation SubmitAction($gameId: Uuid!, $action: ActionInput!) {
                       submitAction(gameId: $gameId, action: $action) { success }
                     }"
                      :variables {:gameId (str (:id game))
                                  :action {:type "bashketball/set-phase" :phase "actions"}})]
        (is (seq (tu/graphql-errors response)))))))
