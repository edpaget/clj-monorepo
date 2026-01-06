(ns bashketball-game.polix.effects-test
  (:require
   [bashketball-game.effect-catalog :as catalog]
   [bashketball-game.polix.card-effects :as card-effects]
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.effects :as effects]
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.game-rules :as game-rules]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [f]
    (polix/initialize!)
    (f)))

(defn opts-with-registry
  "Returns opts map with a registry containing game rules."
  []
  {:validate? false
   :registry (-> (triggers/create-registry)
                 (game-rules/register-game-rules!))})

(deftest move-player-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        result (fx/apply-effect state
                                {:type :bashketball/move-player
                                 :player-id fixtures/home-player-1
                                 :position [2 5]}
                                {} (opts-with-registry))]
    (testing "moves player to new position"
      (is (= [2 5] (:position (state/get-basketball-player (:state result) fixtures/home-player-1)))))

    (testing "returns applied action"
      (is (= 1 (count (:applied result)))))

    (testing "no failures"
      (is (empty? (:failed result))))))

(deftest exhaust-player-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/exhaust-player
                                 :player-id fixtures/home-player-1}
                                {} (opts-with-registry))]
    (testing "marks player as exhausted"
      (is (true? (:exhausted (state/get-basketball-player (:state result) fixtures/home-player-1)))))))

(deftest refresh-player-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-exhausted fixtures/home-player-1))
        result (fx/apply-effect state
                                {:type :bashketball/refresh-player
                                 :player-id fixtures/home-player-1}
                                {} (opts-with-registry))]
    (testing "removes exhaustion from player"
      (is (false? (:exhausted (state/get-basketball-player (:state result) fixtures/home-player-1)))))))

(deftest give-ball-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/give-ball
                                 :player-id fixtures/home-player-1}
                                {} (opts-with-registry))]
    (testing "sets ball as possessed by player"
      (is (= :ball-status/POSSESSED (:status (state/get-ball (:state result)))))
      (is (= fixtures/home-player-1 (:holder-id (state/get-ball (:state result))))))))

(deftest loose-ball-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-ball-possessed fixtures/home-player-1))
        result (fx/apply-effect state
                                {:type :bashketball/loose-ball
                                 :position [3 5]}
                                {} (opts-with-registry))]
    (testing "sets ball as loose at position"
      (is (= :ball-status/LOOSE (:status (state/get-ball (:state result)))))
      (is (= [3 5] (:position (state/get-ball (:state result))))))))

(deftest draw-cards-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/draw-cards
                                 :player :team/HOME
                                 :count 3}
                                {} (opts-with-registry))]
    (testing "draws cards into hand"
      (is (= 3 (count (state/get-hand (:state result) :team/HOME)))))

    (testing "reduces draw pile"
      (is (= 2 (count (state/get-draw-pile (:state result) :team/HOME)))))))

(deftest draw-cards-logs-event-test
  (let [state       (fixtures/base-game-state)
        draw-pile   (state/get-draw-pile state :team/HOME)
        result      (fx/apply-effect state
                                     {:type :bashketball/draw-cards
                                      :player :team/HOME
                                      :count 2}
                                     {} (opts-with-registry))
        event       (last (:events (:state result)))]
    (testing "logs draw-cards event"
      (is (= :bashketball/draw-cards (:type event))))
    (testing "event contains player"
      (is (= :team/HOME (:player event))))
    (testing "event contains count"
      (is (= 2 (:count event))))
    (testing "event contains drawn cards"
      (is (= (take 2 draw-pile) (:cards event))))))

(deftest add-score-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/add-score
                                 :team :team/HOME
                                 :points 3}
                                {} (opts-with-registry))]
    (testing "adds points to team score"
      (is (= 3 (get-in (state/get-score (:state result)) [:team/HOME]))))))

(deftest effect-with-context-bindings-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        ctx    {:bindings {:self fixtures/home-player-1
                           :target-position [2 6]}}
        result (fx/apply-effect state
                                {:type :bashketball/move-player
                                 :player-id :self
                                 :position :target-position}
                                ctx (opts-with-registry))]
    (testing "resolves :self binding"
      (is (= [2 6] (:position (state/get-basketball-player (:state result) fixtures/home-player-1)))))))

(deftest discard-cards-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-drawn-cards :team/HOME 3))
        hand   (state/get-hand state :team/HOME)
        ids    [(-> hand first :instance-id)]
        result (fx/apply-effect state
                                {:type :bashketball/discard-cards
                                 :player :team/HOME
                                 :instance-ids ids}
                                {} (opts-with-registry))]
    (testing "removes card from hand"
      (is (= 2 (count (state/get-hand (:state result) :team/HOME)))))

    (testing "adds card to discard"
      (is (= 1 (count (state/get-discard (:state result) :team/HOME)))))))

