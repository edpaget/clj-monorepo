(ns bashketball-game.polix.card-execution-test
  (:require [bashketball-game.effect-catalog :as catalog]
            [bashketball-game.polix.card-execution :as exec]
            [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(def play-card
  {:slug "fast-break"
   :name "Fast Break"
   :card-type :card-type/PLAY_CARD
   :fate 4
   :play
   {:play/id "fast-break"
    :play/name "Fast Break"
    :play/description "Refresh a player and grant an extra action"
    :play/targets [:target/player-id]
    :play/effect {:effect/type :bashketball/sequence
                  :effects [{:effect/type :bashketball/refresh-player
                             :player-id :target/player-id}
                            {:effect/type :bashketball/grant-action
                             :player-id :target/player-id}]}}})

(def coaching-card
  {:slug "quick-release"
   :name "Quick Release"
   :card-type :card-type/COACHING_CARD
   :fate 3
   :call
   {:call/id "quick-release-call"
    :call/name "Quick Release"
    :call/description "+2 SHT to target player until end of turn"
    :call/targets [:target/player-id]
    :call/effect {:effect/type :bashketball/add-modifier
                  :target :target/player-id
                  :stat :stat/SHOOTING
                  :amount 2
                  :duration :until-end-of-turn}}
   :signal
   {:signal/id "quick-release-signal"
    :signal/name "Quick Release Signal"
    :signal/description "+1 SHT to next Shot"
    :signal/trigger {:trigger/event :bashketball/card-discarded-as-fuel
                     :trigger/condition [:= :event/card-instance-id :self/instance-id]}
    :signal/effect {:effect/type :bashketball/add-modifier
                    :stat :stat/SHOOTING
                    :amount 1
                    :duration :next-skill-test}}})

(def coaching-card-no-signal
  {:slug "timeout"
   :name "Timeout"
   :card-type :card-type/COACHING_CARD
   :fate 2
   :call
   {:call/id "timeout-call"
    :call/name "Timeout"
    :call/effect {:effect/type :bashketball/refresh-all
                  :team :self/team}}})

(def another-coaching-card
  {:slug "zone-defense"
   :name "Zone Defense"
   :card-type :card-type/COACHING_CARD
   :fate 3
   :call
   {:call/id "zone-defense-call"
    :call/effect {:effect/type :bashketball/add-team-modifier}}
   :signal
   {:signal/id "zone-defense-signal"
    :signal/trigger {:trigger/event :bashketball/card-discarded-as-fuel}
    :signal/effect {:effect/type :bashketball/add-modifier
                    :stat :stat/DEFENSE
                    :amount 1}}})

(def standard-action
  {:slug "shoot-block"
   :name "Shoot / Block"
   :card-type :card-type/STANDARD_ACTION_CARD
   :fate 2
   :offense
   {:action/id "shoot"
    :action/name "Shoot"
    :action/description "Ball carrier attempts Shot"
    :action/requires [:bashketball/has-ball? :doc/state :actor/id]
    :action/targets [:actor/id]
    :action/effect {:effect/type :bashketball/initiate-skill-test
                    :test-type :shoot
                    :player-id :actor/id
                    :stat :stat/SHOOTING
                    :exhausts true}}
   :defense
   {:action/id "block"
    :action/name "Block"
    :action/description "Force opponent to shoot or exhaust"
    :action/requires [:bashketball/adjacent? :doc/state :actor/id :target/id]
    :action/targets [:actor/id :target/id]
    :action/effect {:effect/type :bashketball/force-choice
                    :target :target/id
                    :choices [:shoot :exhaust-skip]}}})

(def split-play
  {:slug "pick-and-roll"
   :name "Pick and Roll"
   :card-type :card-type/SPLIT_PLAY_CARD
   :fate 5
   :offense
   {:action/id "pick-offense"
    :action/name "Pick and Roll"
    :action/effect {:effect/type :bashketball/sequence
                    :effects [{:effect/type :bashketball/move-player}]}}
   :defense
   {:action/id "pick-defense"
    :action/name "Defensive Switch"
    :action/effect {:effect/type :bashketball/sequence
                    :effects [{:effect/type :bashketball/swap-defenders}]}}})

(def offense-only-action
  {:slug "alley-oop"
   :name "Alley Oop"
   :card-type :card-type/SPLIT_PLAY_CARD
   :fate 4
   :offense
   {:action/id "alley-oop"
    :action/name "Alley Oop"
    :action/effect {:effect/type :bashketball/alley-oop}}})

(def sample-cards [play-card coaching-card coaching-card-no-signal another-coaching-card
                   standard-action split-play offense-only-action])

(defn create-test-catalog []
  (catalog/create-catalog-from-seq sample-cards))

(def game-state
  {:turn-number 1
   :active-player :team/HOME
   :phase :phase/PLAY})

;; =============================================================================
;; Effect Resolution Helper Tests
;; =============================================================================

(deftest get-play-effect-test
  (let [cat (create-test-catalog)]
    (testing "returns effect for play card"
      (let [effect (exec/get-play-effect cat "fast-break")]
        (is (some? effect))
        (is (= :bashketball/sequence (:effect/type effect)))))

    (testing "returns nil for non-play cards"
      (is (nil? (exec/get-play-effect cat "quick-release"))))

    (testing "returns nil for unknown cards"
      (is (nil? (exec/get-play-effect cat "unknown-card"))))))

(deftest get-call-effect-test
  (let [cat (create-test-catalog)]
    (testing "returns effect for coaching card"
      (let [effect (exec/get-call-effect cat "quick-release")]
        (is (some? effect))
        (is (= :bashketball/add-modifier (:effect/type effect)))))

    (testing "returns nil for non-coaching cards"
      (is (nil? (exec/get-call-effect cat "fast-break"))))))

(deftest get-signal-test
  (let [cat (create-test-catalog)]
    (testing "returns signal for coaching card with signal"
      (let [signal (exec/get-signal cat "quick-release")]
        (is (some? signal))
        (is (= "quick-release-signal" (:signal/id signal)))))

    (testing "returns nil for coaching card without signal"
      (is (nil? (exec/get-signal cat "timeout"))))

    (testing "returns nil for non-coaching cards"
      (is (nil? (exec/get-signal cat "fast-break"))))))

;; =============================================================================
;; Context Building Tests
;; =============================================================================

(deftest build-effect-context-test
  (testing "builds context with team and card info"
    (let [card-instance {:instance-id "card-1" :card-slug "fast-break"}
          context       (exec/build-effect-context game-state card-instance :team/HOME {})]
      (is (= :team/HOME (:self/team context)))
      (is (= "card-1" (:card/instance-id context)))
      (is (= "fast-break" (:card/slug context)))))

  (testing "includes target bindings"
    (let [card-instance {:instance-id "card-1" :card-slug "fast-break"}
          targets       {:target/player-id "player-1"
                         :target/position [0 0]}
          context       (exec/build-effect-context game-state card-instance :team/HOME targets)]
      (is (= "player-1" (:target/player-id context)))
      (is (= [0 0] (:target/position context)))))

  (testing "includes self-id when provided"
    (let [card-instance {:instance-id "card-1" :card-slug "fast-break"}
          targets       {:self-id "player-2"}
          context       (exec/build-effect-context game-state card-instance :team/HOME targets)]
      (is (= "player-2" (:self/id context))))))

(deftest build-signal-context-test
  (testing "builds context for signal execution"
    (let [fuel-card    {:instance-id "fuel-1" :card-slug "quick-release"}
          main-card    {:instance-id "main-1" :card-slug "fast-break"}
          main-targets {:target/player-id "player-1"}
          context      (exec/build-signal-context game-state fuel-card main-card main-targets :team/HOME)]
      (is (= :team/HOME (:self/team context)))
      (is (= "fuel-1" (:card/instance-id context)))
      (is (= "fuel-1" (:event/card-instance-id context)))
      (is (= main-card (:event/main-card context)))
      (is (= main-targets (:event/main-card-targets context))))))

;; =============================================================================
;; Signal Collection Tests
;; =============================================================================

(deftest collect-signals-test
  (let [cat (create-test-catalog)]
    (testing "collects signals from coaching cards"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}]
            signals    (exec/collect-signals cat fuel-cards)]
        (is (= 1 (count signals)))
        (is (= "fuel-1" (get-in signals [0 :fuel-card :instance-id])))
        (is (= "quick-release-signal" (get-in signals [0 :signal :signal/id])))))

    (testing "ignores cards without signals"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "timeout"}]
            signals    (exec/collect-signals cat fuel-cards)]
        (is (empty? signals))))

    (testing "ignores non-coaching cards"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "fast-break"}]
            signals    (exec/collect-signals cat fuel-cards)]
        (is (empty? signals))))

    (testing "collects multiple signals"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}
                        {:instance-id "fuel-2" :card-slug "zone-defense"}]
            signals    (exec/collect-signals cat fuel-cards)]
        (is (= 2 (count signals)))))))

