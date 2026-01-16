(ns build
  "Build script for clj-jobrunr.

  Provides tasks for:
  - AOT compilation of the Java bridge class
  - Building a JAR for distribution

  Usage:
    clojure -T:build compile-bridge  ; AOT compile the bridge class
    clojure -T:build jar             ; Build a JAR with AOT classes
    clojure -T:build clean           ; Clean build artifacts"
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

(defn compile-bridge
  "AOT compile the Java bridge class.

  This must be run before using clj-jobrunr with JobRunr, as JobRunr
  requires actual Java classes for serialization."
  [_]
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile ['clj-jobrunr.java-bridge]}))

(defn compile-all
  "AOT compile all namespaces.

  Use this for full AOT compilation if needed."
  [_]
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir}))

(defn jar
  "Build a JAR file with AOT-compiled classes.

  The JAR includes the compiled Java bridge class and all source files."
  [_]
  (clean nil)
  (compile-bridge nil)
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
  (compile-bridge nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file (str "target/" (name lib) "-" version "-standalone.jar")
           :basis basis}))
