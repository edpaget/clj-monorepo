(ns bashketball-editor-api.git.sync-test
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.git.sync :as git-sync]
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:dynamic *test-repo* nil)
(def ^:dynamic *test-repo-path* nil)

(defn with-temp-repo [f]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                          (str "test-sync-repo-" (System/currentTimeMillis)))]
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

(deftest get-sync-status-test
  (testing "returns error status for non-git directory"
    (let [status (git-sync/get-sync-status *test-repo*)]
      (is (map? status))
      (is (contains? status :ahead))
      (is (contains? status :behind))
      (is (contains? status :uncommitted-changes))
      (is (contains? status :is-clean))
      (is (= 0 (:ahead status)))
      (is (= 0 (:behind status)))
      (is (false? (:is-clean status)) "non-git directory should not be clean")
      (is (= :not-a-git-repo (:error status))))))

(deftest get-working-tree-status-test
  (testing "returns error status for non-git directory"
    (let [status (git-sync/get-working-tree-status *test-repo*)]
      (is (map? status))
      (is (true? (:is-dirty status)) "non-git directory should be dirty")
      (is (= [] (:added status)))
      (is (= [] (:modified status)))
      (is (= [] (:deleted status)))
      (is (= [] (:untracked status)))
      (is (= :not-a-git-repo (:error status))))))

(deftest get-sync-status-initialized-test
  (testing "returns clean status for initialized empty git repo"
    (git/git-init :dir *test-repo-path*)
    (let [status (git-sync/get-sync-status *test-repo*)]
      (is (map? status))
      (is (nil? (:error status)))
      (is (= 0 (:ahead status)))
      (is (= 0 (:behind status)))
      (is (= 0 (:uncommitted-changes status)))
      (is (true? (:is-clean status))))))

(deftest get-working-tree-status-initialized-test
  (testing "returns clean status for initialized empty git repo"
    (git/git-init :dir *test-repo-path*)
    (let [status (git-sync/get-working-tree-status *test-repo*)]
      (is (map? status))
      (is (nil? (:error status)))
      (is (false? (:is-dirty status)))
      (is (= [] (:added status)))
      (is (= [] (:modified status)))
      (is (= [] (:deleted status)))
      (is (= [] (:untracked status)))))

  (testing "returns dirty status when there are staged files"
    (git/git-init :dir *test-repo-path*)
    (git-repo/write-file *test-repo* "new-file.txt" "content")
    (let [status (git-sync/get-working-tree-status *test-repo*)]
      (is (nil? (:error status)))
      (is (true? (:is-dirty status)))
      ;; File is staged (added) because write-file auto-stages
      (is (= ["new-file.txt"] (:added status))))))

(deftest push-read-only-test
  (testing "push returns error for read-only repo"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          result         (git-sync/push-to-remote read-only-repo "token")]
      (is (= "error" (:status result)))
      (is (= "Repository is read-only" (:message result))))))