;; =============================================================================
;; Signal Ordering Tests
;; =============================================================================

(deftest needs-signal-ordering-test
  (testing "returns false for no signals"
    (is (not (exec/needs-signal-ordering? []))))

  (testing "returns false for single signal"
    (is (not (exec/needs-signal-ordering? [{:signal {}}]))))

  (testing "returns true for multiple signals"
    (is (exec/needs-signal-ordering? [{:signal {}} {:signal {}}]))))

(deftest create-signal-ordering-prompt-test
  (testing "creates prompt with signals and team"
    (let [signals [{:fuel-card {:instance-id "f1"}} {:fuel-card {:instance-id "f2"}}]
          prompt  (exec/create-signal-ordering-prompt signals :team/AWAY)]
      (is (= :signal-ordering (:prompt-type prompt)))
      (is (= 2 (count (:signals prompt))))
      (is (= :team/AWAY (:team prompt))))))

;; =============================================================================
;; Signal Resolution Tests
;; =============================================================================

(deftest resolve-signal-test
  (let [cat          (create-test-catalog)
        fuel-card    {:instance-id "fuel-1" :card-slug "quick-release"}
        main-card    {:instance-id "main-1" :card-slug "fast-break"}
        main-targets {:target/player-id "player-1"}
        signal-info  {:fuel-card fuel-card
                      :signal (exec/get-signal cat "quick-release")}]
    (testing "resolves signal with effect and context"
      (let [result (exec/resolve-signal game-state signal-info main-card main-targets :team/HOME)]
        (is (some? (:effect result)))
        (is (= :bashketball/add-modifier (get-in result [:effect :effect/type])))
        (is (= 1 (get-in result [:effect :amount])))
        (is (some? (:context result)))
        (is (= "quick-release-signal" (get-in result [:signal :signal/id])))))))

