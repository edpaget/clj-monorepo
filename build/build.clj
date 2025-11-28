(ns build
  "Build utilities for the Clojure monorepo"
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(defn find-projects
  "Find all project directories containing deps.edn files"
  []
  (let [result (shell/sh "find" "." "-name" "deps.edn" "-not" "-path" "./deps.edn")]
    (when (zero? (:exit result))
      (->> (:out result)
           str/split-lines
           (map #(-> % (str/replace #"/deps\.edn$" "") (str/replace #"^\./", "")))
           (filter seq)))))

(defn run-in-project
  "Run a command in a specific project directory"
  [project-dir cmd & args]
  (let [full-args (concat [cmd] args)
        result    (apply shell/sh (concat full-args [:dir project-dir]))]
    (println (format "[%s] %s" project-dir (str/join " " full-args)))
    (when (seq (:out result))
      (println (:out result)))
    (when (seq (:err result))
      (binding [*out* *err*]
        (println (:err result))))
    result))

(defn test-all
  "Run tests for all projects using top-level aliases"
  [& _]
  (println "Running tests for all projects...")
  (let [result (shell/sh "clojure" "-X:test-all")]
    (when (seq (:out result))
      (println (:out result)))
    (when (seq (:err result))
      (binding [*out* *err*]
        (println (:err result))))
    result))

(defn test-project
  "Run tests for a specific project"
  [project]
  (println (format "Testing %s..." project))
  (let [alias  (keyword (str project "-test"))
        result (shell/sh "clojure" (str "-X" alias))]
    (when (seq (:out result))
      (println (:out result)))
    (when (seq (:err result))
      (binding [*out* *err*]
        (println (:err result))))
    result))

(defn check-all
  "Run syntax and type checking for all projects"
  []
  (let [projects (find-projects)]
    (if (empty? projects)
      (println "No projects found")
      (doseq [project projects]
        (println (format "Checking %s..." project))
        (run-in-project project "clojure" "-M:lint")))))

(defn lint-all
  "Run linting for all projects"
  []
  (let [projects (find-projects)]
    (if (empty? projects)
      (println "No projects found")
      (doseq [project projects]
        (println (format "Linting %s..." project))
        (run-in-project project "clojure" "-M:lint")))))

(defn clean-all
  "Clean build artifacts for all projects"
  []
  (let [projects (find-projects)]
    (if (empty? projects)
      (println "No projects found")
      (doseq [project projects]
        (println (format "Cleaning %s..." project))
        (run-in-project project "rm" "-rf" "target" ".cpcache")))))

(defn deps-all
  "Download dependencies for all projects"
  []
  (let [projects (find-projects)]
    (if (empty? projects)
      (println "No projects found")
      (doseq [project projects]
        (println (format "Downloading deps for %s..." project))
        (run-in-project project "clojure" "-P")))))

(defn repl
  "Start a REPL with all projects available"
  [& _]
  (println "Starting REPL with all projects...")
  (shell/sh "clojure" "-M:dev-all:repl"))

(defn outdated
  "Check for outdated dependencies across all projects"
  []
  (let [projects (find-projects)]
    (if (empty? projects)
      (println "No projects found")
      (doseq [project projects]
        (println (format "Checking outdated deps for %s..." project))
        (run-in-project project "clojure" "-M:outdated")))))

(def projects
  "Configuration for building uberjars."
  {:bashketball-editor-api
   {:basis-aliases [:bashketball-editor-api]
    :main          'bashketball-editor-api.server
    :compile-ns    '[bashketball-editor-api.server
                     clojure.tools.logging
                     clojure.tools.logging.impl]
    :src-dirs      ["bashketball-editor-api/src"
                    "bashketball-editor-api/resources"
                    "db/src"
                    "graphql-server/src"
                    "authn/src"
                    "oidc/src"
                    "oidc-github/src"
                    "exclusive-initializer/src"]
    :target-dir    "bashketball-editor-api/target"
    :jar-file      "bashketball-editor-api/target/bashketball-editor-api.jar"}})

(defn uberjar
  "Build an uberjar for a project.

  Usage: clojure -T:build uberjar :project :bashketball-editor-api"
  [{:keys [project]}]
  (let [{:keys [basis-aliases main compile-ns src-dirs target-dir jar-file]} (get projects project)
        namespaces-to-compile                                                (or compile-ns [main])]
    (when-not jar-file
      (throw (ex-info (str "Unknown project: " project) {:project project
                                                         :available (keys projects)})))
    (println (str "Building uberjar for " (name project) "..."))
    (let [basis     (b/create-basis {:aliases basis-aliases})
          class-dir (str target-dir "/classes")]
      (b/delete {:path target-dir})
      (println "Copying sources...")
      (b/copy-dir {:src-dirs   src-dirs
                   :target-dir class-dir})
      (println "Compiling namespaces:" namespaces-to-compile)
      (b/compile-clj {:basis     basis
                      :ns-compile namespaces-to-compile
                      :class-dir class-dir})
      (println "Packaging uberjar...")
      (b/uber {:class-dir class-dir
               :uber-file jar-file
               :basis     basis
               :main      main})
      (println (str "Built: " jar-file)))))
