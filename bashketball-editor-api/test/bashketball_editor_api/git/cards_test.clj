(ns bashketball-editor-api.git.cards-test
  (:require
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
              *test-repo* (git-repo/create-git-repo {:repo-path (.getAbsolutePath temp-dir)
                                                     :writer? true})]
      (try
        (f)
        (finally
          (doseq [file (reverse (file-seq temp-dir))]
            (.delete file)))))))

(use-fixtures :each with-temp-repo)

(deftest create-card-repository-test
  (testing "creates a CardRepository record"
    (let [repo (git-cards/create-card-repository *test-repo*)]
      (is (instance? bashketball_editor_api.git.cards.CardRepository repo))
      (is (= *test-repo* (:git-repo repo))))))

(deftest find-by-test
  (testing "returns nil when card not found"
    (let [card-repo (git-cards/create-card-repository *test-repo*)]
      (is (nil? (proto/find-by card-repo {:slug "nonexistent"
                                          :set-id (random-uuid)})))))

  (testing "requires both slug and set-id"
    (let [card-repo (git-cards/create-card-repository *test-repo*)]
      (is (nil? (proto/find-by card-repo {:slug "test"})))
      (is (nil? (proto/find-by card-repo {:set-id (random-uuid)}))))))

(deftest find-all-test
  (testing "returns empty vector when no cards in set"
    (let [card-repo (git-cards/create-card-repository *test-repo*)]
      (is (= [] (proto/find-all card-repo {:where {:set-id (random-uuid)}})))))

  (testing "returns empty vector when no set-id filter"
    (let [card-repo (git-cards/create-card-repository *test-repo*)]
      (is (= [] (proto/find-all card-repo {}))))))

(deftest read-only-test
  (testing "throws on create when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          card-repo (git-cards/create-card-repository read-only-repo)
          card-data {:slug "test-card"
                     :name "Test Card"
                     :set-id (random-uuid)
                     :card-type :card-type/PLAYER_CARD
                     :deck-size 5
                     :sht 1 :pss 1 :def 1 :speed 1
                     :size :size/SM
                     :abilities []
                     :_user {:name "Test" :email "test@example.com" :github-token "token"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                            (proto/create! card-repo card-data)))))

  (testing "throws on update when read-only"
    (let [read-only-repo (git-repo/create-git-repo {:repo-path *test-repo-path*
                                                    :writer? false})
          card-repo (git-cards/create-card-repository read-only-repo)
          card-data {:set-id (random-uuid)
                     :name "Test Card"
                     :_user {:name "Test" :email "test@example.com" :github-token "token"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"read-only"
                            (proto/update! card-repo {:slug "test-card"} card-data))))))

(deftest user-context-required-test
  (testing "throws when user context missing on create"
    (let [card-repo (git-cards/create-card-repository *test-repo*)
          card-data {:slug "test-card"
                     :name "Test Card"
                     :set-id (random-uuid)
                     :card-type :card-type/PLAYER_CARD
                     :deck-size 5
                     :sht 1 :pss 1 :def 1 :speed 1
                     :size :size/SM
                     :abilities []}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"User context required"
                            (proto/create! card-repo card-data)))))

  (testing "throws when user context missing on update"
    (let [card-repo (git-cards/create-card-repository *test-repo*)
          card-data {:set-id (random-uuid)
                     :name "Test Card"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"User context required"
                            (proto/update! card-repo {:slug "test-card"} card-data))))))

(deftest slugify-test
  (testing "converts strings to URL-safe slugs"
    (is (= "hello-world" (git-cards/slugify "Hello World")))
    (is (= "jordan-23" (git-cards/slugify "Jordan #23")))
    (is (= "test" (git-cards/slugify "  Test  ")))
    (is (= "fast-break" (git-cards/slugify "Fast Break!")))))
