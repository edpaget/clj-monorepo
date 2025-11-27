(ns bashketball-editor-api.git.cards-test
  (:require
   [bashketball-editor-api.context :as ctx]
   [bashketball-editor-api.git.cards :as git-cards]
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:dynamic *test-repo* nil)
(def ^:dynamic *test-repo-path* nil)

(defn with-temp-repo [f]
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                          (str "test-cards-repo-" (System/currentTimeMillis)))]
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

(deftest create-card-repository-test
  (testing "creates a CardRepository record"
    (let [repo (git-cards/create-card-repository *test-repo* ctx/current-user-context)]
      (is (instance? bashketball_editor_api.git.cards.CardRepository repo))
      (is (= *test-repo* (:git-repo repo))))))

(deftest find-by-test
  (testing "returns nil when card not found"
    (let [card-repo (git-cards/create-card-repository *test-repo* ctx/current-user-context)]
      (is (nil? (proto/find-by card-repo {:slug "nonexistent"
                                          :set-slug "nonexistent-set"})))))

  (testing "requires both slug and set-slug"
    (let [card-repo (git-cards/create-card-repository *test-repo* ctx/current-user-context)]
      (is (nil? (proto/find-by card-repo {:slug "test"})))
      (is (nil? (proto/find-by card-repo {:set-slug "some-set"}))))))

(deftest find-all-test
  (testing "returns empty vector when no cards in set"
    (let [card-repo (git-cards/create-card-repository *test-repo* ctx/current-user-context)]
      (is (= [] (proto/find-all card-repo {:where {:set-slug "empty-set"}})))))

  (testing "returns empty vector when no set-slug filter"
    (let [card-repo (git-cards/create-card-repository *test-repo* ctx/current-user-context)]
      (is (= [] (proto/find-all card-repo {}))))))

(deftest read-only-test
  (testing "throws on create when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          card-repo      (git-cards/create-card-repository read-only-repo ctx/current-user-context)
          card-data      {:slug "test-card"
                          :name "Test Card"
                          :set-slug "test-set"
                          :card-type :card-type/PLAYER_CARD
                          :deck-size 5
                          :sht 1 :pss 1 :def 1 :speed 1
                          :size :size/SM
                          :abilities []}]
      (binding [ctx/*user-context* test-user-ctx]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                              (proto/create! card-repo card-data))))))

  (testing "throws on update when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          card-repo      (git-cards/create-card-repository read-only-repo ctx/current-user-context)
          card-data      {:name "Test Card"}]
      (binding [ctx/*user-context* test-user-ctx]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                              (proto/update! card-repo {:slug "test-card" :set-slug "test-set"} card-data)))))))

(deftest user-context-required-test
  (testing "throws when user context missing on create"
    (let [card-repo (git-cards/create-card-repository *test-repo* ctx/current-user-context)
          card-data {:slug "test-card"
                     :name "Test Card"
                     :set-slug "test-set"
                     :card-type :card-type/PLAYER_CARD
                     :deck-size 5
                     :sht 1 :pss 1 :def 1 :speed 1
                     :size :size/SM
                     :abilities []}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No user context available"
                            (proto/create! card-repo card-data)))))

  (testing "throws when user context missing on update"
    (let [card-repo (git-cards/create-card-repository *test-repo* ctx/current-user-context)
          card-data {:name "Test Card"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No user context available"
                            (proto/update! card-repo {:slug "test-card" :set-slug "test-set"} card-data))))))

(deftest slugify-test
  (testing "converts strings to URL-safe slugs"
    (is (= "hello-world" (git-cards/slugify "Hello World")))
    (is (= "jordan-23" (git-cards/slugify "Jordan #23")))
    (is (= "test" (git-cards/slugify "  Test  ")))
    (is (= "fast-break" (git-cards/slugify "Fast Break!")))))
