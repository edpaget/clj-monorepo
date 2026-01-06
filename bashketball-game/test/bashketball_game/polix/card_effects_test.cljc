(ns bashketball-game.polix.card-effects-test
  (:require [bashketball-game.effect-catalog :as catalog]
            [bashketball-game.polix.card-effects :as card-effects]
            [bashketball-game.polix.core :as polix]
            [bashketball-game.polix.fixtures :as fixtures]
            [bashketball-game.polix.triggers :as triggers]
            [bashketball-game.state :as state]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [f]
    (polix/initialize!)
    (f)))

(defn- apply-effect
  "Helper to apply effect and return just the state."
  [game-state effect]
  (:state (fx/apply-effect game-state effect {} {})))

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

;; =============================================================================
;; Test Assets
;; =============================================================================

(def asset-with-trigger
  "Team asset with a triggered ability."
  {:slug "home-court-advantage"
   :name "Home Court Advantage"
   :card-type :card-type/TEAM_ASSET_CARD
   :asset-power
   {:asset/id "home-court"
    :asset/name "Home Court Advantage"
    :asset/triggers
    [{:trigger {:trigger/event :bashketball/turn-started.at
                :trigger/condition [:= :doc/active-player :self/team]}
      :effect {:effect/type :bashketball/draw-cards
               :team :self/team
               :count 1}}]}})

(def asset-with-two-triggers
  "Team asset with multiple triggered abilities."
  {:slug "momentum-builder"
   :name "Momentum Builder"
   :card-type :card-type/TEAM_ASSET_CARD
   :asset-power
   {:asset/id "momentum"
    :asset/triggers
    [{:trigger {:trigger/event :bashketball/shoot.after
                :trigger/condition [:= :event/success true]}
      :effect {:effect/type :bashketball/add-modifier
               :stat :stat/SHOOTING
               :amount 1}}
     {:trigger {:trigger/event :bashketball/pass.after
                :trigger/condition [:= :event/success true]}
      :effect {:effect/type :bashketball/add-modifier
               :stat :stat/PASSING
               :amount 1}}]}})

(def asset-without-triggers
  "Team asset with no triggers (passive or activated only)."
  {:slug "team-banner"
   :name "Team Banner"
   :card-type :card-type/TEAM_ASSET_CARD
   :asset-power
   {:asset/id "banner"
    :asset/condition [:= :doc/home-game true]}})

(def response-asset
  "Response asset that prompts Apply/Pass."
  {:slug "defensive-timeout"
   :name "Defensive Timeout"
   :card-type :card-type/TEAM_ASSET_CARD
   :card-subtypes [:card-subtype/RESPONSE]
   :asset-power
   {:asset/id "defensive-timeout"
    :asset/response
    {:response/trigger {:trigger/event :bashketball/skill-test.before
                        :trigger/condition [:= :event/defender-team :self/team]}
     :response/prompt "Call Defensive Timeout?"
     :response/effect {:effect/type :bashketball/add-modifier
                       :target :event/defender-id
                       :stat :stat/DEFENSE
                       :amount 2}}}})

(def test-catalog
  "Catalog with test cards."
  (catalog/create-catalog
   {"orc-center" player-with-ability
    "elf-point-guard" player-without-ability
    "dwarf-power-forward" player-with-two-abilities
    "troll-center" player-without-ability
    "goblin-shooting-guard" player-without-ability
    "human-small-forward" player-without-ability
    "quick-release" ability-card
    "home-court-advantage" asset-with-trigger
    "momentum-builder" asset-with-two-triggers
    "team-banner" asset-without-triggers
    "defensive-timeout" response-asset}))

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

(deftest substitute-effect-updates-registry-test
  (testing "substitute effect updates registry correctly"
    (let [game-state  (-> (fixtures/base-game-state)
                          (fixtures/with-player-at fixtures/home-player-1 [1 3]))
          ;; Register trigger for player on court
          initial-reg (card-effects/initialize-game-triggers game-state test-catalog)
          _           (is (= 1 (count-triggers initial-reg)))  ;; orc-center has 1 ability
          ;; Substitute: orc-center leaves, elf-point-guard enters
          result      (fx/apply-effect game-state
                                       {:type :bashketball/do-substitute
                                        :on-court-id fixtures/home-player-1
                                        :off-court-id fixtures/home-player-2}
                                       {}
                                       {:registry initial-reg
                                        :effect-catalog test-catalog})]
      ;; orc-center trigger removed, elf-point-guard has no abilities
      (is (= 0 (count-triggers (:registry result)))))))

