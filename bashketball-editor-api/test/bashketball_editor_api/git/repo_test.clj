(ns bashketball-editor-api.git.repo-test
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
   [java.time Instant]))

(def ^:dynamic *test-repo-path* nil)

(defn with-temp-repo [f]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                          (str "test-git-repo-" (System/currentTimeMillis)))]
    (.mkdirs temp-dir)
    (binding [*test-repo-path* (.getAbsolutePath temp-dir)]
      (try
        (f)
        (finally
          (doseq [file (reverse (file-seq temp-dir))]
            (.delete file)))))))

(use-fixtures :each with-temp-repo)

(deftest create-git-repo-test
  (testing "creates a GitRepo record"
    (let [config {:repo-path *test-repo-path*
                  :remote-url "https://github.com/test/repo.git"
                  :branch "main"
                  :writer? true}
          repo   (git-repo/create-git-repo config)]
      (is (= *test-repo-path* (:repo-path repo)))
      (is (= "https://github.com/test/repo.git" (:remote-url repo)))
      (is (= "main" (:branch repo)))
      (is (true? (:writer? repo))))))

(deftest read-write-file-test
  (testing "writes and reads a file"
    (let [repo    (git-repo/create-git-repo {:repo-path *test-repo-path*
                                             :writer? true})
          content "Hello, World!"]
      (git-repo/write-file repo "test.txt" content)
      (is (= content (git-repo/read-file repo "test.txt")))))

  (testing "returns nil for non-existent file"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      (is (nil? (git-repo/read-file repo "nonexistent.txt")))))

  (testing "creates parent directories"
    (let [repo    (git-repo/create-git-repo {:repo-path *test-repo-path*
                                             :writer? true})
          content "Nested content"]
      (git-repo/write-file repo "deep/nested/file.txt" content)
      (is (= content (git-repo/read-file repo "deep/nested/file.txt"))))))

(deftest delete-file-test
  (testing "deletes an existing file"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      (git-repo/write-file repo "to-delete.txt" "content")
      (is (some? (git-repo/read-file repo "to-delete.txt")))
      (git-repo/delete-file repo "to-delete.txt")
      (is (nil? (git-repo/read-file repo "to-delete.txt")))))

  (testing "returns nil for non-existent file"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      (is (nil? (git-repo/delete-file repo "nonexistent.txt"))))))

(deftest list-files-test
  (testing "lists files in directory"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      (git-repo/write-file repo "cards/set1/card1.edn" "{:id 1}")
      (git-repo/write-file repo "cards/set1/card2.edn" "{:id 2}")
      (git-repo/write-file repo "cards/set1/readme.txt" "info")
      (let [all-files (git-repo/list-files repo "cards/set1")
            edn-files (git-repo/list-files repo "cards/set1" ".edn")]
        (is (= 3 (count all-files)))
        (is (= 2 (count edn-files))))))

  (testing "returns nil for non-existent directory"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      (is (nil? (git-repo/list-files repo "nonexistent"))))))

(deftest closeable-test
  (testing "GitRepo implements Closeable"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      (is (instance? java.io.Closeable repo))
      (.close repo))))

(deftest file-timestamps-no-git-history-test
  (testing "returns nil for file with no git history"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      ;; Write a file but don't commit it (no git repo initialized)
      (git-repo/write-file repo "untracked.txt" "content")
      (is (nil? (git-repo/file-timestamps repo "untracked.txt"))))))

(deftest file-timestamps-single-commit-test
  (testing "returns timestamps from git history"
    (let [repo      (git-repo/create-git-repo {:repo-path *test-repo-path*
                                               :writer? true})
          jgit-repo (git/git-init :dir *test-repo-path*)]
      ;; Create and commit a file
      (git-repo/write-file repo "tracked.txt" "initial content")
      (git/git-add jgit-repo ".")
      (git/git-commit jgit-repo "Initial commit" :name "Test" :email "test@test.com")

      (let [timestamps (git-repo/file-timestamps repo "tracked.txt")]
        (is (some? timestamps))
        (is (instance? Instant (:created-at timestamps)))
        (is (instance? Instant (:updated-at timestamps)))
        ;; Both should be the same for a single commit
        (is (= (:created-at timestamps) (:updated-at timestamps)))))))

(deftest file-timestamps-multiple-commits-test
  (testing "returns different timestamps after update"
    (let [repo      (git-repo/create-git-repo {:repo-path *test-repo-path*
                                               :writer? true})
          jgit-repo (git/git-init :dir *test-repo-path*)]
      ;; Create and commit a file
      (git-repo/write-file repo "multi-commit.txt" "initial")
      (git/git-add jgit-repo ".")
      (git/git-commit jgit-repo "First commit" :name "Test" :email "test@test.com")

      ;; Wait a moment to ensure different timestamps
      (Thread/sleep 1100)

      ;; Update and commit again
      (git-repo/write-file repo "multi-commit.txt" "updated")
      (git/git-add jgit-repo ".")
      (git/git-commit jgit-repo "Second commit" :name "Test" :email "test@test.com")

      (let [timestamps (git-repo/file-timestamps repo "multi-commit.txt")]
        (is (some? timestamps))
        ;; created-at should be before updated-at
        (is (.isBefore (:created-at timestamps) (:updated-at timestamps)))))))

;; Auto-staging tests

(deftest write-file-auto-stages-new-file-test
  (testing "write-file automatically stages new files"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})
          _    (git/git-init :dir *test-repo-path*)]
      (git-repo/write-file repo "new-file.txt" "content")
      (let [status (git-repo/status repo)]
        (is (contains? (:added status) "new-file.txt"))
        (is (not (contains? (:untracked status) "new-file.txt")))))))

(deftest write-file-auto-stages-modified-file-test
  (testing "write-file automatically stages modifications"
    (let [repo      (git-repo/create-git-repo {:repo-path *test-repo-path*
                                               :writer? true})
          jgit-repo (git/git-init :dir *test-repo-path*)]
      ;; Create and commit initial file
      (git-repo/write-file repo "existing.txt" "initial")
      (git/git-commit jgit-repo "Initial" :name "Test" :email "test@test.com")

      ;; Modify the file
      (git-repo/write-file repo "existing.txt" "modified")
      (let [status (git-repo/status repo)]
        (is (contains? (:changed status) "existing.txt"))))))

(deftest delete-file-auto-stages-removal-test
  (testing "delete-file automatically stages the deletion"
    (let [repo      (git-repo/create-git-repo {:repo-path *test-repo-path*
                                               :writer? true})
          jgit-repo (git/git-init :dir *test-repo-path*)]
      ;; Create and commit a file
      (git-repo/write-file repo "to-remove.txt" "content")
      (git/git-commit jgit-repo "Add file" :name "Test" :email "test@test.com")

      ;; Delete the file
      (git-repo/delete-file repo "to-remove.txt")
      (let [status (git-repo/status repo)]
        (is (contains? (:removed status) "to-remove.txt"))))))

(deftest write-file-no-git-repo-test
  (testing "write-file works without git repo (no staging)"
    (let [repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                          :writer? true})]
      ;; No git init - should still write file without error
      (git-repo/write-file repo "no-git.txt" "content")
      (is (= "content" (git-repo/read-file repo "no-git.txt"))))))
