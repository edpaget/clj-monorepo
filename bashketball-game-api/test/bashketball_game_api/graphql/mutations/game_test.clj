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
        (is (= "WAITING" (:status game)))
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
  (testing "joinGame allows player 2 to join a WAITING game"
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
        (is (= "ACTIVE" (:status result)))))))

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
  (testing "forfeitGame ends ACTIVE game and sets opponent as winner"
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
        (is (= "COMPLETED" (:status result)))))))

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
  (testing "leaveGame allows player 1 to leave a WAITING game"
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

(deftest leave-ACTIVE-game-test
  (testing "leaveGame returns false for ACTIVE game"
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

(deftest submit-set-ball-in-air-position-target-test
  (testing "submitAction with set-ball-in-air and position target works"
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
                         submitAction(gameId: $gameId, action: $action) { success error }
                       }"
                        :variables {:gameId (str (:id game))
                                    :action {:type "bashketball/set-ball-in-air"
                                             :origin [2 5]
                                             :target {:targetType "position" :position [2 13]}
                                             :actionType "shot"}}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:submitAction])]
        (is (true? (:success result)) (str "Expected success, got error: " (:error result)))))))

(deftest submit-set-ball-in-air-player-target-test
  (testing "submitAction with set-ball-in-air and player target works"
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
                         submitAction(gameId: $gameId, action: $action) { success error }
                       }"
                        :variables {:gameId (str (:id game))
                                    :action {:type "bashketball/set-ball-in-air"
                                             :origin [2 5]
                                             :target {:targetType "player" :playerId "HOME-1"}
                                             :actionType "pass"}}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:submitAction])]
        (is (true? (:success result)) (str "Expected success, got error: " (:error result)))))))

(deftest query-game-state-test
  (testing "game state can be queried via GraphQL without errors"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            response   (tu/graphql-request
                        "query GetGame($id: Uuid!) {
                         game(id: $id) {
                           id
                           status
                           gameState {
                             phase
                             turnNumber
                             activePlayer
                             score { HOME AWAY }
                             ball {
                               ... on BallLoose { position }
                               ... on BallPossessed { holderId }
                               ... on BallInAir {
                                origin
                                actionType
                                target {
                                  __typename
                                  ... on PositionTarget { position }
                                  ... on PlayerTarget { playerId }
                                }
                              }
                             }
                             players {
                               HOME {
                                 id
                                 actionsRemaining
                                 team {
                                   players
                                 }
                               }
                               AWAY {
                                 id
                                 actionsRemaining
                                 team {
                                   players
                                 }
                               }
                             }
                           }
                         }
                       }"
                        :variables {:id (str (:id game))}
                        :session-id session-id)
            errors     (tu/graphql-errors response)
            game-data  (get-in (tu/graphql-data response) [:game])]
        (is (empty? errors) (str "GraphQL errors: " (pr-str errors)))
        (is (some? (:gameState game-data)) "gameState should not be null")
        (is (some? (get-in game-data [:gameState :players :HOME :actionsRemaining]))
            "HOME.actionsRemaining should not be null")
        (is (some? (get-in game-data [:gameState :players :AWAY :actionsRemaining]))
            "AWAY.actionsRemaining should not be null")))))

(deftest submit-move-player-action-test
  (testing "move-player action sets player position correctly"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            joined     (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            ;; Get a player ID from the home team players map
            game-state (:game-state joined)
            player-id  (first (keys (get-in game-state [:players :team/HOME :team :players])))]

        (testing "player position is initially nil"
          (let [player (get-in game-state [:players :team/HOME :team :players (keyword player-id)])]
            (is (nil? (:position player)))))

        (testing "submit move-player action"
          (let [response (tu/graphql-request
                          "mutation SubmitAction($gameId: Uuid!, $action: ActionInput!) {
                           submitAction(gameId: $gameId, action: $action) {
                             success
                             error
                             gameId
                           }
                         }"
                          :variables {:gameId   (str (:id game))
                                      :action   {:type      "bashketball/move-player"
                                                 :playerId  player-id
                                                 :position  [2 3]}}
                          :session-id session-id)
                result   (get-in (tu/graphql-data response) [:submitAction])]
            (is (true? (:success result)) (str "Expected success but got error: " (:error result)))))

        (testing "player position is updated after move"
          (let [response  (tu/graphql-request
                           "query GetGame($id: Uuid!) {
                             game(id: $id) {
                               gameState {
                                 players {
                                   HOME {
                                     team {
                                       players
                                     }
                                   }
                                 }
                               }
                             }
                           }"
                           :variables {:id (str (:id game))}
                           :session-id session-id)
                errors    (tu/graphql-errors response)
                _         (is (empty? errors) (str "GraphQL errors: " (pr-str errors)))
                game-data (get-in (tu/graphql-data response) [:game :gameState])
                players   (get-in game-data [:players :HOME :team :players])
                player    (get players (keyword player-id))]
            (is (some? player) (str "Could not find player with id: " player-id))
            (is (= [2 3] (:position player))
                (str "Expected position [2 3] but got: " (:position player)))))))))

;; ---------------------------------------------------------------------------
;; Examine Cards Actions

(deftest submit-examine-cards-action-test
  (testing "examine-cards action moves cards to examined zone"
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
                         submitAction(gameId: $gameId, action: $action) { success error }
                       }"
                        :variables {:gameId (str (:id game))
                                    :action {:type   "bashketball/examine-cards"
                                             :player "home"
                                             :count  3}}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:submitAction])]
        (is (true? (:success result)) (str "Expected success, got error: " (:error result)))))))

(deftest submit-resolve-examined-cards-action-test
  (testing "resolve-examined-cards action places cards correctly"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            ;; First examine cards
            _          (tu/graphql-request
                        "mutation SubmitAction($gameId: Uuid!, $action: ActionInput!) {
                          submitAction(gameId: $gameId, action: $action) { success }
                        }"
                        :variables {:gameId (str (:id game))
                                    :action {:type   "bashketball/examine-cards"
                                             :player "home"
                                             :count  3}}
                        :session-id session-id)
            ;; Get the examined cards from the game state
            game-resp  (tu/graphql-request
                        "query GetGame($id: Uuid!) {
                           game(id: $id) {
                             gameState {
                               players {
                                 HOME { deck { examined { instanceId cardSlug } } }
                               }
                             }
                           }
                         }"
                        :variables {:id (str (:id game))}
                        :session-id session-id)
            examined   (get-in (tu/graphql-data game-resp)
                               [:game :gameState :players :HOME :deck :examined])
            placements (mapv (fn [card idx]
                               {:instanceId  (:instanceId card)
                                :destination (case idx
                                               0 "TOP"
                                               1 "BOTTOM"
                                               2 "DISCARD")})
                             examined (range))
            ;; Now resolve the examined cards
            response   (tu/graphql-request
                        "mutation SubmitAction($gameId: Uuid!, $action: ActionInput!) {
                          submitAction(gameId: $gameId, action: $action) { success error }
                        }"
                        :variables {:gameId (str (:id game))
                                    :action {:type       "bashketball/resolve-examined-cards"
                                             :player     "home"
                                             :placements placements}}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:submitAction])]
        (is (= 3 (count examined)) "Should have 3 examined cards")
        (is (true? (:success result)) (str "Expected success, got error: " (:error result)))))))