(deftest sequence-of-effects-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effects state
                                 [{:type :bashketball/draw-cards
                                   :player :team/HOME
                                   :count 2}
                                  {:type :bashketball/add-score
                                   :team :team/HOME
                                   :points 2}]
                                 {} (opts-with-registry))]
    (testing "both effects applied"
      (is (= 2 (count (state/get-hand (:state result) :team/HOME))))
      (is (= 2 (get-in (state/get-score (:state result)) [:team/HOME]))))))

;; =============================================================================
;; Test Data for Ability Effects
;; =============================================================================

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
     :ability/effect {:effect/type :bashketball/draw-cards
                      :player :self/team
                      :count 1}}]})

(def ability-card-removed-destination
  "Ability card with detach destination of REMOVED."
  {:slug "power-shot"
   :name "Power Shot"
   :card-type :card-type/ABILITY_CARD
   :removable false
   :detach-destination :detach/REMOVED
   :abilities []})

(def test-effect-catalog
  "Catalog with test ability cards."
  (catalog/create-catalog
   {"quick-release" ability-card
    "power-shot" ability-card-removed-destination}))

(defn- count-triggers [registry]
  (count (triggers/get-triggers registry)))

(defn- has-trigger-for-source? [registry source-id]
  (some #(= (:source %) source-id)
        (triggers/get-triggers registry)))

(defn opts-with-registry-and-catalog
  "Returns opts map with registry and effect catalog for lifecycle effects."
  []
  {:validate? false
   :registry (-> (triggers/create-registry)
                 (game-rules/register-game-rules!))
   :effect-catalog test-effect-catalog})

;; =============================================================================
;; Attach Ability Effect Tests
;; =============================================================================

