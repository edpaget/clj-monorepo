(ns build
  "Build utilities for the Clojure monorepo"
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as str]))

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
        result (apply shell/sh (concat full-args [:dir project-dir]))]
    (println (format "[%s] %s" project-dir (str/join " " full-args)))
    (when (seq (:out result))
      (println (:out result)))
    (when (seq (:err result))
      (binding [*out* *err*]
        (println (:err result))))
    result))

(defn test-all
  "Run tests for all projects"
  [& {:keys [parallel] :or {parallel false}}]
  (let [projects (find-projects)]
    (if (empty? projects)
      (println "No projects found")
      (doseq [project projects]
        (println (format "Testing %s..." project))
        (run-in-project project "clojure" "-X:test")))))

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
  []
  (println "Starting REPL with all projects...")
  (shell/sh "clojure" "-M:repl"))

(defn outdated
  "Check for outdated dependencies across all projects"
  []
  (let [projects (find-projects)]
    (if (empty? projects)
      (println "No projects found")
      (doseq [project projects]
        (println (format "Checking outdated deps for %s..." project))
        (run-in-project project "clojure" "-M:outdated")))))
