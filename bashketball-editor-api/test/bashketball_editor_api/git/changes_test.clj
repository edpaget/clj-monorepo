(ns bashketball-editor-api.git.changes-test
  "Tests for ChangesRepository."
  (:require
   [bashketball-editor-api.git.changes :as changes]
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:dynamic *test-repo* nil)
(def ^:dynamic *test-repo-path* nil)

(defn with-temp-repo [f]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                          (str "test-changes-repo-" (System/currentTimeMillis)))]
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

(deftest find-all-no-git-repo-test
  (testing "returns empty status for non-git directory"
    (let [changes-repo (changes/create-changes-repository *test-repo*)
          status       (proto/find-all changes-repo {})]
      (is (= [] (:added status)))
      (is (= [] (:modified status)))
      (is (= [] (:deleted status)))
      (is (= [] (:untracked status)))
      (is (false? (:is-dirty status))))))

(deftest find-all-clean-repo-test
  (testing "returns clean status for initialized empty repo"
    (git/git-init :dir *test-repo-path*)

    (let [changes-repo (changes/create-changes-repository *test-repo*)
          status       (proto/find-all changes-repo {})]
      (is (= [] (:added status)))
      (is (= [] (:modified status)))
      (is (= [] (:deleted status)))
      (is (= [] (:untracked status)))
      (is (nil? (:is-dirty status))))))

(deftest find-all-staged-files-test
  (testing "returns added files when staged"
    (git/git-init :dir *test-repo-path*)
    ;; write-file auto-stages
    (git-repo/write-file *test-repo* "new-file.txt" "content")

    (let [changes-repo (changes/create-changes-repository *test-repo*)
          status       (proto/find-all changes-repo {})]
      (is (= ["new-file.txt"] (:added status)))
      (is (= [] (:modified status)))
      (is (= [] (:deleted status)))
      (is (some? (:is-dirty status))))))

(deftest find-all-modified-files-test
  (testing "returns modified files"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "file.txt" "initial")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial" :name "Test" :email "test@test.com"))

    ;; Modify the file
    (git-repo/write-file *test-repo* "file.txt" "modified")

    (let [changes-repo (changes/create-changes-repository *test-repo*)
          status       (proto/find-all changes-repo {})]
      (is (= ["file.txt"] (:modified status)))
      (is (some? (:is-dirty status))))))

(deftest find-all-deleted-files-test
  (testing "returns deleted files"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "file.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Add file" :name "Test" :email "test@test.com"))

    ;; Delete the file
    (git-repo/delete-file *test-repo* "file.txt")

    (let [changes-repo (changes/create-changes-repository *test-repo*)
          status       (proto/find-all changes-repo {})]
      (is (= ["file.txt"] (:deleted status)))
      (is (some? (:is-dirty status))))))

(deftest delete-discards-changes-test
  (testing "delete! discards all uncommitted changes"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "committed.txt" "initial")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial" :name "Test" :email "test@test.com"))

    ;; Make some changes
    (git-repo/write-file *test-repo* "committed.txt" "modified")
    (git-repo/write-file *test-repo* "new-staged.txt" "staged content")

    ;; Create an untracked file manually (bypassing auto-stage)
    (spit (io/file *test-repo-path* "untracked.txt") "untracked content")

    ;; Verify we have changes
    (let [changes-repo (changes/create-changes-repository *test-repo*)
          before       (proto/find-all changes-repo {})]
      (is (some? (:is-dirty before))))

    ;; Discard all changes
    (let [changes-repo (changes/create-changes-repository *test-repo*)
          result       (proto/delete! changes-repo :all)]
      (is (= "success" (:status result))))

    ;; Verify clean state
    (let [changes-repo (changes/create-changes-repository *test-repo*)
          after        (proto/find-all changes-repo {})]
      (is (= [] (:added after)))
      (is (= [] (:modified after)))
      (is (= [] (:deleted after)))
      (is (= [] (:untracked after)))
      (is (nil? (:is-dirty after))))

    ;; Verify committed file is restored
    (is (= "initial" (git-repo/read-file *test-repo* "committed.txt")))))

(deftest delete-read-only-test
  (testing "delete! throws for read-only repo"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "file.txt" "content")

    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          changes-repo   (changes/create-changes-repository read-only-repo)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"read-only"
                            (proto/delete! changes-repo :all))))))

(deftest delete-no-git-repo-test
  (testing "delete! returns error for non-git directory"
    (let [changes-repo (changes/create-changes-repository *test-repo*)
          result       (proto/delete! changes-repo :all)]
      (is (= "error" (:status result)))
      (is (re-find #"[Nn]ot a git" (:message result))))))

(deftest find-by-not-implemented-test
  (testing "find-by throws not implemented"
    (let [changes-repo (changes/create-changes-repository *test-repo*)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Not implemented"
                            (proto/find-by changes-repo {}))))))

(deftest create-not-implemented-test
  (testing "create! throws not implemented"
    (let [changes-repo (changes/create-changes-repository *test-repo*)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Not implemented"
                            (proto/create! changes-repo {}))))))

(deftest update-not-implemented-test
  (testing "update! throws not implemented"
    (let [changes-repo (changes/create-changes-repository *test-repo*)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Not implemented"
                            (proto/update! changes-repo {} {}))))))
