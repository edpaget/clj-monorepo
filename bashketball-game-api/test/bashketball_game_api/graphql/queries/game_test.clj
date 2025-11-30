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

(deftest my-games-unauthenticated-test
  (testing "myGames query returns errors when not authenticated"
    (let [response (tu/graphql-request "{ myGames { id status } }")]
      (is (seq (tu/graphql-errors response)))
      (is (nil? (get-in response [:data :myGames]))))))

(deftest my-games-empty-test
  (testing "myGames returns empty array for user with no games"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          response   (tu/graphql-request "{ myGames { id status } }"
                                         :session-id session-id)]
      (is (= [] (get-in (tu/graphql-data response) [:myGames]))))))

(deftest my-games-with-games-test
  (testing "myGames returns user's games"
    (tu/with-db
      (let [user       (tu/create-test-user)
            deck       (tu/create-valid-test-deck (:id user))
            _          (game-svc/create-game! (game-service) (:id user) (:id deck))
            _          (game-svc/create-game! (game-service) (:id user) (:id deck))
            session-id (tu/create-authenticated-session! (:id user) :user user)
            response   (tu/graphql-request "{ myGames { id status } }"
                                           :session-id session-id)
            games      (get-in (tu/graphql-data response) [:myGames])]
        (is (= 2 (count games)))
        (is (every? #(= "waiting" (:status %)) games))))))

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
            response   (tu/graphql-request "{ myGames { id player1Id } }"
                                           :session-id session-id)
            games      (get-in (tu/graphql-data response) [:myGames])]
        (is (= 1 (count games)))
        (is (= (str (:id user1)) (:player1Id (first games))))))))

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