(deftest attach-ability-effect-registers-triggers-test
  (testing "attach-ability effect registers triggers"
    (let [game-state        (-> (fixtures/base-game-state)
                                (fixtures/with-player-at fixtures/home-player-1 [1 3])
                                (fixtures/with-drawn-cards :team/HOME 3))
          ;; Add ability card to hand
          game-with-ability (update-in game-state [:players :team/HOME :deck :hand]
                                       conj {:instance-id "ability-card-1"
                                             :card-slug "quick-release"})
          ;; Attach it using effect
          result            (fx/apply-effect game-with-ability
                                             {:type :bashketball/attach-ability
                                              :player :team/HOME
                                              :instance-id "ability-card-1"
                                              :target-player-id fixtures/home-player-1}
                                             {}
                                             {:registry (triggers/create-registry)
                                              :effect-catalog test-catalog})]
      (is (= 1 (count-triggers (:registry result))))
      (is (has-trigger-for-source? (:registry result) "ability-card-1")))))

(deftest detach-ability-effect-unregisters-triggers-test
  (testing "detach-ability effect unregisters triggers"
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
          ;; Detach it using effect
          result               (fx/apply-effect game-with-attachment
                                                {:type :bashketball/detach-ability
                                                 :player :team/HOME
                                                 :target-player-id fixtures/home-player-1
                                                 :instance-id "ability-card-1"}
                                                {}
                                                {:registry initial-reg
                                                 :effect-catalog test-catalog})]
      (is (= 0 (count-triggers (:registry result)))))))

;; =============================================================================
;; Tests: Effect Integration with Registry
;; =============================================================================

(deftest effect-updates-registry-on-substitute-test
  (testing "effect with catalog updates registry on substitute"
    (let [game-state (-> (fixtures/base-game-state)
                         (fixtures/with-player-at fixtures/home-player-1 [1 3]))
          registry   (card-effects/initialize-game-triggers game-state test-catalog)
          result     (fx/apply-effect game-state
                                      {:type :bashketball/do-substitute
                                       :on-court-id fixtures/home-player-1
                                       :off-court-id fixtures/home-player-2}
                                      {}
                                      {:registry registry
                                       :effect-catalog test-catalog})]
      ;; orc-center had 1 trigger, elf-point-guard has 0
      (is (= 0 (count-triggers (:registry result)))))))

;; =============================================================================
;; Tests: Team Asset Registration
;; =============================================================================

(deftest register-asset-triggers-test
  (testing "registers trigger for asset with single trigger"
    (let [asset    {:instance-id "asset-1" :card-slug "home-court-advantage"}
          registry (card-effects/register-asset-triggers
                    (triggers/create-registry)
                    test-catalog
                    asset
                    :team/HOME)]
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry "asset-1"))))

  (testing "registers multiple triggers for asset with multiple triggers"
    (let [asset    {:instance-id "asset-2" :card-slug "momentum-builder"}
          registry (card-effects/register-asset-triggers
                    (triggers/create-registry)
                    test-catalog
                    asset
                    :team/HOME)]
      (is (= 2 (count-triggers registry)))))

  (testing "registers no triggers for asset without triggers"
    (let [asset    {:instance-id "asset-3" :card-slug "team-banner"}
          registry (card-effects/register-asset-triggers
                    (triggers/create-registry)
                    test-catalog
                    asset
                    :team/HOME)]
      (is (= 0 (count-triggers registry)))))

  (testing "registers prompt trigger for response asset"
    (let [asset    {:instance-id "asset-4" :card-slug "defensive-timeout"}
          registry (card-effects/register-asset-triggers
                    (triggers/create-registry)
                    test-catalog
                    asset
                    :team/HOME)]
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry "asset-4")))))

(deftest unregister-asset-triggers-test
  (testing "unregisters all triggers for an asset"
    (let [asset    {:instance-id "asset-1" :card-slug "momentum-builder"}
          registry (card-effects/register-asset-triggers
                    (triggers/create-registry)
                    test-catalog
                    asset
                    :team/HOME)
          _        (is (= 2 (count-triggers registry)))
          updated  (card-effects/unregister-asset-triggers registry "asset-1")]
      (is (= 0 (count-triggers updated))))))

(deftest token-asset-test
  (testing "registers triggers for token asset with inline definition"
    (let [token-card  {:slug "generated-asset"
                       :card-type :card-type/TEAM_ASSET_CARD
                       :asset-power
                       {:asset/id "gen-asset"
                        :asset/triggers
                        [{:trigger {:trigger/event :bashketball/score.after}
                          :effect {:effect/type :bashketball/draw-cards :count 1}}]}}
          token-asset {:instance-id "token-asset-1"
                       :token true
                       :card token-card}
          registry    (card-effects/register-asset-triggers
                       (triggers/create-registry)
                       test-catalog
                       token-asset
                       :team/HOME)]
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry "token-asset-1")))))

