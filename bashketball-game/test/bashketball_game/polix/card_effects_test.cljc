(ns bashketball-game.polix.card-effects-test
  (:require [bashketball-game.actions :as actions]
            [bashketball-game.effect-catalog :as catalog]
            [bashketball-game.polix.card-effects :as card-effects]
            [bashketball-game.polix.fixtures :as fixtures]
            [bashketball-game.polix.triggers :as triggers]
            [bashketball-game.state :as state]
            [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Test Cards with Structured Abilities
;; =============================================================================

(def player-with-ability
  "Player card with a triggered ability."
  {:slug "orc-center"
   :name "Grukk"
   :card-type :card-type/PLAYER_CARD
   :abilities
   [{:ability/id "intimidate"
     :ability/name "Intimidate"
     :ability/trigger {:trigger/event :bashketball/skill-test.before
                       :trigger/condition [:= :event/defender-id :self/id]
                       :trigger/timing :before}
     :ability/effect {:effect/type :bashketball/add-modifier
                      :stat :stat/DEFENSE
                      :amount 1}}]})

(def player-without-ability
  "Player card with no abilities."
  {:slug "elf-point-guard"
   :name "Lyria"
   :card-type :card-type/PLAYER_CARD
   :abilities []})

(def player-with-two-abilities
  "Player card with multiple abilities."
  {:slug "dwarf-power-forward"
   :name "Thorin"
   :card-type :card-type/PLAYER_CARD
   :abilities
   [{:ability/id "sturdy"
     :ability/name "Sturdy"
     :ability/trigger {:trigger/event :bashketball/check.after
                       :trigger/timing :after}
     :ability/effect {:effect/type :bashketball/draw-cards
                      :count 1}}
    {:ability/id "anchor"
     :ability/name "Anchor"
     :ability/trigger {:trigger/event :bashketball/screen.before
                       :trigger/timing :before}
     :ability/effect {:effect/type :bashketball/add-modifier
                      :stat :stat/DEFENSE
                      :amount 2}}]})

(def ability-card
  "Ability card that can be attached to a player."
  {:slug "quick-release"
   :name "Quick Release"
   :card-type :card-type/ABILITY_CARD
   :abilities
   [{:ability/id "quick-release"
     :ability/name "Quick Release"
     :ability/trigger {:trigger/event :bashketball/shoot.after
                       :trigger/condition [:= :event/actor-id :self/id]
                       :trigger/timing :after}
     :ability/effect {:effect/type :bashketball/move-player
                      :player-id :self/id
                      :distance 1}}]})

(def test-catalog
  "Catalog with test cards."
  (catalog/create-catalog
   {"orc-center" player-with-ability
    "elf-point-guard" player-without-ability
    "dwarf-power-forward" player-with-two-abilities
    "troll-center" player-without-ability
    "goblin-shooting-guard" player-without-ability
    "human-small-forward" player-without-ability
    "quick-release" ability-card}))

;; =============================================================================
;; Test Helpers
;; =============================================================================

(defn- count-triggers [registry]
  (count (triggers/get-triggers registry)))

(defn- has-trigger-for-source? [registry source-id]
  (some #(= (:source %) source-id)
        (triggers/get-triggers registry)))

(defn- setup-game-with-players-on-court
  "Sets up a game with 3 players from each team on court."
  []
  (-> (fixtures/base-game-state)
      (fixtures/with-player-at fixtures/home-player-1 [1 3])
      (fixtures/with-player-at fixtures/home-player-2 [2 4])
      (fixtures/with-player-at fixtures/home-player-3 [3 5])
      (fixtures/with-player-at fixtures/away-player-1 [1 10])
      (fixtures/with-player-at fixtures/away-player-2 [2 9])
      (fixtures/with-player-at fixtures/away-player-3 [3 8])))

;; =============================================================================
;; Tests: Ability to Trigger Conversion
;; =============================================================================

(deftest register-player-abilities-test
  (testing "registers trigger for player with ability"
    (let [player   {:id "test-player" :card-slug "orc-center"}
          registry (card-effects/register-player-abilities
                    (triggers/create-registry)
                    test-catalog
                    player
                    :team/HOME)]
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry "test-player"))))

  (testing "registers no triggers for player without abilities"
    (let [player   {:id "test-player" :card-slug "elf-point-guard"}
          registry (card-effects/register-player-abilities
                    (triggers/create-registry)
                    test-catalog
                    player
                    :team/HOME)]
      (is (= 0 (count-triggers registry)))))

  (testing "registers multiple triggers for player with multiple abilities"
    (let [player   {:id "test-player" :card-slug "dwarf-power-forward"}
          registry (card-effects/register-player-abilities
                    (triggers/create-registry)
                    test-catalog
                    player
                    :team/HOME)]
      (is (= 2 (count-triggers registry))))))

(deftest unregister-player-abilities-test
  (testing "removes all triggers for a player"
    (let [player   {:id "test-player" :card-slug "dwarf-power-forward"}
          registry (-> (triggers/create-registry)
                       (card-effects/register-player-abilities test-catalog player :team/HOME))
          _        (is (= 2 (count-triggers registry)))
          updated  (card-effects/unregister-player-abilities registry "test-player")]
      (is (= 0 (count-triggers updated))))))

;; =============================================================================
;; Tests: Initialize Game Triggers
;; =============================================================================

(deftest initialize-game-triggers-test
  (testing "registers triggers for on-court players only"
    (let [game-state (setup-game-with-players-on-court)
          registry   (card-effects/initialize-game-triggers game-state test-catalog)]
      ;; orc-center has 1 ability, dwarf-power-forward has 2
      ;; All other players have 0 abilities in our test catalog
      (is (= 3 (count-triggers registry)))
      (is (has-trigger-for-source? registry fixtures/home-player-1))
      (is (has-trigger-for-source? registry fixtures/home-player-3))))

  (testing "registers no triggers for bench players"
    (let [game-state (fixtures/base-game-state) ;; No players on court
          registry   (card-effects/initialize-game-triggers game-state test-catalog)]
      (is (= 0 (count-triggers registry))))))

;; =============================================================================
;; Tests: Substitution
;; =============================================================================

(deftest handle-substitution-test
  (testing "player leaving court has triggers unregistered"
    (let [player   {:id "leaving" :card-slug "orc-center"}
          registry (-> (triggers/create-registry)
                       (card-effects/register-player-abilities test-catalog player :team/HOME))
          _        (is (= 1 (count-triggers registry)))
          updated  (card-effects/handle-player-leaving-court registry "leaving")]
      (is (= 0 (count-triggers updated)))))

  (testing "player entering court has triggers registered"
    (let [player   {:id "entering" :card-slug "orc-center"}
          registry (triggers/create-registry)
          updated  (card-effects/handle-player-entering-court
                    registry test-catalog player :team/HOME)]
      (is (= 1 (count-triggers updated)))
      (is (has-trigger-for-source? updated "entering")))))

;; =============================================================================
;; Tests: Ability Card Attachment
;; =============================================================================

(deftest register-attached-abilities-test
  (testing "registers triggers for attached ability card"
    (let [attachment {:instance-id "ability-123" :card-slug "quick-release"}
          registry   (card-effects/register-attached-abilities
                      (triggers/create-registry)
                      test-catalog
                      attachment
                      "target-player"
                      :team/HOME)]
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry "ability-123")))))

