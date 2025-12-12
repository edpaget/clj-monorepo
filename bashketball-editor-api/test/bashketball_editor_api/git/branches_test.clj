(ns bashketball-editor-api.git.branches-test
  "Tests for BranchRepository."
  (:require
   [bashketball-editor-api.git.branches :as branches]
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:dynamic *test-repo* nil)
(def ^:dynamic *test-repo-path* nil)

(defn with-temp-repo [f]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                          (str "test-branch-repo-" (System/currentTimeMillis)))]
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
  (testing "returns empty list for non-git directory"
    (let [branch-repo (branches/create-branch-repository *test-repo*)]
      (is (= [] (proto/find-all branch-repo {}))))))

(deftest find-all-initialized-repo-test
  (testing "returns branches for initialized repo"
    (git/git-init :dir *test-repo-path*)
    ;; Need at least one commit to have a branch
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)
          branches    (proto/find-all branch-repo {})]
      (is (= 1 (count branches)))
      (is (= "master" (:name (first branches))))
      (is (true? (:current (first branches)))))))

(deftest find-by-name-test
  (testing "finds branch by name"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)
          branch      (proto/find-by branch-repo {:name "master"})]
      (is (some? branch))
      (is (= "master" (:name branch)))
      (is (true? (:current branch)))))

  (testing "returns nil for non-existent branch"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)
          branch      (proto/find-by branch-repo {:name "nonexistent"})]
      (is (nil? branch)))))

(deftest find-by-current-test
  (testing "finds current branch"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)
          branch      (proto/find-by branch-repo {:current true})]
      (is (some? branch))
      (is (= "master" (:name branch)))
      (is (true? (:current branch))))))

(deftest create-branch-test
  (testing "creates and checks out new branch"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)
          result      (proto/create! branch-repo {:name "feature-branch"})]
      (is (= "success" (:status result)))
      (is (= "feature-branch" (:branch result)))

      ;; Verify we're on the new branch
      (let [current (proto/find-by branch-repo {:current true})]
        (is (= "feature-branch" (:name current))))))

  (testing "returns error for duplicate branch name"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)
          result      (proto/create! branch-repo {:name "master"})]
      (is (= "error" (:status result)))
      (is (re-find #"already exists" (:message result))))))

(deftest create-branch-read-only-test
  (testing "throws for read-only repo"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          branch-repo    (branches/create-branch-repository read-only-repo)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"read-only"
                            (proto/create! branch-repo {:name "new-branch"}))))))

(deftest switch-branch-test
  (testing "switches to existing branch"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)]
      ;; Create a new branch first
      (proto/create! branch-repo {:name "feature"})
      ;; Switch back to master
      (let [result (proto/update! branch-repo {:name "master"} {})]
        (is (= "success" (:status result)))
        (is (= "master" (:branch result)))

        ;; Verify we're on master
        (let [current (proto/find-by branch-repo {:current true})]
          (is (= "master" (:name current)))))))

  (testing "returns error for non-existent branch"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "initial.txt" "content")
    (let [jgit (git/load-repo *test-repo-path*)]
      (git/git-commit jgit "Initial commit" :name "Test" :email "test@test.com"))

    (let [branch-repo (branches/create-branch-repository *test-repo*)
          result      (proto/update! branch-repo {:name "nonexistent"} {})]
      (is (= "error" (:status result)))
      (is (re-find #"not found" (:message result))))))

(deftest delete-not-implemented-test
  (testing "delete throws not implemented"
    (let [branch-repo (branches/create-branch-repository *test-repo*)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Not implemented"
                            (proto/delete! branch-repo "any"))))))