;; =============================================================================
;; Tests: Play-Card and Move-Asset Registry Updates
;; =============================================================================

(deftest play-card-effect-registers-triggers-test
  (testing "stage and resolve registers triggers when card is a team asset"
    (let [game-state     (-> (fixtures/base-game-state)
                             (fixtures/with-drawn-cards :team/HOME 3))
          ;; Add asset card definition to deck catalog AND card to hand
          game-with-card (-> game-state
                             (update-in [:players :team/HOME :deck :cards]
                                        conj asset-with-trigger)
                             (update-in [:players :team/HOME :deck :hand]
                                        conj {:instance-id "asset-card-1"
                                              :card-slug "home-court-advantage"}))
          ;; Stage the card first
          staged-result  (fx/apply-effect game-with-card
                                          {:type :bashketball/do-stage-card
                                           :player :team/HOME
                                           :instance-id "asset-card-1"}
                                          {} {})
          ;; Then resolve it to move to assets and register triggers
          result         (fx/apply-effect (:state staged-result)
                                          {:type :bashketball/do-resolve-card
                                           :instance-id "asset-card-1"}
                                          {}
                                          {:registry (triggers/create-registry)
                                           :effect-catalog test-catalog})]
      (is (= 1 (count-triggers (:registry result))))
      (is (has-trigger-for-source? (:registry result) "asset-card-1"))))

  (testing "stage and resolve does not register triggers for non-asset cards"
    (let [game-state     (-> (fixtures/base-game-state)
                             (fixtures/with-drawn-cards :team/HOME 3))
          ;; Add a non-asset card definition to deck catalog AND card to hand
          game-with-card (-> game-state
                             (update-in [:players :team/HOME :deck :cards]
                                        conj player-without-ability)
                             (update-in [:players :team/HOME :deck :hand]
                                        conj {:instance-id "play-card-1"
                                              :card-slug "elf-point-guard"}))
          ;; Stage the card first
          staged-result  (fx/apply-effect game-with-card
                                          {:type :bashketball/do-stage-card
                                           :player :team/HOME
                                           :instance-id "play-card-1"}
                                          {} {})
          ;; Resolve - should go to discard, not assets
          result         (fx/apply-effect (:state staged-result)
                                          {:type :bashketball/do-resolve-card
                                           :instance-id "play-card-1"}
                                          {}
                                          {:registry (triggers/create-registry)
                                           :effect-catalog test-catalog})]
      (is (= 0 (count-triggers (:registry result)))))))

(deftest move-asset-effect-unregisters-triggers-test
  (testing "move-asset effect unregisters triggers when asset removed"
    (let [game-state      (fixtures/base-game-state)
          ;; Add asset to team's assets
          game-with-asset (update-in game-state [:players :team/HOME :assets]
                                     conj {:instance-id "asset-card-1"
                                           :card-slug "home-court-advantage"})
          ;; Register the trigger
          initial-reg     (card-effects/register-asset-triggers
                           (triggers/create-registry)
                           test-catalog
                           {:instance-id "asset-card-1" :card-slug "home-court-advantage"}
                           :team/HOME)
          _               (is (= 1 (count-triggers initial-reg)))
          ;; Move asset to discard using effect
          result          (fx/apply-effect game-with-asset
                                           {:type :bashketball/do-move-asset
                                            :player :team/HOME
                                            :instance-id "asset-card-1"
                                            :destination :DISCARD}
                                           {}
                                           {:registry initial-reg
                                            :effect-catalog test-catalog})]
      (is (= 0 (count-triggers (:registry result)))))))

;; =============================================================================
;; Tests: Initialize Game with Assets
;; =============================================================================

(deftest initialize-game-triggers-with-assets-test
  (testing "registers triggers for assets already in play"
    (let [game-state (-> (fixtures/base-game-state)
                         (update-in [:players :team/HOME :assets]
                                    conj {:instance-id "asset-1"
                                          :card-slug "home-court-advantage"})
                         (update-in [:players :team/AWAY :assets]
                                    conj {:instance-id "asset-2"
                                          :card-slug "momentum-builder"}))
          registry   (card-effects/initialize-game-triggers game-state test-catalog)]
      ;; home-court-advantage has 1 trigger, momentum-builder has 2
      (is (= 3 (count-triggers registry)))
      (is (has-trigger-for-source? registry "asset-1"))
      (is (has-trigger-for-source? registry "asset-2")))))

;; =============================================================================
;; Tests: Create-Token Registry Updates
;; =============================================================================

