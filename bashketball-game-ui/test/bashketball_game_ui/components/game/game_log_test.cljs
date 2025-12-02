(ns bashketball-game-ui.components.game.game-log-test
  (:require
   [bashketball-game-ui.components.game.game-log :refer [game-log]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-events
  [{:type :bashketball/move-player
    :timestamp "2024-01-01T10:00:00Z"
    :data {:player-id "player-1" :position [2 5]}}
   {:type :bashketball/advance-turn
    :timestamp "2024-01-01T10:01:00Z"
    :data {}}
   {:type :bashketball/add-score
    :timestamp "2024-01-01T10:02:00Z"
    :data {:team :home :points 2}}])

(t/deftest game-log-renders-header-test
  (uix-tlr/render ($ game-log {:events []}))
  (t/is (some? (screen/get-by-text "Game Log"))))

(t/deftest game-log-shows-empty-message-when-no-events-test
  (uix-tlr/render ($ game-log {:events []}))
  (t/is (some? (screen/get-by-text "No events yet"))))

(t/deftest game-log-renders-move-event-test
  (uix-tlr/render ($ game-log {:events [(first sample-events)]}))
  (t/is (some? (screen/get-by-text "Move"))))

(t/deftest game-log-renders-turn-event-test
  (uix-tlr/render ($ game-log {:events [(second sample-events)]}))
  (t/is (some? (screen/get-by-text "Turn")))
  (t/is (some? (screen/get-by-text "Turn ended"))))

(t/deftest game-log-renders-score-event-test
  (uix-tlr/render ($ game-log {:events [(nth sample-events 2)]}))
  (t/is (some? (screen/get-by-text "Score"))))
