(ns bashketball-game-api.graphql.resolvers.deck-test
  "Tests for deck GraphQL resolvers."
  (:require
   [bashketball-game-api.system :as system]
   [bashketball-game-api.test-utils :refer [with-server with-clean-db
                                            create-test-user create-test-deck
                                            server-port *system*]]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [db.core :as db]))

(use-fixtures :once with-server)
(use-fixtures :each with-clean-db)

(defn graphql-url []
  (str "http://localhost:" (server-port) "/graphql"))

(defn execute-query
  ([query]
   (execute-query query nil nil))
  ([query variables]
   (execute-query query variables nil))
  ([query variables cookies]
   (let [response (http/post (graphql-url)
                             (cond-> {:body (json/generate-string
                                             {:query query
                                              :variables variables})
                                      :content-type :json
                                      :as :json
                                      :throw-exceptions false}
                               cookies (assoc :cookies cookies)))]
     (:body response))))

;; ---------------------------------------------------------------------------
;; Query Tests (Unauthenticated)

(deftest my-decks-unauthenticated-test
  (testing "myDecks query throws error when not authenticated"
    (let [result (execute-query "{ myDecks { id name } }")]
      (is (some? (:errors result))))))

(deftest deck-query-unauthenticated-test
  (testing "deck query throws error when not authenticated"
    (let [result (execute-query "{ deck(id: \"00000000-0000-0000-0000-000000000000\") { id name } }")]
      (is (some? (:errors result))))))

;; ---------------------------------------------------------------------------
;; Mutation Tests (Unauthenticated)

(deftest create-deck-unauthenticated-test
  (testing "createDeck mutation throws error when not authenticated"
    (let [result (execute-query "mutation { createDeck(name: \"Test\") { id name } }")]
      (is (some? (:errors result))))))

(deftest update-deck-unauthenticated-test
  (testing "updateDeck mutation throws error when not authenticated"
    (let [result (execute-query "mutation { updateDeck(id: \"00000000-0000-0000-0000-000000000000\", name: \"Updated\") { id name } }")]
      (is (some? (:errors result))))))

(deftest delete-deck-unauthenticated-test
  (testing "deleteDeck mutation throws error when not authenticated"
    (let [result (execute-query "mutation { deleteDeck(id: \"00000000-0000-0000-0000-000000000000\") }")]
      (is (some? (:errors result))))))

(deftest validate-deck-unauthenticated-test
  (testing "validateDeck mutation throws error when not authenticated"
    (let [result (execute-query "mutation { validateDeck(id: \"00000000-0000-0000-0000-000000000000\") { id isValid } }")]
      (is (some? (:errors result))))))

;; ---------------------------------------------------------------------------
;; Service Layer Tests (bypassing HTTP auth)
;; These test the resolver logic directly through the deck service

(deftest deck-service-crud-integration-test
  (testing "Deck service CRUD operations work correctly"
    (binding [db/*datasource* (::system/db-pool *system*)]
      (let [user         (create-test-user)
            deck-service (::system/deck-service *system*)
            create-fn    (requiring-resolve 'bashketball-game-api.services.deck/create-deck!)
            list-fn      (requiring-resolve 'bashketball-game-api.services.deck/list-user-decks)
            get-fn       (requiring-resolve 'bashketball-game-api.services.deck/get-deck)
            created      (create-fn deck-service (:id user) "My Test Deck")]
        (is (some? (:id created)))
        (is (= "My Test Deck" (:name created)))
        (is (= 1 (count (list-fn deck-service (:id user)))))
        (is (= (:id created) (:id (get-fn deck-service (:id created)))))))))

(deftest deck-validation-integration-test
  (testing "Deck validation updates is-valid and validation-errors"
    (binding [db/*datasource* (::system/db-pool *system*)]
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "Test Deck" ["michael-jordan"])
            deck-service (::system/deck-service *system*)
            validate-fn  (requiring-resolve 'bashketball-game-api.services.deck/validate-deck!)
            validated    (validate-fn deck-service (:id deck) (:id user))]
        (is (false? (:is-valid validated)))
        (is (vector? (:validation-errors validated)))
        (is (some #(re-find #"player cards" %) (:validation-errors validated)))))))

(deftest deck-card-operations-integration-test
  (testing "Adding and removing cards from deck"
    (binding [db/*datasource* (::system/db-pool *system*)]
      (let [user         (create-test-user)
            deck         (create-test-deck (:id user) "Test Deck" [])
            deck-service (::system/deck-service *system*)
            add-svc      (requiring-resolve 'bashketball-game-api.services.deck/add-cards-to-deck!)
            remove-svc   (requiring-resolve 'bashketball-game-api.services.deck/remove-cards-from-deck!)]
        ;; Add cards
        (let [with-cards (add-svc deck-service (:id deck) (:id user) ["michael-jordan" "shaq"])]
          (is (= ["michael-jordan" "shaq"] (:card-slugs with-cards))))

        ;; Remove a card
        (let [removed (remove-svc deck-service (:id deck) (:id user) ["shaq"])]
          (is (= ["michael-jordan"] (:card-slugs removed))))))))
