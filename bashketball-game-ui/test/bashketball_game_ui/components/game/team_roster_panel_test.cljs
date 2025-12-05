(ns bashketball-game-ui.components.game.team-roster-panel-test
  (:require
   [bashketball-game-ui.components.game.team-roster-panel :refer [team-roster-panel]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-players
  {"home-orc-0"   {:id "home-orc-0" :name "Orc Center" :card-slug "orc-center"
                   :stats {:shooting 8 :defense 6 :speed 4} :exhausted? false}
   "home-elf-1"   {:id "home-elf-1" :name "Elf Guard" :card-slug "elf-guard"
                   :stats {:shooting 6 :defense 4 :speed 8} :exhausted? false}
   "home-dwarf-2" {:id "home-dwarf-2" :name "Dwarf Forward" :card-slug "dwarf-forward"
                   :stats {:shooting 7 :defense 8 :speed 3} :exhausted? false}
   "home-goblin-3" {:id "home-goblin-3" :name "Goblin Runner" :card-slug "goblin-runner"
                    :stats {:shooting 4 :defense 3 :speed 9} :exhausted? false}})

(def sample-starters ["home-orc-0" "home-elf-1"])
(def sample-bench ["home-dwarf-2" "home-goblin-3"])
(def sample-indices {"home-orc-0" 1 "home-elf-1" 2 "home-dwarf-2" 3 "home-goblin-3" 4})

(t/deftest team-roster-panel-renders-team-label-test
  (uix-tlr/render ($ team-roster-panel {:team           :HOME
                                         :team-label     "HOME"
                                         :players        sample-players
                                         :starters       sample-starters
                                         :bench          sample-bench
                                         :player-indices sample-indices}))
  (t/is (some? (screen/get-by-text "HOME"))))

(t/deftest team-roster-panel-shows-on-court-section-test
  (uix-tlr/render ($ team-roster-panel {:team           :HOME
                                         :team-label     "HOME"
                                         :players        sample-players
                                         :starters       sample-starters
                                         :bench          sample-bench
                                         :player-indices sample-indices}))
  (t/is (some? (screen/get-by-text "On Court (2)"))))

(t/deftest team-roster-panel-shows-bench-section-test
  (uix-tlr/render ($ team-roster-panel {:team           :HOME
                                         :team-label     "HOME"
                                         :players        sample-players
                                         :starters       sample-starters
                                         :bench          sample-bench
                                         :player-indices sample-indices}))
  (t/is (some? (screen/get-by-text "Bench (2)"))))

(t/deftest team-roster-panel-renders-starter-players-test
  (uix-tlr/render ($ team-roster-panel {:team           :HOME
                                         :team-label     "HOME"
                                         :players        sample-players
                                         :starters       sample-starters
                                         :bench          sample-bench
                                         :player-indices sample-indices}))
  (t/is (some? (screen/get-by-text "Orc Center")))
  (t/is (some? (screen/get-by-text "Elf Guard"))))

(t/deftest team-roster-panel-renders-bench-players-test
  (uix-tlr/render ($ team-roster-panel {:team           :HOME
                                         :team-label     "HOME"
                                         :players        sample-players
                                         :starters       sample-starters
                                         :bench          sample-bench
                                         :player-indices sample-indices}))
  (t/is (some? (screen/get-by-text "Dwarf Forward")))
  (t/is (some? (screen/get-by-text "Goblin Runner"))))

(t/deftest team-roster-panel-empty-starters-shows-message-test
  (uix-tlr/render ($ team-roster-panel {:team           :HOME
                                         :team-label     "HOME"
                                         :players        sample-players
                                         :starters       []
                                         :bench          sample-bench
                                         :player-indices sample-indices}))
  (t/is (some? (screen/get-by-text "No players on court"))))

(t/deftest team-roster-panel-empty-bench-shows-message-test
  (uix-tlr/render ($ team-roster-panel {:team           :HOME
                                         :team-label     "HOME"
                                         :players        sample-players
                                         :starters       sample-starters
                                         :bench          []
                                         :player-indices sample-indices}))
  (t/is (some? (screen/get-by-text "No players on bench"))))
