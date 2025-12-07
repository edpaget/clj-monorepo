(ns bashketball-game-api.graphql.queries.game-test
  "Tests for game GraphQL queries.

  Tests the `myGames`, `game`, and `availableGames` queries with authentication
  requirements and data isolation between users."
  (:require
   [bashketball-game-api.services.game :as game-svc]
   [bashketball-game-api.system :as system]
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(defn- game-service []
  (::system/game-service tu/*system*))

(def ^:private my-games-query
  "{ myGames { data { id status } pageInfo { totalCount hasNextPage hasPreviousPage } } }")

(deftest my-games-unauthenticated-test
  (testing "myGames query returns errors when not authenticated"
    (let [response (tu/graphql-request my-games-query)]
      (is (seq (tu/graphql-errors response)))
      (is (nil? (get-in response [:data :myGames]))))))

(deftest my-games-empty-test
  (testing "myGames returns empty data array for user with no games"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request my-games-query :session-id session-id)
          result     (get-in (tu/graphql-data response) [:myGames])]
      (is (= [] (:data result)))
      (is (= 0 (get-in result [:pageInfo :totalCount]))))))

(deftest my-games-with-games-test
  (testing "myGames returns user's games with pagination info"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-valid-test-deck (:id user))
            _          (game-svc/create-game! (game-service) (:id user) (:id deck))
            _          (game-svc/create-game! (game-service) (:id user) (:id deck))
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request my-games-query :session-id session-id)
            result     (get-in (tu/graphql-data response) [:myGames])
            games      (:data result)]
        (is (= 2 (count games)))
        (is (every? #(= "WAITING" (:status %)) games))
        (is (= 2 (get-in result [:pageInfo :totalCount])))
        (is (false? (get-in result [:pageInfo :hasNextPage])))
        (is (false? (get-in result [:pageInfo :hasPreviousPage])))))))

(deftest my-games-isolation-test
  (testing "myGames only returns games where user is a participant"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            _          (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/create-game! (game-service) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            response   (tu/graphql-request
                        "{ myGames { data { id player1Id } pageInfo { totalCount } } }"
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:myGames])
            games      (:data result)]
        (is (= 1 (count games)))
        (is (= (str (:id user1)) (:player1Id (first games))))
        (is (= 1 (get-in result [:pageInfo :totalCount])))))))

(deftest my-games-status-filter-test
  (testing "myGames filters by status enum"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            _WAITING   (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            game2      (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game2) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            response   (tu/graphql-request
                        "query($status: GameStatus) {
                         myGames(status: $status) { data { id status } pageInfo { totalCount } }
                       }"
                        :variables {:status "ACTIVE"}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:myGames])]
        (is (= 1 (count (:data result))))
        (is (= "ACTIVE" (:status (first (:data result)))))
        (is (= 1 (get-in result [:pageInfo :totalCount])))))))

(deftest my-games-pagination-test
  (testing "myGames supports limit and offset"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-valid-test-deck (:id user))
            _          (dotimes [_ 5] (game-svc/create-game! (game-service) (:id user) (:id deck)))
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request
                        "query($limit: Int, $offset: Int) {
                         myGames(limit: $limit, offset: $offset) {
                           data { id } pageInfo { totalCount hasNextPage hasPreviousPage }
                         }
                       }"
                        :variables {:limit 2 :offset 1}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:myGames])]
        (is (= 2 (count (:data result))))
        (is (= 5 (get-in result [:pageInfo :totalCount])))
        (is (true? (get-in result [:pageInfo :hasNextPage])))
        (is (true? (get-in result [:pageInfo :hasPreviousPage])))))))

(deftest game-query-test
  (testing "game query returns specific game by id"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-valid-test-deck (:id user))
            game       (game-svc/create-game! (game-service) (:id user) (:id deck))
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request
                        "query GetGame($id: Uuid!) { game(id: $id) { id status player1Id createdAt } }"
                        :variables {:id (str (:id game))}
                        :session-id session-id)
            result     (get-in (tu/graphql-data response) [:game])]
        (is (= (str (:id game)) (:id result)))
        (is (= "WAITING" (:status result)))
        (is (= (str (:id user)) (:player1Id result)))
        (is (some? (:createdAt result)))))))

(deftest game-query-unauthenticated-test
  (testing "game query returns errors when not authenticated"
    (tu/with-db
      (let [user     (tu/create-test-user)
            deck     (tu/create-valid-test-deck (:id user))
            game     (game-svc/create-game! (game-service) (:id user) (:id deck))
            response (tu/graphql-request
                      "query GetGame($id: Uuid!) { game(id: $id) { id } }"
                      :variables {:id (str (:id game))})]
        (is (seq (tu/graphql-errors response)))))))

(deftest game-query-not-participant-test
  (testing "game query returns null for game where user is not a participant"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck       (tu/create-valid-test-deck (:id user1))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck))
            session-id (tu/create-authenticated-session! (:id user2) :user user2)
            response   (tu/graphql-request
                        "query GetGame($id: Uuid!) { game(id: $id) { id } }"
                        :variables {:id (str (:id game))}
                        :session-id session-id)]
        (is (nil? (get-in (tu/graphql-data response) [:game])))))))

(deftest available-games-test
  (testing "availableGames returns WAITING games excluding user's own"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            _          (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/create-game! (game-service) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            response   (tu/graphql-request "{ availableGames { id player1Id status } }"
                                           :session-id session-id)
            games      (get-in (tu/graphql-data response) [:availableGames])]
        (is (= 1 (count games)))
        (is (= (str (:id user2)) (:player1Id (first games))))
        (is (= "WAITING" (:status (first games))))))))

(deftest available-games-unauthenticated-test
  (testing "availableGames returns errors when not authenticated"
    (let [response (tu/graphql-request "{ availableGames { id } }")]
      (is (seq (tu/graphql-errors response))))))

(deftest game-state-union-type-tagging-test
  (testing "game query returns gameState with correctly tagged Ball union type"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            query      "query GetGame($id: Uuid!) {
                          game(id: $id) {
                            id
                            status
                            gameState {
                              turnNumber
                              phase
                              ball {
                                __typename
                                ... on BallPossessed {
                                  holderId
                                }
                                ... on BallLoose {
                                  position
                                }
                                ... on BallInAir {
                                  origin
                                  target
                                }
                              }
                            }
                          }
                        }"
            response   (tu/graphql-request query
                                           :variables {:id (str (:id game))}
                                           :session-id session-id)
            result     (tu/graphql-data response)
            game-data  (:game result)]
        (is (= "ACTIVE" (:status game-data)))
        (is (some? (:gameState game-data)) "gameState should be present")
        (is (some? (get-in game-data [:gameState :ball])) "ball should be present")
        (is (contains? #{"BallPossessed" "BallLoose" "BallInAir"}
                       (get-in game-data [:gameState :ball :__typename]))
            "ball __typename should be one of the Ball union types")))))

(deftest game-state-deck-cards-hydration-test
  (testing "game query returns hydrated cards in deck"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            query      "query GetGame($id: Uuid!) {
                          game(id: $id) {
                            gameState {
                              players {
                                HOME {
                                  deck {
                                    drawPile { cardSlug }
                                    cards {
                                      __typename
                                      ... on PlayerCard {
                                        slug
                                        name
                                        speed
                                        size
                                      }
                                      ... on StandardActionCard {
                                        slug
                                        name
                                        fate
                                      }
                                      ... on PlayCard {
                                        slug
                                        name
                                        fate
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }"
            response   (tu/graphql-request query
                                           :variables {:id (str (:id game))}
                                           :session-id session-id)
            result     (tu/graphql-data response)
            deck       (get-in result [:game :gameState :players :HOME :deck])
            cards      (:cards deck)]
        (is (seq cards) "cards should be populated")
        (is (every? :slug cards) "each card should have a slug")
        (is (every? :name cards) "each card should have a name")
        (is (every? :__typename cards) "each card should have a __typename")))))

(deftest game-state-uppercase-team-keys-test
  (testing "game query returns gameState with uppercase team keys"
    (tu/with-db
      (let [user1      (tu/create-test-user "user-1")
            user2      (tu/create-test-user "user-2")
            deck1      (tu/create-valid-test-deck (:id user1))
            deck2      (tu/create-valid-test-deck (:id user2))
            game       (game-svc/create-game! (game-service) (:id user1) (:id deck1))
            _          (game-svc/join-game! (game-service) (:id game) (:id user2) (:id deck2))
            session-id (tu/create-authenticated-session! (:id user1) :user user1)
            query      "query GetGame($id: Uuid!) {
                          game(id: $id) {
                            id
                            status
                            gameState {
                              score {
                                HOME
                                AWAY
                              }
                              players {
                                HOME {
                                  id
                                  actionsRemaining
                                }
                                AWAY {
                                  id
                                  actionsRemaining
                                }
                              }
                            }
                          }
                        }"
            response   (tu/graphql-request query
                                           :variables {:id (str (:id game))}
                                           :session-id session-id)
            result     (tu/graphql-data response)
            game-data  (:game result)]
        (is (= "ACTIVE" (:status game-data)))
        (is (some? (:gameState game-data)) "gameState should be present")

        ;; GraphQL now returns uppercase keys using :graphql/name
        ;; Verify score map
        (let [score (get-in game-data [:gameState :score])]
          (is (contains? score :HOME) "score map should have :HOME key")
          (is (contains? score :AWAY) "score map should have :AWAY key")
          (is (= 0 (:HOME score)) "HOME score should be 0 initially")
          (is (= 0 (:AWAY score)) "AWAY score should be 0 initially"))

        ;; Verify players map
        (let [players (get-in game-data [:gameState :players])]
          (is (contains? players :HOME) "players map should have :HOME key")
          (is (contains? players :AWAY) "players map should have :AWAY key")
          (is (= "HOME" (get-in players [:HOME :id])) "HOME player id should be \"HOME\"")
          (is (= "AWAY" (get-in players [:AWAY :id])) "AWAY player id should be \"AWAY\""))))))
