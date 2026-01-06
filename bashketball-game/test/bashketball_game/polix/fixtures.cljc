(ns bashketball-game.polix.fixtures
  "Shared test fixtures for polix integration tests.

  Provides a minimal valid game state and helper functions for setting up
  test scenarios."
  (:require
   [bashketball-game.polix.effects :as effects]
   [bashketball-game.state :as state]
   [polix.effects.core :as fx]))

(def test-config
  "Minimal game configuration for testing."
  {:home {:deck ["card-1" "card-2" "card-3" "card-4" "card-5"]
          :players [{:card-slug "orc-center"
                     :name "Grukk"
                     :stats {:size :size/LG :speed 2 :shooting 2 :passing 1 :defense 4}}
                    {:card-slug "elf-point-guard"
                     :name "Lyria"
                     :stats {:size :size/SM :speed 5 :shooting 3 :passing 4 :defense 2}}
                    {:card-slug "dwarf-power-forward"
                     :name "Thorin"
                     :stats {:size :size/MD :speed 2 :shooting 3 :passing 2 :defense 4}}]}
   :away {:deck ["card-a" "card-b" "card-c" "card-d" "card-e"]
          :players [{:card-slug "troll-center"
                     :name "Grok"
                     :stats {:size :size/LG :speed 1 :shooting 1 :passing 1 :defense 5}}
                    {:card-slug "goblin-shooting-guard"
                     :name "Sneek"
                     :stats {:size :size/SM :speed 4 :shooting 4 :passing 3 :defense 1}}
                    {:card-slug "human-small-forward"
                     :name "John"
                     :stats {:size :size/MD :speed 3 :shooting 3 :passing 3 :defense 3}}]}})

(defn base-game-state
  "Creates a fresh game state for testing."
  []
  (effects/register-effects!)
  (state/create-game test-config))

(defn with-player-at
  "Places a player at the given position."
  [game-state player-id position]
  (:state (fx/apply-effect game-state
                           {:type :bashketball/move-player
                            :player-id player-id
                            :position position}
                           {} {})))

(defn with-exhausted
  "Marks a player as exhausted."
  [game-state player-id]
  (:state (fx/apply-effect game-state
                           {:type :bashketball/do-exhaust-player
                            :player-id player-id}
                           {} {})))

(defn with-ball-possessed
  "Sets the ball as possessed by a player."
  [game-state player-id]
  (:state (fx/apply-effect game-state
                           {:type :bashketball/do-set-ball-possessed
                            :holder-id player-id}
                           {} {})))

(defn with-ball-loose
  "Sets the ball as loose at a position."
  [game-state position]
  (:state (fx/apply-effect game-state
                           {:type :bashketball/do-set-ball-loose
                            :position position}
                           {} {})))

(defn with-drawn-cards
  "Draws cards for a team."
  [game-state team count]
  (:state (fx/apply-effect game-state
                           {:type :bashketball/do-draw-cards
                            :player team
                            :count count}
                           {} {})))

(def home-player-1 "HOME-orc-center-0")
(def home-player-2 "HOME-elf-point-guard-1")
(def home-player-3 "HOME-dwarf-power-forward-2")
(def away-player-1 "AWAY-troll-center-0")
(def away-player-2 "AWAY-goblin-shooting-guard-1")
(def away-player-3 "AWAY-human-small-forward-2")
