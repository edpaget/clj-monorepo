(ns bashketball-game.standard-actions
  "Standard action card definitions.

  Standard actions are basic gameplay actions available to all players.
  Players can either play a standard action card from their hand, or
  discard 2 cards to use any standard action without the card.

  The three standard action cards are:
  - Shoot / Block
  - Pass / Steal
  - Screen / Check")

(def standard-action-cards
  "Vector of all standard action card definitions.

  Each card follows the [[bashketball-schemas.card/StandardActionCard]] schema
  with offense and defense options representing the two sides of the card.

  Standard actions use structured [[bashketball-schemas.effect/ActionModeDef]]
  with preconditions, targets, and effects that integrate with the polix system."
  [{:slug      "shoot-block"
    :name      "Shoot / Block"
    :set-slug  "standard"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate      3
    :offense
    {:action/id          "shoot"
     :action/name        "Shoot"
     :action/description "Ball carrier within 7 hexes of basket attempts Shot"
     :action/requires    [:and
                          [:has-ball? :doc/state :actor/id]
                          [:within-range? :doc/state :actor/id :basket 7]
                          [:not [:player-exhausted? :doc/state :actor/id]]]
     :action/targets     [:actor/id]
     :action/effect      {:effect/type :polix.effects/sequence
                          :effects     [{:effect/type :bashketball/exhaust-player
                                         :player-id   :actor/id}
                                        {:effect/type   :bashketball/initiate-skill-test
                                         :actor-id      :actor/id
                                         :stat          :stat/SHOOTING
                                         :target-value  nil
                                         :context       {:type :shoot}}]}}
    :defense
    {:action/id          "block"
     :action/name        "Block"
     :action/description "Force opponent with ball within 4 hexes of basket to shoot or exhaust"
     :action/requires    [:and
                          [:adjacent? :doc/state :actor/id :target/id]
                          [:has-ball? :doc/state :target/id]
                          [:within-range? :doc/state :target/id :basket 4]]
     :action/targets     [:actor/id :target/id]
     :action/effect      {:effect/type :bashketball/force-choice
                          :target      :target/id
                          :choice-type :shoot-or-exhaust
                          :options     [{:id :shoot :label "Shoot"}
                                        {:id :exhaust-skip :label "Exhaust & Skip"}]}}}

   {:slug      "pass-steal"
    :name      "Pass / Steal"
    :set-slug  "standard"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate      3
    :offense
    {:action/id          "pass"
     :action/name        "Pass"
     :action/description "Ball carrier passes to teammate within 6 hexes"
     :action/requires    [:and
                          [:has-ball? :doc/state :actor/id]
                          [:player-on-court? :doc/state :target/id]
                          [:within-range? :doc/state :actor/id :target/id 6]
                          [:= [:player-team :doc/state :actor/id]
                           [:player-team :doc/state :target/id]]]
     :action/targets     [:actor/id :target/id]
     :action/effect      {:effect/type :bashketball/initiate-skill-test
                          :actor-id    :actor/id
                          :stat        :stat/PASSING
                          :target-value nil
                          :context     {:type   :pass
                                        :target :target/id}}}
    :defense
    {:action/id          "steal"
     :action/name        "Steal"
     :action/description "Engage ball carrier within 2 hexes, attempt steal"
     :action/requires    [:and
                          [:adjacent? :doc/state :actor/id :target/id]
                          [:has-ball? :doc/state :target/id]
                          [:not [:= [:player-team :doc/state :actor/id]
                                 [:player-team :doc/state :target/id]]]]
     :action/targets     [:actor/id :target/id]
     :action/effect      {:effect/type :bashketball/initiate-skill-test
                          :actor-id    :actor/id
                          :stat        :stat/DEFENSE
                          :target-value nil
                          :context     {:type   :steal
                                        :target :target/id}}}}

   {:slug      "screen-check"
    :name      "Screen / Check"
    :set-slug  "standard"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate      3
    :offense
    {:action/id          "screen"
     :action/name        "Screen"
     :action/description "Engage defender within 2 hexes, screen play (exhaust both)"
     :action/requires    [:and
                          [:adjacent? :doc/state :actor/id :target/id]
                          [:not [:= [:player-team :doc/state :actor/id]
                                 [:player-team :doc/state :target/id]]]
                          [:not [:player-exhausted? :doc/state :actor/id]]]
     :action/targets     [:actor/id :target/id]
     :action/effect      {:effect/type :polix.effects/sequence
                          :effects     [{:effect/type :bashketball/exhaust-player
                                         :player-id   :actor/id}
                                        {:effect/type :bashketball/exhaust-player
                                         :player-id   :target/id}]}}
    :defense
    {:action/id          "check"
     :action/name        "Check"
     :action/description "Engage opponent within 2 hexes, check play"
     :action/requires    [:and
                          [:adjacent? :doc/state :actor/id :target/id]
                          [:not [:= [:player-team :doc/state :actor/id]
                                 [:player-team :doc/state :target/id]]]
                          [:not [:player-exhausted? :doc/state :actor/id]]]
     :action/targets     [:actor/id :target/id]
     :action/effect      {:effect/type  :bashketball/initiate-skill-test
                          :actor-id     :actor/id
                          :stat         :stat/DEFENSE
                          :target-value nil
                          :context      {:type   :check
                                         :target :target/id}}}}])

(def standard-action-slugs
  "Set of valid standard action card slugs."
  #{"shoot-block" "pass-steal" "screen-check"})

(defn get-standard-action
  "Returns the standard action card definition for the given slug, or nil."
  [slug]
  (first (filter #(= (:slug %) slug) standard-action-cards)))

(defn valid-standard-action-slug?
  "Returns true if the slug is a valid standard action card."
  [slug]
  (contains? standard-action-slugs slug))