(def token-ability-card-def
  "Token ability card with a triggered ability."
  {:slug "hot-hand"
   :name "Hot Hand"
   :card-type :card-type/ABILITY_CARD
   :set-slug "tokens"
   :fate 0
   :abilities [{:ability/id "hot-hand"
                :ability/trigger {:trigger/event :bashketball/shoot.after
                                  :trigger/condition [:= :event/success true]}
                :ability/effect {:effect/type :bashketball/add-modifier
                                 :stat :stat/SHOOTING
                                 :amount 1}}]})

(def token-asset-card-def
  "Token asset card with a trigger."
  {:slug "momentum-token"
   :name "Momentum Token"
   :card-type :card-type/TEAM_ASSET_CARD
   :set-slug "tokens"
   :asset-power
   {:asset/id "momentum-token"
    :asset/triggers
    [{:trigger {:trigger/event :bashketball/score.after}
      :effect {:effect/type :bashketball/draw-cards :count 1}}]}})

(deftest update-registry-for-create-token-ability-test
  (testing "create-token registers triggers for token ability attachment"
    (let [game-state     (fixtures/base-game-state)
          ;; Get a player - use a player that exists
          player-id      "HOME-elf-point-guard-0"
          instance-id    "token-ability-123"
          token-instance {:instance-id instance-id
                          :token true
                          :card token-ability-card-def}
          ;; Create a new state with the token attached and event recorded
          new-state      (-> game-state
                             (state/update-basketball-player player-id
                                                             update :attachments conj token-instance)
                             (update :events conj {:type :bashketball/create-token
                                                   :created-token token-instance
                                                   :placement :placement/ATTACH
                                                   :target-player-id player-id}))
          action         {:type :bashketball/create-token
                          :player :team/HOME
                          :placement :placement/ATTACH
                          :target-player-id player-id
                          :card token-ability-card-def}
          registry       (card-effects/update-registry-for-action
                          (triggers/create-registry) test-catalog game-state new-state action)]
      ;; Verify trigger was registered
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry instance-id)))))

(deftest update-registry-for-create-token-asset-test
  (testing "create-token registers triggers for token asset"
    (let [game-state     (fixtures/base-game-state)
          instance-id    "token-asset-456"
          token-instance {:instance-id instance-id
                          :token true
                          :card token-asset-card-def}
          ;; Create a new state with the token in assets and event recorded
          new-state      (-> game-state
                             (update-in [:players :team/HOME :assets] conj token-instance)
                             (update :events conj {:type :bashketball/create-token
                                                   :created-token token-instance
                                                   :placement :placement/ASSET}))
          action         {:type :bashketball/create-token
                          :player :team/HOME
                          :placement :placement/ASSET
                          :card token-asset-card-def}
          registry       (card-effects/update-registry-for-action
                          (triggers/create-registry) test-catalog game-state new-state action)]
      ;; Verify trigger was registered
      (is (= 1 (count-triggers registry)))
      (is (has-trigger-for-source? registry instance-id)))))

(deftest create-token-registry-updates-test
  (testing "token abilities registered via update-registry-for-action work correctly"
    (let [game-state     (fixtures/base-game-state)
          player-id      "HOME-elf-point-guard-0"
          instance-id    "token-triggered-789"
          ;; Token with triggered ability
          token-card     {:slug "quick-shot"
                          :card-type :card-type/ABILITY_CARD
                          :set-slug "tokens"
                          :abilities [{:ability/id "quick-shot"
                                       :ability/trigger {:trigger/event :bashketball/pass.after}
                                       :ability/effect {:effect/type :bashketball/exhaust-player}}]}
          token-instance {:instance-id instance-id
                          :token true
                          :card token-card}
          new-state      (-> game-state
                             (state/update-basketball-player player-id
                                                             update :attachments conj token-instance)
                             (update :events conj {:type :bashketball/create-token
                                                   :created-token token-instance
                                                   :placement :placement/ATTACH
                                                   :target-player-id player-id}))
          action         {:type :bashketball/create-token
                          :player :team/HOME
                          :placement :placement/ATTACH
                          :target-player-id player-id
                          :card token-card}
          registry       (card-effects/update-registry-for-action
                          (triggers/create-registry) test-catalog game-state new-state action)]
      ;; Verify exactly 1 trigger registered
      (is (= 1 (count-triggers registry)))
      ;; Verify it's registered for the token's instance-id
      (is (has-trigger-for-source? registry instance-id))
      ;; Verify unregistration works
      (let [unregistered (card-effects/unregister-attached-abilities registry instance-id)]
        (is (= 0 (count-triggers unregistered)))))))
