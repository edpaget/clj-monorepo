(ns bashketball-editor-api.git.sets-test
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.git.sets :as git-sets]
   [bashketball-editor-api.models.protocol :as proto]
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [malli.core :as m])
  (:import
   [java.time Instant]))

(def ^:dynamic *test-repo* nil)
(def ^:dynamic *test-repo-path* nil)

(defn with-temp-repo [f]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                          (str "test-sets-repo-" (System/currentTimeMillis)))]
    (.mkdirs temp-dir)
    (binding [*test-repo-path* (.getAbsolutePath temp-dir)
              *test-repo*      (git-repo/create-git-repo {:repo-path (.getAbsolutePath temp-dir)
                                                          :writer? true})]
      (try
        (f)
        (finally
          (doseq [file (reverse (file-seq temp-dir))]
            (.delete file)))))))

(use-fixtures :each with-temp-repo)

(deftest card-set-schema-test
  (testing "validates a complete card set"
    (let [card-set {:slug "test-set"
                    :name "Test Set"
                    :description "A test card set"
                    :created-at (java.time.Instant/now)
                    :updated-at (java.time.Instant/now)}]
      (is (m/validate git-sets/CardSet card-set))))

  (testing "validates a minimal card set with slug and name"
    (let [card-set {:slug "test-set" :name "Test Set"}]
      (is (m/validate git-sets/CardSet card-set))))

  (testing "rejects card set without slug"
    (let [card-set {:name "Test Set"}]
      (is (not (m/validate git-sets/CardSet card-set)))))

  (testing "rejects card set without name"
    (let [card-set {:slug "test-set"}]
      (is (not (m/validate git-sets/CardSet card-set))))))

(deftest create-set-repository-test
  (testing "creates a SetRepository record"
    (let [repo (git-sets/create-set-repository *test-repo*)]
      (is (instance? bashketball_editor_api.git.sets.SetRepository repo))
      (is (= *test-repo* (:git-repo repo))))))

(deftest find-by-test
  (testing "returns nil when set not found"
    (let [set-repo (git-sets/create-set-repository *test-repo*)]
      (is (nil? (proto/find-by set-repo {:slug "nonexistent-set"})))))

  (testing "requires slug"
    (let [set-repo (git-sets/create-set-repository *test-repo*)]
      (is (nil? (proto/find-by set-repo {}))))))

(deftest find-all-test
  (testing "returns empty vector when no sets"
    (let [set-repo (git-sets/create-set-repository *test-repo*)]
      (is (= [] (proto/find-all set-repo {}))))))

(deftest read-only-test
  (testing "throws on create when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          set-repo       (git-sets/create-set-repository read-only-repo)
          set-data       {:slug "test-set" :name "Test Set"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                            (proto/create! set-repo set-data)))))

  (testing "throws on update when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          set-repo       (git-sets/create-set-repository read-only-repo)
          set-data       {:name "Test Set"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                            (proto/update! set-repo "test-set" set-data))))))

(deftest git-timestamps-find-by-test
  (testing "find-by returns git timestamps for sets without timestamps in EDN"
    (let [jgit-repo (git/git-init :dir *test-repo-path*)
          set-repo  (git-sets/create-set-repository *test-repo*)
          set-edn   "{:slug \"test-set\" :name \"Test Set\"}"]
      (git-repo/write-file *test-repo* "test-set/metadata.edn" set-edn)
      (git/git-add jgit-repo ".")
      (git/git-commit jgit-repo "Add test set" :name "Test" :email "test@test.com")

      (let [card-set (proto/find-by set-repo {:slug "test-set"})]
        (is (some? card-set))
        (is (= "test-set" (:slug card-set)))
        (is (instance? Instant (:created-at card-set)))
        (is (instance? Instant (:updated-at card-set)))))))

(deftest git-timestamps-find-all-test
  (testing "find-all returns git timestamps for sets without timestamps in EDN"
    (let [jgit-repo (git/git-init :dir *test-repo-path*)
          set-repo  (git-sets/create-set-repository *test-repo*)
          set1-edn  "{:slug \"set-1\" :name \"Set 1\"}"
          set2-edn  "{:slug \"set-2\" :name \"Set 2\"}"]
      (git-repo/write-file *test-repo* "set-1/metadata.edn" set1-edn)
      (git-repo/write-file *test-repo* "set-2/metadata.edn" set2-edn)
      (git/git-add jgit-repo ".")
      (git/git-commit jgit-repo "Add sets" :name "Test" :email "test@test.com")

      (let [sets (proto/find-all set-repo {})]
        (is (= 2 (count sets)))
        (doseq [card-set sets]
          (is (instance? Instant (:created-at card-set)))
          (is (instance? Instant (:updated-at card-set))))))))
