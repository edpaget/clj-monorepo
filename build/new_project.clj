(ns new-project
  "Script to create new projects in the monorepo"
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str])
  (:import
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)))

(def ^:private project-template-deps
  '{:paths ["src" "resources"]
    :deps {org.clojure/clojure {:mvn/version "1.12.2"}
           metosin/malli       {:mvn/version "0.19.1"}}
    :aliases
    {:dev      {:extra-paths ["dev"]
                :deps        {dev.weavejester/hashp {:mvn/version "0.4.0"}}}
     :test     {:extra-paths ["test"],
                :jvm-opts    ["-Duser.timezone=UTC"],
                :main-opts   ["-m" "cognitect.test-runner"]
                :exec-fn     cognitect.test-runner.api/test
                :extra-deps  {io.github.cognitect-labs/test-runner
                              {:git/tag "v0.5.1" :git/sha "dfb30dd"}}}
     :repl     {:extra-deps {nrepl/nrepl       {:mvn/version "1.3.0"}
                             cider/cider-nrepl {:mvn/version "0.50.2"}}
                :main-opts  ["-m" "nrepl.cmdline" "--middleware" "[cider.nrepl/cider-middleware]" "--port" "7888"]}
     :lint     {:extra-deps {clj-kondo/clj-kondo {:mvn/version "2025.07.28"}}
                :main-opts ["-m" "clj-kondo.main"]}
     :outdated {:extra-deps {com.github.liquidz/antq {:mvn/version "2.11.1276"}}
                :main-opts  ["-m" "antq.core"]}}})

(defn create-project-structure
  "Create the basic directory structure for a new project"
  [project-name]
  (let [project-dir project-name
        src-dir     (str project-dir "/src")
        test-dir    (str project-dir "/test")]
    (doseq [dir [project-dir src-dir test-dir]]
      (io/make-parents (str dir "/dummy"))
      (println (format "Created directory: %s" dir)))))

(defn create-deps-edn
  "Create deps.edn file for the new project"
  [project-name]
  (let [deps-file (str project-name "/deps.edn")]
    (binding [*print-namespace-maps* false]
      (spit deps-file (with-out-str (pprint/pprint project-template-deps))))
    (println (format "Created: %s" deps-file))))

(defn create-config-symlinks
  "Create symlinks to shared configuration files"
  [project-name]
  (let [root-dir         (System/getProperty "user.dir")
        project-dir      (io/file project-name)
        clj-kondo-link   (io/file project-dir ".clj-kondo")
        cljfmt-link      (io/file project-dir ".cljfmt.edn")
        clj-kondo-target (io/file root-dir ".clj-kondo")
        cljfmt-target    (io/file root-dir ".cljfmt.edn")]

    ;; Create .clj-kondo symlink
    (when (.exists clj-kondo-target)
      (.delete clj-kondo-link)
      (Files/createSymbolicLink
       (.toPath clj-kondo-link)
       (.toPath (io/file ".." ".clj-kondo"))
       (into-array FileAttribute []))
      (println (format "Created symlink: %s/.clj-kondo -> ../.clj-kondo" project-name)))

    ;; Create .cljfmt.edn symlink
    (when (.exists cljfmt-target)
      (.delete cljfmt-link)
      (Files/createSymbolicLink
       (.toPath cljfmt-link)
       (.toPath (io/file ".." ".cljfmt.edn"))
       (into-array FileAttribute []))
      (println (format "Created symlink: %s/.cljfmt.edn -> ../.cljfmt.edn" project-name)))))

(defn create-readme
  "Create README.md file for the project"
  [project-name]
  (let [readme-file (format "%s/README.md" project-name)
        title       (str/replace project-name #"-" " ")
        title       (str/join " " (map str/capitalize (str/split title #" ")))]
    (spit readme-file (format "# %s\n\nDescription of the %s library/application.\n\n## Usage\n\n```clojure\n(require '[%s.core :as %s])\n\n(%s/hello \"World\")\n;; => \"Hello, World!\"\n```\n\n## Development\n\n```bash\n# Run tests\nclojure -X:test\n\n# Start REPL\nclojure -M:repl\n\n# Lint code\nclojure -M:lint\n```\n"
                              title project-name project-name (first (str/split project-name #"-")) (first (str/split project-name #"-"))))
    (println (format "Created: %s" readme-file))))

(defn create-namespace-files
  "Create initial namespace files"
  [project-name]
  (let [namespace-name (-> (str/replace project-name #"-" "_")
                           (str/replace #"\." "/"))
        src-file       (format "%s/src/%s/core.clj" project-name namespace-name)
        test-file      (format "%s/test/%s/core_test.clj" project-name namespace-name)]

    ;; Create main namespace
    (io/make-parents src-file)
    (spit src-file (format "(ns %s.core\n  \"Core functionality for %s\")\n\n(defn hello\n  \"A simple hello function\"\n  [name]\n  (str \"Hello, \" name \"!\"))\n"
                           project-name project-name))
    (println (format "Created: %s" src-file))

    ;; Create test namespace
    (io/make-parents test-file)
    (spit test-file (format "(ns %s.core-test\n  (:require\n   [clojure.test :refer [deftest is testing]]\n   [%s.core :as core]))\n\n(deftest hello-test\n  (testing \"hello function\"\n    (is (= \"Hello, World!\" (core/hello \"World\")))))\n"
                            project-name namespace-name))
    (println (format "Created: %s" test-file))))

(defn new-project
  "Create a new project in the monorepo"
  [project-name]
  (when (str/blank? project-name)
    (throw (ex-info "Project name cannot be blank" {})))

  (when (.exists (io/file project-name))
    (throw (ex-info (format "Project directory %s already exists" project-name) {})))

  (println (format "Creating new project: %s" project-name))
  (create-project-structure project-name)
  (create-deps-edn project-name)
  (create-config-symlinks project-name)
  (create-readme project-name)
  (create-namespace-files project-name)
  (println (format "Project %s created successfully!" project-name)))

(defn -main
  "Main entry point for the script"
  [& args]
  (if-let [project-name (first args)]
    (new-project project-name)
    (println "Usage: clojure -M build/new_project.clj <project-name>")))
