(ns bashketball-game-ui.content.macros
  "Compile-time macros for processing markdown content.

  Provides macros that read markdown files at compile time, parse YAML
  frontmatter, and convert markdown to HTML using flexmark-java. The
  resulting HTML and metadata are embedded directly into the compiled
  ClojureScript."
  (:require
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [com.vladsch.flexmark.ast Heading]
   [com.vladsch.flexmark.ext.anchorlink AnchorLinkExtension]
   [com.vladsch.flexmark.ext.tables TablesExtension]
   [com.vladsch.flexmark.html HtmlRenderer]
   [com.vladsch.flexmark.parser Parser]
   [com.vladsch.flexmark.util.data MutableDataSet]
   [java.util ArrayList]))

(def ^:private options
  (doto (MutableDataSet.)
    (.set Parser/EXTENSIONS [(AnchorLinkExtension/create)
                             (TablesExtension/create)])
    (.set AnchorLinkExtension/ANCHORLINKS_WRAP_TEXT (Boolean. false))
    (.set AnchorLinkExtension/ANCHORLINKS_ANCHOR_CLASS "anchor")))

(def ^:private parser
  (-> options
      (Parser/builder)
      (.build)))

(def ^:private renderer
  (-> options
      (HtmlRenderer/builder)
      (.build)))

(defn- parse-frontmatter
  "Extracts YAML frontmatter and body from markdown content.

  Returns a map with `:frontmatter` (parsed YAML map) and `:body` (remaining markdown)."
  [content]
  (if (str/starts-with? content "---")
    (let [end-idx (str/index-of content "---" 3)]
      (if end-idx
        (let [yaml-str (subs content 3 end-idx)
              body     (str/trim (subs content (+ end-idx 3)))]
          {:frontmatter (yaml/parse-string yaml-str)
           :body body})
        {:frontmatter {}
         :body content}))
    {:frontmatter {}
     :body content}))

(defn- text->slug
  "Converts heading text to URL-friendly slug."
  [text]
  (-> text
      str/lower-case
      (str/replace #"[^a-z0-9\s-]" "")
      str/trim
      (str/replace #"\s+" "-")))

(defn- extract-headings
  "Extracts heading metadata from parsed document for ToC generation."
  [document]
  (let [headings (ArrayList.)]
    (doseq [node (iterator-seq (.iterator (.getDescendants document)))]
      (when (instance? Heading node)
        (let [level (.getLevel node)
              text  (str (.getText node))
              id    (text->slug text)]
          (when (and (>= level 2) (<= level 3))
            (.add headings {:level level :text text :id id})))))
    (vec headings)))

(defn- add-heading-ids
  "Adds id attributes to h2 and h3 elements in HTML."
  [html toc]
  (reduce (fn [h {:keys [level text id]}]
            (let [tag         (str "h" level)
                  pattern     (re-pattern (str "(?i)<" tag ">([^<]*" (java.util.regex.Pattern/quote text) "[^<]*)</" tag ">"))
                  replacement (str "<" tag " id=\"" id "\">$1</" tag ">")]
              (str/replace-first h pattern replacement)))
          html
          toc))

(defn- markdown->html+toc
  "Converts markdown string to HTML and extracts ToC metadata."
  [markdown]
  (let [document (.parse parser markdown)
        raw-html (.render renderer document)
        toc      (extract-headings document)
        html     (add-heading-ids raw-html toc)]
    {:html html :toc toc}))

(defmacro inline-content-registry
  "Generates a registry map of all content at compile time.

  Takes a vector of paths (relative to classpath) and returns a map keyed by
  `[category slug]`. Each entry contains `:slug`, `:category`, `:html`, `:toc`,
  and `:frontmatter`."
  [paths]
  (let [entries (for [path paths]
                  (let [resource (io/resource path)]
                    (when-not resource
                      (throw (ex-info (str "Markdown file not found: " path) {:path path})))
                    (let [content                    (slurp resource)
                          {:keys [frontmatter body]} (parse-frontmatter content)
                          {:keys [html toc]}         (markdown->html+toc body)
                          path-parts                 (str/split path #"/")
                          category                   (first path-parts)
                          filename                   (last path-parts)
                          slug                       (str/replace filename #"\.md$" "")]
                      [[category slug]
                       {:slug slug
                        :category category
                        :html html
                        :toc toc
                        :frontmatter frontmatter}])))]
    (into {} entries)))
