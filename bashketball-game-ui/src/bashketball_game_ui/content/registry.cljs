(ns bashketball-game-ui.content.registry
  "Registry of all static markdown content.

  Content is processed at compile time and embedded in the build."
  (:require
   [bashketball-game-ui.content.macros :refer [inline-content-registry]]))

(def content
  "Map of all content keyed by [category slug]."
  (inline-content-registry
   ["rules/introduction.md"
    "rules/core-concepts.md"
    "rules/zone-of-control.md"
    "rules/skill-actions.md"
    "rules/standard-actions.md"
    "rules/injuries.md"
    "rules/card-types.md"
    "rules/symbols-keywords.md"
    "rules/the-court.md"
    "rules/team-construction.md"
    "rules/game-flow.md"]))

(defn get-content
  "Retrieves content by category and slug."
  [category slug]
  (get content [category slug]))

(defn list-by-category
  "Returns all content entries for a given category, sorted by frontmatter order."
  [category]
  (->> content
       vals
       (filter #(= category (:category %)))
       (sort-by #(get-in % [:frontmatter :order] 999))))