(deftest unregister-attached-abilities-test
  (testing "unregisters triggers when ability card detached"
    (let [attachment {:instance-id "ability-123" :card-slug "quick-release"}
          registry   (-> (triggers/create-registry)
                         (card-effects/register-attached-abilities
                          test-catalog attachment "target-player" :team/HOME))
          _          (is (= 1 (count-triggers registry)))
          updated    (card-effects/unregister-attached-abilities registry "ability-123")]
      (is (= 0 (count-triggers updated))))))

;; =============================================================================
;; Tests: Token Ability Cards
;; =============================================================================

(deftest token-ability-card-test
  (testing "registers triggers for token ability with inline definition"
    (let [token-card       {:slug "hot-hand"
                            :card-type :card-type/ABILITY_CARD
                            :abilities [{:ability/id "hot-hand"
                                         :ability/trigger {:trigger/event :bashketball/shoot.after}
                                         :ability/effect {:effect/type :bashketball/add-modifier}}]}
          token-attachment {:instance-id "token-456"
                            :token true
                            :card token-card}
          registry         (card-effects/register-attached-abilities
                            (triggers/create-registry)
                            test-catalog  ;; catalog not used for tokens
                            token-attachment
                            "target-player"
                            :team/HOME)]
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry "token-456")))))

;; =============================================================================
;; Tests: Update Registry for Action
;; =============================================================================

