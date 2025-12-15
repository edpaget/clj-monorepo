(ns bashketball-game-ui.content.registry-test
  "Tests for content registry macro and functions."
  (:require
   [bashketball-game-ui.content.macros :refer [inline-content-registry]]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]))

(def test-content
  "Test registry using fixture markdown files."
  (inline-content-registry
   ["test-category/basic.md"
    "test-category/advanced.md"
    "test-category/no-frontmatter.md"]))

(defn get-test-content
  "Retrieves test content by category and slug."
  [category slug]
  (get test-content [category slug]))

(defn list-test-by-category
  "Returns all test content entries for a given category, sorted by order."
  [category]
  (->> test-content
       vals
       (filter #(= category (:category %)))
       (sort-by #(get-in % [:frontmatter :order] 999))))

(t/deftest inline-content-registry-creates-map-test
  (t/is (map? test-content))
  (t/is (= 3 (count test-content))))

(t/deftest content-keyed-by-category-and-slug-test
  (t/is (contains? test-content ["test-category" "basic"]))
  (t/is (contains? test-content ["test-category" "advanced"]))
  (t/is (contains? test-content ["test-category" "no-frontmatter"])))

(t/deftest content-entry-has-required-keys-test
  (let [entry (get-test-content "test-category" "basic")]
    (t/is (contains? entry :slug))
    (t/is (contains? entry :category))
    (t/is (contains? entry :html))
    (t/is (contains? entry :toc))
    (t/is (contains? entry :frontmatter))))

(t/deftest slug-extracted-from-filename-test
  (let [entry (get-test-content "test-category" "basic")]
    (t/is (= "basic" (:slug entry)))))

(t/deftest category-extracted-from-path-test
  (let [entry (get-test-content "test-category" "basic")]
    (t/is (= "test-category" (:category entry)))))

(t/deftest frontmatter-parsed-correctly-test
  (let [entry (get-test-content "test-category" "basic")]
    (t/is (= "Basic Test Page" (get-in entry [:frontmatter :title])))
    (t/is (= "A simple test page" (get-in entry [:frontmatter :description])))
    (t/is (= 1 (get-in entry [:frontmatter :order])))))

(t/deftest custom-frontmatter-fields-preserved-test
  (let [entry (get-test-content "test-category" "advanced")]
    (t/is (= "custom value" (get-in entry [:frontmatter :custom_field])))))

(t/deftest missing-frontmatter-returns-empty-map-test
  (let [entry (get-test-content "test-category" "no-frontmatter")]
    (t/is (map? (:frontmatter entry)))
    (t/is (empty? (:frontmatter entry)))))

(t/deftest markdown-converted-to-html-test
  (let [entry (get-test-content "test-category" "basic")]
    (t/is (string? (:html entry)))
    (t/is (str/includes? (:html entry) "<h1>"))
    (t/is (str/includes? (:html entry) "<strong>bold</strong>"))
    (t/is (str/includes? (:html entry) "<em>italic</em>"))))

(t/deftest complex-markdown-converted-test
  (let [entry (get-test-content "test-category" "advanced")]
    (t/is (str/includes? (:html entry) "<h2>"))
    (t/is (str/includes? (:html entry) "<a href=\"https://example.com\">"))
    (t/is (str/includes? (:html entry) "<li>"))))

(t/deftest get-test-content-returns-nil-for-missing-test
  (t/is (nil? (get-test-content "nonexistent" "page")))
  (t/is (nil? (get-test-content "test-category" "nonexistent"))))

(t/deftest list-test-by-category-returns-filtered-entries-test
  (let [entries (list-test-by-category "test-category")]
    (t/is (= 3 (count entries)))
    (t/is (every? #(= "test-category" (:category %)) entries))))

(t/deftest list-test-by-category-sorts-by-order-test
  (let [entries (list-test-by-category "test-category")
        slugs (mapv :slug entries)]
    (t/is (= "basic" (first slugs)))
    (t/is (= "advanced" (second slugs)))))

(t/deftest list-test-by-category-returns-empty-for-missing-test
  (t/is (empty? (list-test-by-category "nonexistent"))))

(t/deftest toc-is-vector-test
  (let [entry (get-test-content "test-category" "advanced")]
    (t/is (vector? (:toc entry)))))

(t/deftest toc-extracts-h2-headings-test
  (let [entry (get-test-content "test-category" "advanced")
        toc (:toc entry)]
    (t/is (some #(= "Section One" (:text %)) toc))
    (t/is (some #(= "Section Two" (:text %)) toc))))

(t/deftest toc-entries-have-required-keys-test
  (let [entry (get-test-content "test-category" "advanced")
        toc-entry (first (:toc entry))]
    (t/is (contains? toc-entry :level))
    (t/is (contains? toc-entry :text))
    (t/is (contains? toc-entry :id))))

(t/deftest toc-generates-slug-ids-test
  (let [entry (get-test-content "test-category" "advanced")
        toc (:toc entry)]
    (t/is (some #(= "section-one" (:id %)) toc))
    (t/is (some #(= "section-two" (:id %)) toc))))

(t/deftest html-contains-heading-ids-test
  (let [entry (get-test-content "test-category" "advanced")]
    (t/is (str/includes? (:html entry) "id=\"section-one\""))
    (t/is (str/includes? (:html entry) "id=\"section-two\""))))
