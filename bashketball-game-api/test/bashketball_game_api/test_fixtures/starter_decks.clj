(ns bashketball-game-api.test-fixtures.starter-decks
  "Mock starter deck configuration for testing.

  Provides a fixed set of starter deck definitions that don't depend on the
  actual starter-decks.edn configuration, making tests more resilient to
  configuration changes.")

(def mock-starter-decks-config
  "Mock starter deck configuration for testing.

  Contains three starter decks with cards that exist in the mock card catalog."
  {:starter-decks
   [{:id :speed-demons
     :name "Speed Demons"
     :description "Fast-paced offense built around quick players and transition plays."
     :card-slugs ["michael-jordan" "shaq" "mugsy-bogues"
                  "basic-shot" "basic-shot" "basic-shot" "basic-shot"
                  "jump-shot" "jump-shot" "jump-shot" "jump-shot"
                  "layup" "layup" "layup" "layup"
                  "drive-and-dish" "drive-and-dish" "drive-and-dish" "drive-and-dish"
                  "post-up" "post-up" "post-up" "post-up"
                  "pick-and-roll" "pick-and-roll" "pick-and-roll" "pick-and-roll"
                  "alley-oop" "alley-oop"
                  "fast-break" "fast-break"]}
    {:id :post-dominance
     :name "Post Dominance"
     :description "Inside-out offense with dominant post presence."
     :card-slugs ["michael-jordan" "shaq" "mugsy-bogues"
                  "basic-shot" "basic-shot" "basic-shot" "basic-shot"
                  "jump-shot" "jump-shot" "jump-shot" "jump-shot"
                  "layup" "layup" "layup" "layup"
                  "drive-and-dish" "drive-and-dish" "drive-and-dish" "drive-and-dish"
                  "post-up" "post-up" "post-up" "post-up"
                  "pick-and-roll" "pick-and-roll" "pick-and-roll" "pick-and-roll"
                  "alley-oop" "alley-oop"
                  "fast-break" "fast-break"]}
    {:id :balanced-attack
     :name "Balanced Attack"
     :description "Versatile offense that can score from anywhere."
     :card-slugs ["michael-jordan" "shaq" "mugsy-bogues"
                  "basic-shot" "basic-shot" "basic-shot" "basic-shot"
                  "jump-shot" "jump-shot" "jump-shot" "jump-shot"
                  "layup" "layup" "layup" "layup"
                  "drive-and-dish" "drive-and-dish" "drive-and-dish" "drive-and-dish"
                  "post-up" "post-up" "post-up" "post-up"
                  "pick-and-roll" "pick-and-roll" "pick-and-roll" "pick-and-roll"
                  "alley-oop" "alley-oop"
                  "fast-break" "fast-break"]}]})
