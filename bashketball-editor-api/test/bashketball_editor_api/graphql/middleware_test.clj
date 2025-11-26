(ns bashketball-editor-api.graphql.middleware-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bashketball-editor-api.graphql.middleware :as mw]))

(deftest wrap-db-context-test
  (testing "adds db-pool to context"
    (let [mock-db-pool {:pool "test-pool"}
          resolver     (fn [ctx _args _value]
                         {:db-pool (:db-pool ctx)})
          wrapped      (mw/wrap-db-context resolver mock-db-pool)
          result       (wrapped {} nil nil)]
      (is (= mock-db-pool (:db-pool result)))))

  (testing "preserves existing context"
    (let [mock-db-pool {:pool "test-pool"}
          resolver     (fn [ctx _args _value]
                         {:existing (:existing ctx)
                          :db-pool (:db-pool ctx)})
          wrapped      (mw/wrap-db-context resolver mock-db-pool)
          result       (wrapped {:existing "value"} nil nil)]
      (is (= "value" (:existing result)))
      (is (= mock-db-pool (:db-pool result))))))

(deftest wrap-repositories-test
  (testing "adds repositories to context"
    (let [repos    {:user-repo "user-repo" :card-repo "card-repo"}
          resolver (fn [ctx _args _value]
                     {:user-repo (:user-repo ctx)
                      :card-repo (:card-repo ctx)})
          wrapped  (mw/wrap-repositories resolver repos)
          result   (wrapped {} nil nil)]
      (is (= "user-repo" (:user-repo result)))
      (is (= "card-repo" (:card-repo result)))))

  (testing "preserves existing context"
    (let [repos    {:user-repo "user-repo"}
          resolver (fn [ctx _args _value]
                     {:existing (:existing ctx)
                      :user-repo (:user-repo ctx)})
          wrapped  (mw/wrap-repositories resolver repos)
          result   (wrapped {:existing "value"} nil nil)]
      (is (= "value" (:existing result)))
      (is (= "user-repo" (:user-repo result))))))

(deftest require-authentication-test
  (testing "allows authenticated requests"
    (let [resolver (fn [_ctx _args _value] {:data "success"})
          wrapped  (mw/require-authentication resolver)
          ctx      {:request {:authn/authenticated? true}}
          result   (wrapped ctx nil nil)]
      (is (= {:data "success"} result))))

  (testing "blocks unauthenticated requests"
    (let [resolver (fn [_ctx _args _value] {:data "success"})
          wrapped  (mw/require-authentication resolver)
          ctx      {:request {:authn/authenticated? false}}
          result   (wrapped ctx nil nil)]
      (is (contains? result :errors))
      (is (= "Authentication required" (get-in result [:errors 0 :message])))))

  (testing "blocks requests without authentication flag"
    (let [resolver (fn [_ctx _args _value] {:data "success"})
          wrapped  (mw/require-authentication resolver)
          ctx      {:request {}}
          result   (wrapped ctx nil nil)]
      (is (contains? result :errors)))))
