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
    :abilities ["clutch" "fadeaway"]}
   {:slug "shaq"
    :name "Shaquille O'Neal"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 7
    :pss 4
    :def 9
    :speed 5
    :size :size/LG
    :abilities ["dominant-post"]}
   {:slug "mugsy-bogues"
    :name "Muggsy Bogues"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 6
    :pss 9
    :def 7
    :speed 10
    :size :size/SM
    :abilities ["steal-master"]}
   {:slug "scottie-pippen"
    :name "Scottie Pippen"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 7
    :pss 8
    :def 9
    :speed 8
    :size :size/MD
    :abilities ["lockdown-defender"]}
   {:slug "elf-point-guard"
    :name "Elf Point Guard"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 6
    :pss 9
    :def 5
    :speed 10
    :size :size/SM
    :abilities ["quick-feet"]}
   {:slug "dwarf-power-forward"
    :name "Dwarf Power Forward"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 5
    :pss 4
    :def 8
    :speed 4
    :size :size/MD
    :abilities ["tough"]}
   {:slug "orc-center"
    :name "Orc Center"
    :set-slug "base"
    :card-type :card-type/PLAYER_CARD
    :sht 4
    :pss 3
    :def 9
    :speed 3
    :size :size/LG
    :abilities ["intimidate"]}])

(def test-action-cards
  "Mock action cards for testing."
  [{:slug "basic-shot"
    :name "Basic Shot"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 3
    :offense "Take a basic shot attempt"
    :defense nil}
   {:slug "jump-shot"
    :name "Jump Shot"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 4
    :offense "Take a mid-range jump shot"
    :defense nil}
   {:slug "layup"
    :name "Layup"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 5
    :offense "Drive to the basket for a layup"
    :defense nil}
   {:slug "drive-and-dish"
    :name "Drive and Dish"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 4
    :offense "Drive then pass to an open teammate"
    :defense nil}
   {:slug "post-up"
    :name "Post Up"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 4
    :offense "Establish position in the post"
    :defense nil}
   {:slug "pick-and-roll"
    :name "Pick and Roll"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 5
    :offense "Run a pick and roll play"
    :defense nil}
   {:slug "alley-oop"
    :name "Alley Oop"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 6
    :offense "Throw an alley-oop pass"
    :defense nil}
   {:slug "fast-break"
    :name "Fast Break"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 5
    :offense "Push the ball in transition"
    :defense nil}
   {:slug "basic-pass"
    :name "Basic Pass"
    :set-slug "base"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 2
    :offense "Pass to a teammate"
    :defense nil}
   {:slug "special-play"
    :name "Special Play"
    :set-slug "base"
    :card-type :card-type/PLAY_CARD
    :fate 5
    :offense "Execute a special play"
    :defense nil}])

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
