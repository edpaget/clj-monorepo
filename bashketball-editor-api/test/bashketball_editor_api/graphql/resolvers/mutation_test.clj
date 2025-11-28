(ns bashketball-editor-api.graphql.resolvers.mutation-test
  (:require
   [bashketball-editor-api.graphql.resolvers.mutation :as mutation]
   [clojure.test :refer [deftest is testing]]
   [com.walmartlabs.lacinia.resolve :as resolve]))

(def test-user-id (random-uuid))

(defn make-ctx
  [authenticated?]
  {:request {:authn/authenticated? authenticated?
             :authn/user-id (str test-user-id)}})

(deftest pull-from-remote-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx false)
          [_schema resolver] (get mutation/resolvers [:Mutation :pullFromRemote])
          result             (resolver ctx {} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))))))

(deftest push-to-remote-mutation-test
  (testing "requires authentication"
    (let [ctx                (make-ctx false)
          [_schema resolver] (get mutation/resolvers [:Mutation :pushToRemote])
          result             (resolver ctx {} nil)]
      (is (resolve/is-resolver-result? result))
      (let [wrapped-value (:resolved-value result)]
        (is (= :error (:behavior wrapped-value)))))))