(deftest update-registry-for-substitute-action-test
  (testing "substitute updates registry correctly"
    (let [game-state  (-> (fixtures/base-game-state)
                          (fixtures/with-player-at fixtures/home-player-1 [1 3]))
          ;; Register trigger for player on court
          initial-reg (card-effects/initialize-game-triggers game-state test-catalog)
          _           (is (= 1 (count-triggers initial-reg)))  ;; orc-center has 1 ability
          ;; Substitute: orc-center leaves, elf-point-guard enters
          new-state   (actions/do-action game-state
                                         {:type :bashketball/substitute
                                          :on-court-id fixtures/home-player-1
                                          :off-court-id fixtures/home-player-2})
          action      {:type :bashketball/substitute
                       :on-court-id fixtures/home-player-1
                       :off-court-id fixtures/home-player-2}
          updated-reg (card-effects/update-registry-for-action
                       initial-reg test-catalog game-state new-state action)]
      ;; orc-center trigger removed, elf-point-guard has no abilities
      (is (= 0 (count-triggers updated-reg))))))

(deftest update-registry-for-attach-action-test
  (testing "attach-ability registers triggers"
    (let [game-state        (-> (fixtures/base-game-state)
                                (fixtures/with-player-at fixtures/home-player-1 [1 3])
                                (fixtures/with-drawn-cards :team/HOME 3))
          ;; Add ability card to hand
          game-with-ability (update-in game-state [:players :team/HOME :deck :hand]
                                       conj {:instance-id "ability-card-1"
                                             :card-slug "quick-release"})
          ;; Attach it
          new-state         (actions/do-action game-with-ability
                                               {:type :bashketball/attach-ability
                                                :player :team/HOME
                                                :instance-id "ability-card-1"
                                                :target-player-id fixtures/home-player-1})
          action            {:type :bashketball/attach-ability
                             :player :team/HOME
                             :instance-id "ability-card-1"
                             :target-player-id fixtures/home-player-1}
          registry          (card-effects/update-registry-for-action
                             (triggers/create-registry) test-catalog game-with-ability new-state action)]
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry "ability-card-1")))))

(deftest update-registry-for-detach-action-test
  (testing "detach-ability unregisters triggers"
    (let [game-state           (-> (fixtures/base-game-state)
                                   (fixtures/with-player-at fixtures/home-player-1 [1 3]))
          ;; Manually add attachment to player
          game-with-attachment (state/update-basketball-player
                                game-state fixtures/home-player-1
                                update :attachments conj
                                {:instance-id "ability-card-1"
                                 :card-slug "quick-release"
                                 :removable true
                                 :detach-destination :detach/DISCARD})
          ;; Register the ability's trigger
          initial-reg          (card-effects/register-attached-abilities
                                (triggers/create-registry)
                                test-catalog
                                {:instance-id "ability-card-1" :card-slug "quick-release"}
                                fixtures/home-player-1
                                :team/HOME)
          _                    (is (= 1 (count-triggers initial-reg)))
          ;; Detach it
          new-state            (actions/do-action game-with-attachment
                                                  {:type :bashketball/detach-ability
                                                   :player :team/HOME
                                                   :target-player-id fixtures/home-player-1
                                                   :instance-id "ability-card-1"})
          action               {:type :bashketball/detach-ability
                                :player :team/HOME
                                :target-player-id fixtures/home-player-1
                                :instance-id "ability-card-1"}
          updated-reg          (card-effects/update-registry-for-action
                                initial-reg test-catalog game-with-attachment new-state action)]
      (is (= 0 (count-triggers updated-reg))))))

;; =============================================================================
;; Tests: Integration with apply-action
;; =============================================================================

(deftest apply-action-updates-registry-test
  (testing "apply-action with catalog updates registry on substitute"
    (let [game-state (-> (fixtures/base-game-state)
                         (fixtures/with-player-at fixtures/home-player-1 [1 3]))
          registry   (card-effects/initialize-game-triggers game-state test-catalog)
          ctx        {:state game-state :registry registry :catalog test-catalog}
          result     (actions/apply-action ctx
                                           {:type :bashketball/substitute
                                            :on-court-id fixtures/home-player-1
                                            :off-court-id fixtures/home-player-2})]
      (is (not (:prevented? result)))
      ;; orc-center had 1 trigger, elf-point-guard has 0
      (is (= 0 (count-triggers (:registry result)))))))
