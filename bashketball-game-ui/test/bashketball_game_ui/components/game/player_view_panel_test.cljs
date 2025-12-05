(ns bashketball-game-ui.components.game.player-view-panel-test
  (:require
   [bashketball-game-ui.components.game.player-view-panel :refer [player-view-panel]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def home-players
  {"home-orc-0" {:id "home-orc-0" :name "Orc Center" :card-slug "orc-center"
                 :stats {:shooting 8 :defense 6 :speed 4} :exhausted? false}
   "home-elf-1" {:id "home-elf-1" :name "Elf Guard" :card-slug "elf-guard"
                 :stats {:shooting 6 :defense 4 :speed 8} :exhausted? false}})

(def away-players
  {"away-human-0" {:id "away-human-0" :name "Human Forward" :card-slug "human-forward"
                   :stats {:shooting 7 :defense 5 :speed 6} :exhausted? false}
   "away-dwarf-1" {:id "away-dwarf-1" :name "Dwarf Tank" :card-slug "dwarf-tank"
                   :stats {:shooting 5 :defense 9 :speed 3} :exhausted? false}})

(t/deftest player-view-panel-renders-both-teams-test
  (uix-tlr/render ($ player-view-panel {:home-players  home-players
                                         :away-players  away-players
                                         :home-starters ["home-orc-0"]
                                         :away-starters ["away-human-0"]
                                         :home-bench    ["home-elf-1"]
                                         :away-bench    ["away-dwarf-1"]}))
  (t/is (some? (screen/get-by-text "HOME")))
  (t/is (some? (screen/get-by-text "AWAY"))))

(t/deftest player-view-panel-renders-home-players-test
  (uix-tlr/render ($ player-view-panel {:home-players  home-players
                                         :away-players  away-players
                                         :home-starters ["home-orc-0"]
                                         :away-starters ["away-human-0"]
                                         :home-bench    ["home-elf-1"]
                                         :away-bench    ["away-dwarf-1"]}))
  (t/is (some? (screen/get-by-text "Orc Center")))
  (t/is (some? (screen/get-by-text "Elf Guard"))))

(t/deftest player-view-panel-renders-away-players-test
  (uix-tlr/render ($ player-view-panel {:home-players  home-players
                                         :away-players  away-players
                                         :home-starters ["home-orc-0"]
                                         :away-starters ["away-human-0"]
                                         :home-bench    ["home-elf-1"]
                                         :away-bench    ["away-dwarf-1"]}))
  (t/is (some? (screen/get-by-text "Human Forward")))
  (t/is (some? (screen/get-by-text "Dwarf Tank"))))

(t/deftest player-view-panel-empty-rosters-test
  (uix-tlr/render ($ player-view-panel {:home-players  {}
                                         :away-players  {}
                                         :home-starters []
                                         :away-starters []
                                         :home-bench    []
                                         :away-bench    []}))
  (t/is (some? (screen/get-by-text "HOME")))
  (t/is (some? (screen/get-by-text "AWAY"))))