(deftest resolve-signals-in-order-test
  (let [cat          (create-test-catalog)
        fuel-cards   [{:instance-id "fuel-1" :card-slug "quick-release"}
                      {:instance-id "fuel-2" :card-slug "zone-defense"}]
        signals      (exec/collect-signals cat fuel-cards)
        main-card    {:instance-id "main-1" :card-slug "fast-break"}
        main-targets {:target/player-id "player-1"}]
    (testing "resolves signals in specified order"
      (let [results (exec/resolve-signals-in-order game-state signals main-card main-targets :team/HOME)]
        (is (= 2 (count results)))
        (is (= "fuel-1" (get-in results [0 :fuel-card :instance-id])))
        (is (= "fuel-2" (get-in results [1 :fuel-card :instance-id])))))

    (testing "respects reversed order"
      (let [reversed (vec (reverse signals))
            results  (exec/resolve-signals-in-order game-state reversed main-card main-targets :team/HOME)]
        (is (= "fuel-2" (get-in results [0 :fuel-card :instance-id])))
        (is (= "fuel-1" (get-in results [1 :fuel-card :instance-id])))))))

;; =============================================================================
;; Card Resolution Tests
;; =============================================================================

(deftest resolve-play-card-test
  (let [cat (create-test-catalog)]
    (testing "resolves play card effect"
      (let [card-instance {:instance-id "card-1" :card-slug "fast-break"}
            targets       {:target/player-id "player-1"}
            result        (exec/resolve-play-card cat game-state card-instance targets :team/HOME)]
        (is (some? result))
        (is (= :bashketball/sequence (get-in result [:effect :effect/type])))
        (is (= :play (:card-type result)))
        (is (some? (:context result)))))

    (testing "returns nil for non-play cards"
      (let [card-instance {:instance-id "card-1" :card-slug "quick-release"}
            result        (exec/resolve-play-card cat game-state card-instance {} :team/HOME)]
        (is (nil? result))))))

