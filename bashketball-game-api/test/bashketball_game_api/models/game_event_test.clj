(ns bashketball-game-api.models.game-event-test
  "Tests for game-event model and repository."
  (:require
   [bashketball-game-api.models.game :as game]
   [bashketball-game-api.models.game-event :as game-event]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db
                                            create-test-user create-test-deck]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(defn- create-test-game
  "Creates a test game for event tests."
  [user-id deck-id]
  (let [game-repo (game/create-game-repository)]
    (proto/create! game-repo {:player-1-id user-id
                              :player-1-deck-id deck-id})))

(deftest create-event-test
  (testing "Creating a game event with all fields"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            event      (proto/create! event-repo {:game-id (:id game-rec)
                                                  :player-id (:id user)
                                                  :event-type "move"
                                                  :event-data {:from [0 0] :to [1 1]}
                                                  :sequence-num 1})]
        (is (uuid? (:id event)))
        (is (= (:id game-rec) (:game-id event)))
        (is (= (:id user) (:player-id event)))
        (is (= "move" (:event-type event)))))))

(deftest create-event-without-player-test
  (testing "Creating a system event without player-id"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            event      (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "game-start"
                                                  :event-data {}
                                                  :sequence-num 1})]
        (is (some? event))
        (is (nil? (:player-id event)))
        (is (= "game-start" (:event-type event)))))))

(deftest create-event-keywordizes-data-test
  (testing "Event data is keywordized when retrieved"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            event      (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "action"
                                                  :event-data {:action-type "shoot" :target-id "p1"}
                                                  :sequence-num 1})]
        (is (= {:action-type "shoot" :target-id "p1"} (:event-data event)))
        (is (keyword? (-> event :event-data keys first)))))))

(deftest find-by-id-test
  (testing "Finding an event by ID"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            created    (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "test"
                                                  :event-data {}
                                                  :sequence-num 1})
            found      (proto/find-by event-repo {:id (:id created)})]
        (is (some? found))
        (is (= (:id created) (:id found)))))))

(deftest find-by-game-id-test
  (testing "Finding an event by game-id"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "test"
                                                  :event-data {}
                                                  :sequence-num 1})
            found      (proto/find-by event-repo {:game-id (:id game-rec)})]
        (is (some? found))
        (is (= (:id game-rec) (:game-id found)))))))

(deftest find-by-sequence-num-test
  (testing "Finding an event by sequence-num"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "first"
                                                  :event-data {}
                                                  :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "second"
                                                  :event-data {}
                                                  :sequence-num 2})
            found      (proto/find-by event-repo {:sequence-num 2})]
        (is (= "second" (:event-type found)))))))

(deftest find-all-by-game-scope-test
  (testing "Finding all events for a game via :by-game scope"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e1" :event-data {} :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e2" :event-data {} :sequence-num 2})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e3" :event-data {} :sequence-num 3})
            events     (proto/find-all event-repo {:scope :by-game :game-id (:id game-rec)})]
        (is (= 3 (count events)))
        (is (= ["e1" "e2" "e3"] (mapv :event-type events)))))))

(deftest find-all-since-scope-test
  (testing "Finding events after a sequence number via :since scope"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e1" :event-data {} :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e2" :event-data {} :sequence-num 2})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e3" :event-data {} :sequence-num 3})
            events     (proto/find-all event-repo {:scope :since :game-id (:id game-rec) :sequence-num 1})]
        (is (= 2 (count events)))
        (is (= [2 3] (mapv :sequence-num events)))))))

(deftest find-all-with-limit-test
  (testing "Finding events with limit option"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e1" :event-data {} :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e2" :event-data {} :sequence-num 2})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e3" :event-data {} :sequence-num 3})
            events     (proto/find-all event-repo {:scope :by-game :game-id (:id game-rec) :limit 2})]
        (is (= 2 (count events)))))))

(deftest find-all-with-offset-test
  (testing "Finding events with offset option"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e1" :event-data {} :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e2" :event-data {} :sequence-num 2})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e3" :event-data {} :sequence-num 3})
            events     (proto/find-all event-repo {:scope :by-game :game-id (:id game-rec) :offset 1})]
        (is (= 2 (count events)))
        (is (= [2 3] (mapv :sequence-num events)))))))

(deftest find-all-order-by-desc-test
  (testing "Finding events with descending order"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e1" :event-data {} :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e2" :event-data {} :sequence-num 2})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e3" :event-data {} :sequence-num 3})
            events     (proto/find-all event-repo {:scope :by-game
                                                   :game-id (:id game-rec)
                                                   :order-by [[:sequence-num :desc]]})]
        (is (= [3 2 1] (mapv :sequence-num events)))))))

(deftest update-event-type-test
  (testing "Updating event type"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            created    (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "original"
                                                  :event-data {}
                                                  :sequence-num 1})
            updated    (proto/update! event-repo (:id created) {:event-type "modified"})]
        (is (= "modified" (:event-type updated)))))))

(deftest update-event-data-test
  (testing "Updating event data"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            created    (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "test"
                                                  :event-data {:old "data"}
                                                  :sequence-num 1})
            updated    (proto/update! event-repo (:id created) {:event-data {:new "data"}})]
        (is (= {:new "data"} (:event-data updated)))))))

(deftest delete-event-test
  (testing "Deleting an event"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            created    (proto/create! event-repo {:game-id (:id game-rec)
                                                  :event-type "test"
                                                  :event-data {}
                                                  :sequence-num 1})
            deleted?   (proto/delete! event-repo (:id created))
            found      (proto/find-by event-repo {:id (:id created)})]
        (is (true? deleted?))
        (is (nil? found))))))

(deftest delete-nonexistent-event-test
  (testing "Deleting a nonexistent event returns false"
    (with-db
      (let [event-repo (game-event/create-game-event-repository)
            deleted?   (proto/delete! event-repo (random-uuid))]
        (is (false? deleted?))))))

(deftest next-sequence-num-empty-game-test
  (testing "Next sequence number for empty game is 1"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            next-num   (game-event/next-sequence-num event-repo (:id game-rec))]
        (is (= 1 next-num))))))

(deftest next-sequence-num-increments-test
  (testing "Next sequence number increments correctly"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game-rec   (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e1" :event-data {} :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game-rec) :event-type "e2" :event-data {} :sequence-num 2})
            next-num   (game-event/next-sequence-num event-repo (:id game-rec))]
        (is (= 3 next-num))))))

(deftest events-isolated-between-games-test
  (testing "Events from different games are isolated"
    (with-db
      (let [user       (create-test-user)
            deck       (create-test-deck (:id user))
            game1      (create-test-game (:id user) (:id deck))
            game2      (create-test-game (:id user) (:id deck))
            event-repo (game-event/create-game-event-repository)
            _          (proto/create! event-repo {:game-id (:id game1) :event-type "g1-e1" :event-data {} :sequence-num 1})
            _          (proto/create! event-repo {:game-id (:id game1) :event-type "g1-e2" :event-data {} :sequence-num 2})
            _          (proto/create! event-repo {:game-id (:id game2) :event-type "g2-e1" :event-data {} :sequence-num 1})
            game1-evts (proto/find-all event-repo {:scope :by-game :game-id (:id game1)})
            game2-evts (proto/find-all event-repo {:scope :by-game :game-id (:id game2)})]
        (is (= 2 (count game1-evts)))
        (is (= 1 (count game2-evts)))
        (is (every? #(= (:id game1) (:game-id %)) game1-evts))
        (is (every? #(= (:id game2) (:game-id %)) game2-evts))))))
