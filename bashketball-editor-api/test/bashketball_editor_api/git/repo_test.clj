(ns bashketball-editor-api.git.repo-test
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]))

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
