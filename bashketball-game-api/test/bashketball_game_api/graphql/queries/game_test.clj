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
        (is (every? #(= "waiting" (:status %)) games))
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
            _waiting   (game-svc/create-game! (game-service) (:id user1) (:id deck1))
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
        (is (= "active" (:status (first (:data result)))))
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
        (is (= "waiting" (:status result)))
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
  (testing "availableGames returns waiting games excluding user's own"
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
        (is (= "waiting" (:status (first games))))))))

(deftest available-games-unauthenticated-test
  (testing "availableGames returns errors when not authenticated"
    (let [response (tu/graphql-request "{ availableGames { id } }")]
      (is (seq (tu/graphql-errors response))))))
