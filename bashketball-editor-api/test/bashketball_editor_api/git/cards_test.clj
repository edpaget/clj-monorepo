(ns bashketball-editor-api.git.cards-test
  (:require
   [bashketball-editor-api.context :as ctx]
   [bashketball-editor-api.git.cards :as git-cards]
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
   [java.time Instant]))

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

(deftest git-timestamps-find-by-test
  (testing "find-by returns git timestamps for cards without timestamps in EDN"
    (let [jgit-repo  (git/git-init :dir *test-repo-path*)
          card-repo  (git-cards/create-card-repository *test-repo* ctx/current-user-context)
          ;; Write card EDN without timestamps
          card-edn   "{:slug \"test-card\" :name \"Test\" :set-slug \"test-set\" :card-type :card-type/PLAYER_CARD}"]
      ;; Create set directory and card file
      (git-repo/write-file *test-repo* "test-set/test-card.edn" card-edn)
      (git/git-add jgit-repo ".")
      (git/git-commit jgit-repo "Add test card" :name "Test" :email "test@test.com")

      (let [card (proto/find-by card-repo {:slug "test-card" :set-slug "test-set"})]
        (is (some? card))
        (is (= "test-card" (:slug card)))
        (is (instance? Instant (:created-at card)))
        (is (instance? Instant (:updated-at card)))))))

(deftest git-timestamps-find-all-test
  (testing "find-all returns git timestamps for cards without timestamps in EDN"
    (let [jgit-repo  (git/git-init :dir *test-repo-path*)
          card-repo  (git-cards/create-card-repository *test-repo* ctx/current-user-context)
          ;; Write cards without timestamps
          card1-edn  "{:slug \"card-1\" :name \"Card 1\" :set-slug \"test-set\" :card-type :card-type/PLAYER_CARD}"
          card2-edn  "{:slug \"card-2\" :name \"Card 2\" :set-slug \"test-set\" :card-type :card-type/ABILITY_CARD}"]
      ;; Create set directory and card files
      (git-repo/write-file *test-repo* "test-set/metadata.edn" "{:slug \"test-set\" :name \"Test Set\"}")
      (git-repo/write-file *test-repo* "test-set/card-1.edn" card1-edn)
      (git-repo/write-file *test-repo* "test-set/card-2.edn" card2-edn)
      (git/git-add jgit-repo ".")
      (git/git-commit jgit-repo "Add cards" :name "Test" :email "test@test.com")

      (let [cards (proto/find-all card-repo {:where {:set-slug "test-set"}})]
        (is (= 2 (count cards)))
        (doseq [card cards]
          (is (instance? Instant (:created-at card)))
          (is (instance? Instant (:updated-at card))))))))
