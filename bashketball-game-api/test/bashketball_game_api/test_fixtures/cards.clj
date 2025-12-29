(ns bashketball-game-api.test-fixtures.cards
  "Test card fixtures and mock card catalog.

  Provides mock cards for testing without depending on the actual cards JAR.
  The mock catalog implements the CardCatalog protocol with a fixed set of
  test cards."
  (:require
   [bashketball-game-api.services.catalog :as catalog]))

;; ---------------------------------------------------------------------------
;; Test Card Data

(def test-player-cards
  "Mock player cards for testing."
  [{:slug "michael-jordan"
    :name "Michael Jordan"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 10
    :pss 8
    :def 8
    :speed 9
    :size :size/MD
    :player-subtypes [:player-subtype/HUMAN]
    :abilities [{:ability/id "clutch"} {:ability/id "fadeaway"}]}
   {:slug "shaq"
    :name "Shaquille O'Neal"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 7
    :pss 4
    :def 9
    :speed 5
    :size :size/LG
    :player-subtypes [:player-subtype/HUMAN]
    :abilities [{:ability/id "dominant-post"}]}
   {:slug "mugsy-bogues"
    :name "Muggsy Bogues"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 6
    :pss 9
    :def 7
    :speed 10
    :size :size/SM
    :player-subtypes [:player-subtype/HUMAN]
    :abilities [{:ability/id "steal-master"}]}
   {:slug "scottie-pippen"
    :name "Scottie Pippen"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 7
    :pss 8
    :def 9
    :speed 8
    :size :size/MD
    :player-subtypes [:player-subtype/HUMAN]
    :abilities [{:ability/id "lockdown-defender"}]}
   {:slug "elf-point-guard"
    :name "Elf Point Guard"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 6
    :pss 9
    :def 5
    :speed 10
    :size :size/SM
    :player-subtypes [:player-subtype/ELF]
    :abilities [{:ability/id "quick-feet"}]}
   {:slug "dwarf-power-forward"
    :name "Dwarf Power Forward"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 5
    :pss 4
    :def 8
    :speed 4
    :size :size/MD
    :player-subtypes [:player-subtype/DWARF]
    :abilities [{:ability/id "tough"}]}
   {:slug "orc-center"
    :name "Orc Center"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 4
    :pss 3
    :def 9
    :speed 3
    :size :size/LG
    :player-subtypes [:player-subtype/ORC]
    :abilities [{:ability/id "intimidate"}]}])

(def test-action-cards
  "Mock action cards for testing."
  [{:slug "basic-shot"
    :name "Basic Shot"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 3
    :offense {:action/id "basic-shot-offense"
              :action/description "Take a basic shot attempt"
              :action/effect {:effect/type :bashketball/shoot}}}
   {:slug "jump-shot"
    :name "Jump Shot"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 4
    :offense {:action/id "jump-shot-offense"
              :action/description "Take a mid-range jump shot"
              :action/effect {:effect/type :bashketball/shoot}}}
   {:slug "layup"
    :name "Layup"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 5
    :offense {:action/id "layup-offense"
              :action/description "Drive to the basket for a layup"
              :action/effect {:effect/type :bashketball/shoot}}}
   {:slug "drive-and-dish"
    :name "Drive and Dish"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 4
    :offense {:action/id "drive-and-dish-offense"
              :action/description "Drive then pass to an open teammate"
              :action/effect {:effect/type :bashketball/pass}}}
   {:slug "post-up"
    :name "Post Up"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 4
    :offense {:action/id "post-up-offense"
              :action/description "Establish position in the post"
              :action/effect {:effect/type :bashketball/move}}}
   {:slug "pick-and-roll"
    :name "Pick and Roll"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 5
    :offense {:action/id "pick-and-roll-offense"
              :action/description "Run a pick and roll play"
              :action/effect {:effect/type :bashketball/screen}}}
   {:slug "alley-oop"
    :name "Alley Oop"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 6
    :offense {:action/id "alley-oop-offense"
              :action/description "Throw an alley-oop pass"
              :action/effect {:effect/type :bashketball/pass}}}
   {:slug "fast-break"
    :name "Fast Break"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 5
    :offense {:action/id "fast-break-offense"
              :action/description "Push the ball in transition"
              :action/effect {:effect/type :bashketball/move}}}
   {:slug "basic-pass"
    :name "Basic Pass"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 2
    :offense {:action/id "basic-pass-offense"
              :action/description "Pass to a teammate"
              :action/effect {:effect/type :bashketball/pass}}}
   {:slug "special-play"
    :name "Special Play"
    :set-slug "base"
    :card-type :card-type/PLAY_CARD
    :fate 5
    :play {:play/id "special-play"
           :play/description "Execute a special play"
           :play/effect {:effect/type :bashketball/special}}}])

(def test-sets
  "Mock card sets for testing."
  [{:slug "base"
    :name "Base Set"
    :description "Base card set for testing purposes"}])

(def all-test-cards
  "All mock cards combined."
  (concat test-player-cards test-action-cards))

;; ---------------------------------------------------------------------------
;; Mock Card Catalog

(defrecord MockCardCatalog [cards-by-slug cards-by-set cards-by-type sets-by-slug]
  catalog/CardCatalog
  (get-card [_this slug]
    (get cards-by-slug slug))

  (get-cards [_this]
    (vals cards-by-slug))

  (get-cards-by-set [_this set-slug]
    (get cards-by-set set-slug []))

  (get-cards-by-type [_this card-type]
    (get cards-by-type card-type []))

  (get-set [_this set-slug]
    (get sets-by-slug set-slug))

  (get-sets [_this]
    (vals sets-by-slug)))

(defn create-mock-card-catalog
  "Creates a mock card catalog with test cards.

  Accepts optional additional cards to merge with the default test cards."
  ([]
   (create-mock-card-catalog all-test-cards test-sets))
  ([cards sets]
   (let [cards-by-slug (into {} (map (juxt :slug identity) cards))
         cards-by-set  (group-by :set-slug cards)
         cards-by-type (group-by :card-type cards)
         sets-by-slug  (into {} (map (juxt :slug identity) sets))]
     (->MockCardCatalog cards-by-slug cards-by-set cards-by-type sets-by-slug))))

(def mock-card-catalog
  "Default mock card catalog instance for testing."
  (create-mock-card-catalog))