(deftest resolve-coaching-call-test
  (let [cat (create-test-catalog)]
    (testing "resolves coaching call effect"
      (let [card-instance {:instance-id "card-1" :card-slug "quick-release"}
            targets       {:target/player-id "player-1"}
            result        (exec/resolve-coaching-call cat game-state card-instance targets :team/AWAY)]
        (is (some? result))
        (is (= :bashketball/add-modifier (get-in result [:effect :effect/type])))
        (is (= :call (:card-type result)))
        (is (= :team/AWAY (get-in result [:context :self/team])))))

    (testing "returns nil for non-coaching cards"
      (let [card-instance {:instance-id "card-1" :card-slug "fast-break"}
            result        (exec/resolve-coaching-call cat game-state card-instance {} :team/HOME)]
        (is (nil? result))))))

;; =============================================================================
;; Complete Execution Flow Tests
;; =============================================================================

(deftest prepare-card-execution-test
  (let [cat (create-test-catalog)]
    (testing "prepares play card with no fuel signals"
      (let [main-card  {:instance-id "main-1" :card-slug "fast-break"}
            fuel-cards [{:instance-id "fuel-1" :card-slug "timeout"}]
            targets    {:target/player-id "player-1"}
            result     (exec/prepare-card-execution cat game-state main-card fuel-cards targets :team/HOME)]
        (is (empty? (:signals result)))
        (is (not (:needs-ordering? result)))
        (is (nil? (:ordering-prompt result)))
        (is (some? (:main-resolution result)))
        (is (= :play (get-in result [:main-resolution :card-type])))))

    (testing "prepares coaching call with fuel signal"
      (let [main-card  {:instance-id "main-1" :card-slug "timeout"}
            fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}]
            targets    {}
            result     (exec/prepare-card-execution cat game-state main-card fuel-cards targets :team/HOME)]
        (is (= 1 (count (:signals result))))
        (is (not (:needs-ordering? result)))
        (is (some? (:main-resolution result)))
        (is (= :call (get-in result [:main-resolution :card-type])))))

    (testing "prepares with multiple fuel signals needing ordering"
      (let [main-card  {:instance-id "main-1" :card-slug "fast-break"}
            fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}
                        {:instance-id "fuel-2" :card-slug "zone-defense"}]
            targets    {:target/player-id "player-1"}
            result     (exec/prepare-card-execution cat game-state main-card fuel-cards targets :team/HOME)]
        (is (= 2 (count (:signals result))))
        (is (:needs-ordering? result))
        (is (some? (:ordering-prompt result)))
        (is (= :signal-ordering (get-in result [:ordering-prompt :prompt-type])))))))

