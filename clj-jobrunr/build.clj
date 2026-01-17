(ns build
  "Build script for clj-jobrunr.

  Provides tasks for building JARs for distribution. No AOT compilation
  is required - the library uses deftype classes that are dynamically
  generated at runtime.

  Usage:
    clojure -T:build jar    ; Build a JAR for distribution
    clojure -T:build clean  ; Clean build artifacts"
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.github.your-org/clj-jobrunr)
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean
  "Delete build artifacts."
  [_]
  (b/delete {:path "target"}))

(defn jar
  "Build a JAR file for distribution.

  The JAR includes all source files. No AOT compilation is needed because
  clj-jobrunr uses deftype classes that are dynamically generated when the
  namespace is loaded."
  [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn uber
  "Build an uberjar with all dependencies."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file (str "target/" (name lib) "-" version "-standalone.jar")
           :basis basis}))