(deftest attach-ability-effect-removes-from-hand-test
  (let [card-instance {:instance-id "ability-1" :card-slug "quick-release"}
        state         (-> (fixtures/base-game-state)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        result        (fx/apply-effect state
                                       {:type :bashketball/attach-ability
                                        :player :team/HOME
                                        :instance-id "ability-1"
                                        :target-player-id fixtures/home-player-1}
                                       {} (opts-with-registry))]
    (testing "card removed from hand"
      (is (empty? (state/get-hand (:state result) :team/HOME))))))

(deftest attach-ability-effect-adds-to-player-test
  (let [card-instance {:instance-id "ability-1" :card-slug "quick-release"}
        state         (-> (fixtures/base-game-state)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        result        (fx/apply-effect state
                                       {:type :bashketball/attach-ability
                                        :player :team/HOME
                                        :instance-id "ability-1"
                                        :target-player-id fixtures/home-player-1}
                                       {} (opts-with-registry))]
    (testing "attachment added to player"
      (let [attachments (state/get-attachments (:state result) fixtures/home-player-1)]
        (is (= 1 (count attachments)))
        (is (= "ability-1" (:instance-id (first attachments))))
        (is (= "quick-release" (:card-slug (first attachments))))))))

(deftest attach-ability-effect-logs-event-test
  (let [card-instance {:instance-id "ability-1" :card-slug "quick-release"}
        state         (-> (fixtures/base-game-state)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        result        (fx/apply-effect state
                                       {:type :bashketball/attach-ability
                                        :player :team/HOME
                                        :instance-id "ability-1"
                                        :target-player-id fixtures/home-player-1}
                                       {} (opts-with-registry))
        event         (last (:events (:state result)))]
    (testing "logs attach event"
      (is (= :bashketball/attach-ability (:type event)))
      (is (= fixtures/home-player-1 (:target-player-id event))))))

(deftest attach-ability-effect-with-registry-test
  (let [card-instance {:instance-id "ability-1" :card-slug "quick-release"}
        state         (-> (fixtures/base-game-state)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        opts          (opts-with-registry-and-catalog)
        initial-count (count-triggers (:registry opts))
        result        (fx/apply-effect state
                                       {:type :bashketball/attach-ability
                                        :player :team/HOME
                                        :instance-id "ability-1"
                                        :target-player-id fixtures/home-player-1}
                                       {} opts)]
    (testing "returns updated registry"
      (is (some? (:registry result))))
    (testing "registry contains trigger for ability"
      (is (= (inc initial-count) (count-triggers (:registry result))))
      (is (has-trigger-for-source? (:registry result) "ability-1")))))

;; =============================================================================
;; Detach Ability Effect Tests
;; =============================================================================

(deftest detach-ability-effect-removes-from-player-test
  (let [attachment {:instance-id "ability-1"
                    :card-slug "quick-release"
                    :removable true
                    :detach-destination :detach/DISCARD
                    :attached-at "2024-01-01T00:00:00Z"}
        state      (-> (fixtures/base-game-state)
                       (state/update-basketball-player fixtures/home-player-1
                                                       assoc :attachments [attachment]))
        result     (fx/apply-effect state
                                    {:type :bashketball/detach-ability
                                     :player :team/HOME
                                     :target-player-id fixtures/home-player-1
                                     :instance-id "ability-1"}
                                    {} (opts-with-registry))]
    (testing "attachment removed from player"
      (is (empty? (state/get-attachments (:state result) fixtures/home-player-1))))))

(deftest detach-ability-effect-goes-to-discard-test
  (let [attachment {:instance-id "ability-1"
                    :card-slug "quick-release"
                    :removable true
                    :detach-destination :detach/DISCARD
                    :attached-at "2024-01-01T00:00:00Z"}
        state      (-> (fixtures/base-game-state)
                       (state/update-basketball-player fixtures/home-player-1
                                                       assoc :attachments [attachment]))
        result     (fx/apply-effect state
                                    {:type :bashketball/detach-ability
                                     :player :team/HOME
                                     :target-player-id fixtures/home-player-1
                                     :instance-id "ability-1"}
                                    {} (opts-with-registry))]
    (testing "card goes to discard"
      (let [discard (state/get-discard (:state result) :team/HOME)]
        (is (= 1 (count discard)))
        (is (= "ability-1" (:instance-id (first discard))))))))

(deftest detach-ability-effect-goes-to-removed-test
  (let [attachment {:instance-id "ability-1"
                    :card-slug "power-shot"
                    :removable false
                    :detach-destination :detach/REMOVED
                    :attached-at "2024-01-01T00:00:00Z"}
        state      (-> (fixtures/base-game-state)
                       (state/update-basketball-player fixtures/home-player-1
                                                       assoc :attachments [attachment]))
        result     (fx/apply-effect state
                                    {:type :bashketball/detach-ability
                                     :player :team/HOME
                                     :target-player-id fixtures/home-player-1
                                     :instance-id "ability-1"}
                                    {} (opts-with-registry))]
    (testing "card goes to removed pile"
      (let [removed (get-in (:state result) [:players :team/HOME :deck :removed])]
        (is (= 1 (count removed)))
        (is (= "ability-1" (:instance-id (first removed))))))))

(deftest detach-ability-effect-logs-event-test
  (let [attachment {:instance-id "ability-1"
                    :card-slug "quick-release"
                    :removable true
                    :detach-destination :detach/DISCARD
                    :attached-at "2024-01-01T00:00:00Z"}
        state      (-> (fixtures/base-game-state)
                       (state/update-basketball-player fixtures/home-player-1
                                                       assoc :attachments [attachment]))
        result     (fx/apply-effect state
                                    {:type :bashketball/detach-ability
                                     :player :team/HOME
                                     :target-player-id fixtures/home-player-1
                                     :instance-id "ability-1"}
                                    {} (opts-with-registry))
        event      (last (:events (:state result)))]
    (testing "logs detach event"
      (is (= :bashketball/detach-ability (:type event)))
      (is (= fixtures/home-player-1 (:target-player-id event)))
      (is (= :detach/DISCARD (:destination event))))))

(deftest detach-ability-effect-with-registry-test
  (let [attachment   {:instance-id "ability-1"
                      :card-slug "quick-release"
                      :removable true
                      :detach-destination :detach/DISCARD
                      :attached-at "2024-01-01T00:00:00Z"}
        state        (-> (fixtures/base-game-state)
                         (state/update-basketball-player fixtures/home-player-1
                                                         assoc :attachments [attachment]))
        ;; Pre-register the ability trigger
        initial-reg  (card-effects/register-attached-abilities
                      (-> (triggers/create-registry)
                          (game-rules/register-game-rules!))
                      test-effect-catalog
                      attachment
                      fixtures/home-player-1
                      :team/HOME)
        opts         {:validate? false
                      :registry initial-reg
                      :effect-catalog test-effect-catalog}
        result       (fx/apply-effect state
                                      {:type :bashketball/detach-ability
                                       :player :team/HOME
                                       :target-player-id fixtures/home-player-1
                                       :instance-id "ability-1"}
                                      {} opts)]
    (testing "returns updated registry"
      (is (some? (:registry result))))
    (testing "registry has trigger removed"
      (is (not (has-trigger-for-source? (:registry result) "ability-1"))))))

(deftest detach-token-effect-does-not-add-to-discard-test
  (let [token-attachment {:instance-id "token-1"
                          :card-slug "speed-boost"
                          :token true
                          :card {:slug "speed-boost" :card-type :card-type/ABILITY_CARD}
                          :removable true
                          :detach-destination :detach/DISCARD
                          :attached-at "2024-01-01T00:00:00Z"}
        state            (-> (fixtures/base-game-state)
                             (state/update-basketball-player fixtures/home-player-1
                                                             assoc :attachments [token-attachment]))
        result           (fx/apply-effect state
                                          {:type :bashketball/detach-ability
                                           :player :team/HOME
                                           :target-player-id fixtures/home-player-1
                                           :instance-id "token-1"}
                                          {} (opts-with-registry))]
    (testing "token removed from player"
      (is (empty? (state/get-attachments (:state result) fixtures/home-player-1))))
    (testing "token NOT added to discard"
      (is (empty? (state/get-discard (:state result) :team/HOME))))
    (testing "event shows deleted destination"
      (let [event (last (:events (:state result)))]
        (is (= :deleted (:destination event)))
        (is (true? (:token-deleted? event)))))))
