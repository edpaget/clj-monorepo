(ns bashketball-editor-api.github.client-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bashketball-editor-api.github.client :as client]))

(deftest create-github-client-test
  (testing "creates a GitHubClient record with all fields"
    (let [gh-client (client/create-github-client "token123" "owner" "repo" "main")]
      (is (instance? bashketball_editor_api.github.client.GitHubClient gh-client))
      (is (= "token123" (:access-token gh-client)))
      (is (= "owner" (:owner gh-client)))
      (is (= "repo" (:repo gh-client)))
      (is (= "main" (:branch gh-client)))))

  (testing "allows nil access-token"
    (let [gh-client (client/create-github-client nil "owner" "repo" "main")]
      (is (nil? (:access-token gh-client))))))

(deftest get-file-test
  (testing "returns nil (stub implementation)"
    (let [gh-client (client/create-github-client "token" "owner" "repo" "main")]
      (is (nil? (client/get-file gh-client "path/to/file.edn"))))))

(deftest create-or-update-file-test
  (testing "returns nil (stub implementation)"
    (let [gh-client (client/create-github-client "token" "owner" "repo" "main")]
      (is (nil? (client/create-or-update-file gh-client "path/to/file.edn" "content" "commit message"))))))

(deftest delete-file-test
  (testing "returns nil (stub implementation)"
    (let [gh-client (client/create-github-client "token" "owner" "repo" "main")]
      (is (nil? (client/delete-file gh-client "path/to/file.edn" "delete message"))))))

(deftest list-files-test
  (testing "returns empty vector (stub implementation)"
    (let [gh-client (client/create-github-client "token" "owner" "repo" "main")]
      (is (= [] (client/list-files gh-client "path/to/dir"))))))
