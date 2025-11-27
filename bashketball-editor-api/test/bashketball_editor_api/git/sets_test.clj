(ns bashketball-editor-api.git.sets-test
  (:require
   [bashketball-editor-api.context :as ctx]
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.git.sets :as git-sets]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [malli.core :as m]))

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

(def test-user-ctx
  {:name "Test User"
   :email "test@example.com"
   :github-token "test-token"})

(deftest card-set-schema-test
  (testing "validates a complete card set"
    (let [card-set {:slug "test-set"
                    :name "Test Set"
                    :description "A test card set"
                    :created-at (java.time.Instant/now)
                    :updated-at (java.time.Instant/now)}]
      (is (m/validate git-sets/CardSet card-set))))

  (testing "validates a minimal card set"
    (let [card-set {:name "Test Set"}]
      (is (m/validate git-sets/CardSet card-set))))

  (testing "rejects card set without name"
    (let [card-set {:slug "test-set"}]
      (is (not (m/validate git-sets/CardSet card-set))))))

(deftest create-set-repository-test
  (testing "creates a SetRepository record"
    (let [repo (git-sets/create-set-repository *test-repo* ctx/current-user-context)]
      (is (instance? bashketball_editor_api.git.sets.SetRepository repo))
      (is (= *test-repo* (:git-repo repo))))))

(deftest find-by-test
  (testing "returns nil when set not found"
    (let [set-repo (git-sets/create-set-repository *test-repo* ctx/current-user-context)]
      (is (nil? (proto/find-by set-repo {:slug "nonexistent-set"})))))

  (testing "requires slug"
    (let [set-repo (git-sets/create-set-repository *test-repo* ctx/current-user-context)]
      (is (nil? (proto/find-by set-repo {}))))))

(deftest find-all-test
  (testing "returns empty vector when no sets"
    (let [set-repo (git-sets/create-set-repository *test-repo* ctx/current-user-context)]
      (is (= [] (proto/find-all set-repo {}))))))

(deftest read-only-test
  (testing "throws on create when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          set-repo       (git-sets/create-set-repository read-only-repo ctx/current-user-context)
          set-data       {:name "Test Set"}]
      (binding [ctx/*user-context* test-user-ctx]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                              (proto/create! set-repo set-data))))))

  (testing "throws on update when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          set-repo       (git-sets/create-set-repository read-only-repo ctx/current-user-context)
          set-data       {:name "Test Set"}]
      (binding [ctx/*user-context* test-user-ctx]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                              (proto/update! set-repo "test-set" set-data)))))))

(deftest user-context-required-test
  (testing "throws when user context missing on create"
    (let [set-repo (git-sets/create-set-repository *test-repo* ctx/current-user-context)
          set-data {:name "Test Set"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No user context available"
                            (proto/create! set-repo set-data)))))

  (testing "throws when user context missing on update"
    (let [set-repo (git-sets/create-set-repository *test-repo* ctx/current-user-context)
          set-data {:name "Test Set"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No user context available"
                            (proto/update! set-repo "test-set" set-data))))))
