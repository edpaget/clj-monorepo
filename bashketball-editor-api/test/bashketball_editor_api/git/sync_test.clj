(ns bashketball-editor-api.git.sync-test
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.git.sync :as git-sync]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:dynamic *test-repo* nil)
(def ^:dynamic *test-repo-path* nil)

(defn with-temp-repo [f]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                          (str "test-sync-repo-" (System/currentTimeMillis)))]
    (.mkdirs temp-dir)
    (binding [*test-repo-path* (.getAbsolutePath temp-dir)
              *test-repo* (git-repo/create-git-repo {:repo-path (.getAbsolutePath temp-dir)
                                                     :writer? true})]
      (try
        (f)
        (finally
          (doseq [file (reverse (file-seq temp-dir))]
            (.delete file)))))))

(use-fixtures :each with-temp-repo)

(deftest get-sync-status-test
  (testing "returns status with defaults for non-git directory"
    (let [status (git-sync/get-sync-status *test-repo*)]
      (is (map? status))
      (is (contains? status :ahead))
      (is (contains? status :behind))
      (is (contains? status :uncommittedChanges))
      (is (contains? status :isClean))
      (is (= 0 (:ahead status)))
      (is (= 0 (:behind status))))))

(deftest push-read-only-test
  (testing "push returns error for read-only repo"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          result (git-sync/push-to-remote read-only-repo "token")]
      (is (= "error" (:status result)))
      (is (= "Repository is read-only" (:message result))))))