(deftest execute-without-ordering-test
  (let [cat (create-test-catalog)]
    (testing "executes play card with no signals"
      (let [main-card  {:instance-id "main-1" :card-slug "fast-break"}
            fuel-cards [{:instance-id "fuel-1" :card-slug "timeout"}]
            targets    {:target/player-id "player-1"}
            prep       (exec/prepare-card-execution cat game-state main-card fuel-cards targets :team/HOME)
            results    (exec/execute-without-ordering game-state prep :team/HOME)]
        (is (= 1 (count results)))
        (is (= :play (:card-type (first results))))))

    (testing "executes with single signal"
      (let [main-card  {:instance-id "main-1" :card-slug "fast-break"}
            fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}]
            targets    {:target/player-id "player-1"}
            prep       (exec/prepare-card-execution cat game-state main-card fuel-cards targets :team/HOME)
            results    (exec/execute-without-ordering game-state prep :team/HOME)]
        (is (= 2 (count results)))
        ;; First is signal
        (is (some? (:signal (first results))))
        ;; Second is main card
        (is (= :play (:card-type (second results))))))))

(deftest execute-with-signal-order-test
  (let [cat        (create-test-catalog)
        main-card  {:instance-id "main-1" :card-slug "fast-break"}
        fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}
                    {:instance-id "fuel-2" :card-slug "zone-defense"}]
        targets    {:target/player-id "player-1"}
        prep       (exec/prepare-card-execution cat game-state main-card fuel-cards targets :team/HOME)]
    (testing "executes signals in specified order"
      (let [ordered-signals (:signals prep)
            results         (exec/execute-with-signal-order game-state prep ordered-signals :team/HOME)]
        (is (= 3 (count results)))
        (is (= "fuel-1" (get-in results [0 :fuel-card :instance-id])))
        (is (= "fuel-2" (get-in results [1 :fuel-card :instance-id])))
        (is (= :play (:card-type (last results))))))

    (testing "respects custom order"
      (let [reversed-signals (vec (reverse (:signals prep)))
            results          (exec/execute-with-signal-order game-state prep reversed-signals :team/HOME)]
        (is (= "fuel-2" (get-in results [0 :fuel-card :instance-id])))
        (is (= "fuel-1" (get-in results [1 :fuel-card :instance-id])))))))

;; =============================================================================
;; Action Mode Resolution Tests
;; =============================================================================

(deftest get-offense-effect-test
  (let [cat (create-test-catalog)]
    (testing "returns offense effect for standard action"
      (let [effect (exec/get-offense-effect cat "shoot-block")]
        (is (some? effect))
        (is (= :bashketball/initiate-skill-test (:effect/type effect)))))

    (testing "returns offense effect for split play"
      (let [effect (exec/get-offense-effect cat "pick-and-roll")]
        (is (some? effect))
        (is (= :bashketball/sequence (:effect/type effect)))))

    (testing "returns nil for non-action cards"
      (is (nil? (exec/get-offense-effect cat "fast-break"))))))

(deftest get-defense-effect-test
  (let [cat (create-test-catalog)]
    (testing "returns defense effect for standard action"
      (let [effect (exec/get-defense-effect cat "shoot-block")]
        (is (some? effect))
        (is (= :bashketball/force-choice (:effect/type effect)))))

    (testing "returns nil for offense-only card"
      (is (nil? (exec/get-defense-effect cat "alley-oop"))))))

(deftest get-available-modes-test
  (let [cat (create-test-catalog)]
    (testing "returns both modes for standard action"
      (let [modes (exec/get-available-modes cat "shoot-block")]
        (is (some? modes))
        (is (:has-offense? modes))
        (is (:has-defense? modes))
        (is (= "shoot" (get-in modes [:offense :action/id])))
        (is (= "block" (get-in modes [:defense :action/id])))))

    (testing "returns offense-only for offense-only card"
      (let [modes (exec/get-available-modes cat "alley-oop")]
        (is (:has-offense? modes))
        (is (not (:has-defense? modes)))))

    (testing "returns nil for non-action cards"
      (is (nil? (exec/get-available-modes cat "fast-break"))))))

;; =============================================================================
;; Mode Choice Prompt Tests
;; =============================================================================

(deftest create-mode-choice-prompt-test
  (let [cat           (create-test-catalog)
        card-instance {:instance-id "card-1" :card-slug "shoot-block"}
        modes         (exec/get-available-modes cat "shoot-block")]
    (testing "creates prompt with card and modes"
      (let [prompt (exec/create-mode-choice-prompt card-instance modes :team/HOME)]
        (is (= :mode-choice (:prompt-type prompt)))
        (is (= "card-1" (get-in prompt [:card :instance-id])))
        (is (= :team/HOME (:team prompt)))
        (is (:has-offense? (:modes prompt)))
        (is (:has-defense? (:modes prompt)))))))

