(ns bashketball-game-ui.content.renderer-test
  "Tests for content renderer components."
  (:require
   [bashketball-game-ui.content.renderer :refer [prose content-page table-of-contents]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-html "<h1>Test Heading</h1><p>Test paragraph with <strong>bold</strong> text.</p>")

(def sample-toc
  [{:level 2 :text "Section One" :id "section-one"}
   {:level 2 :text "Section Two" :id "section-two"}
   {:level 3 :text "Subsection" :id "subsection"}])

(def sample-content
  {:slug "test-page"
   :category "test"
   :html sample-html
   :toc sample-toc
   :frontmatter {:title "Test Title"
                 :description "Test description text"}})

(def content-without-title
  {:slug "no-title"
   :category "test"
   :html "<p>Content without title</p>"
   :toc []
   :frontmatter {:description "Only description"}})

(def content-without-metadata
  {:slug "no-meta"
   :category "test"
   :html "<p>Minimal content</p>"
   :toc []
   :frontmatter {}})

(t/deftest prose-renders-html-content-test
  (uix-tlr/render ($ prose {:html sample-html}))
  (t/is (some? (screen/get-by-role "article"))))

(t/deftest prose-contains-rendered-html-test
  (uix-tlr/render ($ prose {:html sample-html}))
  (t/is (some? (screen/get-by-text "Test Heading")))
  (t/is (some? (screen/get-by-text "bold" {:exact false}))))

(t/deftest prose-applies-prose-class-test
  (uix-tlr/render ($ prose {:html sample-html}))
  (let [article (screen/get-by-role "article")]
    (t/is (.contains (.-classList article) "prose"))))

(t/deftest prose-accepts-additional-classes-test
  (uix-tlr/render ($ prose {:html sample-html :class "custom-class"}))
  (let [article (screen/get-by-role "article")]
    (t/is (.contains (.-classList article) "custom-class"))))

(t/deftest content-page-renders-title-test
  (uix-tlr/render ($ content-page {:content sample-content}))
  (t/is (some? (screen/get-by-role "heading" {:name "Test Title"}))))

(t/deftest content-page-renders-description-test
  (uix-tlr/render ($ content-page {:content sample-content}))
  (t/is (some? (screen/get-by-text "Test description text"))))

(t/deftest content-page-renders-prose-content-test
  (uix-tlr/render ($ content-page {:content sample-content}))
  (t/is (some? (screen/get-by-text "Test paragraph" {:exact false}))))

(t/deftest content-page-handles-missing-title-test
  (uix-tlr/render ($ content-page {:content content-without-title}))
  (t/is (nil? (screen/query-by-role "heading" {:name "Test Title"})))
  (t/is (some? (screen/get-by-text "Only description"))))

(t/deftest content-page-handles-empty-frontmatter-test
  (uix-tlr/render ($ content-page {:content content-without-metadata}))
  (t/is (some? (screen/get-by-text "Minimal content"))))

(t/deftest table-of-contents-renders-heading-test
  (uix-tlr/render ($ table-of-contents {:toc sample-toc}))
  (t/is (some? (screen/get-by-text "On this page"))))

(t/deftest table-of-contents-renders-links-test
  (uix-tlr/render ($ table-of-contents {:toc sample-toc}))
  (t/is (some? (screen/get-by-text "Section One")))
  (t/is (some? (screen/get-by-text "Section Two")))
  (t/is (some? (screen/get-by-text "Subsection"))))

(t/deftest table-of-contents-links-have-correct-href-test
  (uix-tlr/render ($ table-of-contents {:toc sample-toc}))
  (let [link (screen/get-by-text "Section One")]
    (t/is (= "#section-one" (.getAttribute link "href")))))

(t/deftest table-of-contents-returns-nil-for-empty-toc-test
  (uix-tlr/render ($ :div ($ table-of-contents {:toc []})))
  (t/is (nil? (screen/query-by-text "On this page"))))