;; =============================================================================
;; Action Mode Resolution Tests
;; =============================================================================

(deftest resolve-action-mode-test
  (let [cat (create-test-catalog)]
    (testing "resolves offense mode"
      (let [card-instance {:instance-id "card-1" :card-slug "shoot-block"}
            targets       {:actor/id "player-1"}
            result        (exec/resolve-action-mode cat game-state card-instance :offense targets :team/HOME)]
        (is (some? result))
        (is (= :bashketball/initiate-skill-test (get-in result [:effect :effect/type])))
        (is (= :action (:card-type result)))
        (is (= :offense (:mode result)))))

    (testing "resolves defense mode"
      (let [card-instance {:instance-id "card-1" :card-slug "shoot-block"}
            targets       {:actor/id "player-1" :target/id "player-2"}
            result        (exec/resolve-action-mode cat game-state card-instance :defense targets :team/AWAY)]
        (is (some? result))
        (is (= :bashketball/force-choice (get-in result [:effect :effect/type])))
        (is (= :defense (:mode result)))
        (is (= :team/AWAY (get-in result [:context :self/team])))))

    (testing "returns nil for unavailable mode"
      (let [card-instance {:instance-id "card-1" :card-slug "alley-oop"}
            result        (exec/resolve-action-mode cat game-state card-instance :defense {} :team/HOME)]
        (is (nil? result))))))

;; =============================================================================
;; Standard Action Execution Tests
;; =============================================================================

(deftest prepare-action-execution-test
  (let [cat (create-test-catalog)]
    (testing "prepares action with mode choice"
      (let [card-instance {:instance-id "card-1" :card-slug "shoot-block"}
            fuel-cards    [{:instance-id "fuel-1" :card-slug "timeout"}]
            result        (exec/prepare-action-execution cat game-state card-instance fuel-cards :team/HOME)]
        (is (:needs-mode-choice? result))
        (is (some? (:mode-prompt result)))
        (is (= :mode-choice (get-in result [:mode-prompt :prompt-type])))
        (is (:has-offense? (:modes result)))
        (is (:has-defense? (:modes result)))))

    (testing "includes signals from fuel cards"
      (let [card-instance {:instance-id "card-1" :card-slug "shoot-block"}
            fuel-cards    [{:instance-id "fuel-1" :card-slug "quick-release"}]
            result        (exec/prepare-action-execution cat game-state card-instance fuel-cards :team/HOME)]
        (is (= 1 (count (:signals result))))))))

(deftest execute-action-with-mode-test
  (let [cat (create-test-catalog)]
    (testing "executes offense mode"
      (let [card-instance {:instance-id "card-1" :card-slug "shoot-block"}
            fuel-cards    [{:instance-id "fuel-1" :card-slug "timeout"}]
            targets       {:actor/id "player-1"}
            prep          (exec/prepare-action-execution cat game-state card-instance fuel-cards :team/HOME)
            results       (exec/execute-action-with-mode cat game-state prep :offense targets :team/HOME)]
        (is (= 1 (count results)))
        (is (= :action (:card-type (first results))))
        (is (= :offense (:mode (first results))))))

    (testing "executes defense mode with signals"
      (let [card-instance {:instance-id "card-1" :card-slug "shoot-block"}
            fuel-cards    [{:instance-id "fuel-1" :card-slug "quick-release"}]
            targets       {:actor/id "player-1" :target/id "player-2"}
            prep          (exec/prepare-action-execution cat game-state card-instance fuel-cards :team/HOME)
            results       (exec/execute-action-with-mode cat game-state prep :defense targets :team/HOME)]
        (is (= 2 (count results)))
        ;; First is signal
        (is (some? (:signal (first results))))
        ;; Second is action
        (is (= :action (:card-type (second results))))
        (is (= :defense (:mode (second results))))))))

;; =============================================================================
;; Split Play Execution Tests
;; =============================================================================

(deftest split-play-execution-test
  (let [cat (create-test-catalog)]
    (testing "executes split play offense mode"
      (let [card-instance {:instance-id "card-1" :card-slug "pick-and-roll"}
            fuel-cards    [{:instance-id "fuel-1" :card-slug "timeout"}]
            targets       {}
            prep          (exec/prepare-action-execution cat game-state card-instance fuel-cards :team/HOME)
            results       (exec/execute-action-with-mode cat game-state prep :offense targets :team/HOME)]
        (is (= 1 (count results)))
        (is (= :bashketball/sequence (get-in results [0 :effect :effect/type])))))

    (testing "executes split play defense mode"
      (let [card-instance {:instance-id "card-1" :card-slug "pick-and-roll"}
            fuel-cards    [{:instance-id "fuel-1" :card-slug "timeout"}]
            targets       {}
            prep          (exec/prepare-action-execution cat game-state card-instance fuel-cards :team/AWAY)
            results       (exec/execute-action-with-mode cat game-state prep :defense targets :team/AWAY)]
        (is (= 1 (count results)))
        (is (= :defense (:mode (first results))))))))

;; =============================================================================
;; Virtual Standard Action Tests
;; =============================================================================

(deftest create-virtual-card-instance-test
  (testing "creates virtual card with required fields"
    (let [virtual (exec/create-virtual-card-instance "shoot-block" "virtual-123")]
      (is (= "virtual-123" (:instance-id virtual)))
      (is (= "shoot-block" (:card-slug virtual)))
      (is (true? (:virtual virtual))))))

(deftest prepare-virtual-action-test
  (let [cat (create-test-catalog)]
    (testing "prepares virtual action"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "timeout"}
                        {:instance-id "fuel-2" :card-slug "timeout"}
                        {:instance-id "fuel-3" :card-slug "timeout"}]
            result     (exec/prepare-virtual-action cat game-state "shoot-block" "virtual-1" fuel-cards :team/HOME)]
        (is (:virtual result))
        (is (:needs-mode-choice? result))
        (is (true? (get-in result [:card :virtual])))
        (is (= "shoot-block" (get-in result [:card :card-slug])))))

    (testing "collects signals from virtual action fuel"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}
                        {:instance-id "fuel-2" :card-slug "zone-defense"}
                        {:instance-id "fuel-3" :card-slug "timeout"}]
            result     (exec/prepare-virtual-action cat game-state "shoot-block" "virtual-1" fuel-cards :team/HOME)]
        (is (= 2 (count (:signals result))))
        (is (:needs-ordering? result))))))

(deftest execute-virtual-action-test
  (let [cat (create-test-catalog)]
    (testing "executes virtual action"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "timeout"}
                        {:instance-id "fuel-2" :card-slug "timeout"}
                        {:instance-id "fuel-3" :card-slug "timeout"}]
            targets    {:actor/id "player-1"}
            prep       (exec/prepare-virtual-action cat game-state "shoot-block" "virtual-1" fuel-cards :team/HOME)
            results    (exec/execute-virtual-action cat game-state prep :offense targets :team/HOME)]
        (is (= 1 (count results)))
        (is (= :action (:card-type (first results))))
        (is (true? (get-in results [0 :card :virtual])))))

    (testing "executes virtual action with signals"
      (let [fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}
                        {:instance-id "fuel-2" :card-slug "timeout"}
                        {:instance-id "fuel-3" :card-slug "timeout"}]
            targets    {:actor/id "player-1"}
            prep       (exec/prepare-virtual-action cat game-state "shoot-block" "virtual-1" fuel-cards :team/HOME)
            results    (exec/execute-virtual-action cat game-state prep :offense targets :team/HOME)]
        (is (= 2 (count results)))
        (is (some? (:signal (first results))))
        (is (= :action (:card-type (second results))))))))
