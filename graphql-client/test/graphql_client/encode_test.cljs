(ns graphql-client.encode-test
  "Tests for variable and option encoding utilities."
  (:require
   [cljs.test :as t :include-macros true]
   [graphql-client.encode :as encode]))

(t/deftest encode-variable-keys-converts-kebab-to-camel
  (t/testing "converts kebab-case keys to camelCase"
    (let [vars   {:user-id "123" :first-name "Alice"}
          result (encode/encode-variable-keys vars)]
      (t/is (= "123" (get result :userId)))
      (t/is (= "Alice" (get result :firstName))))))

(t/deftest encode-variable-keys-handles-nested
  (t/testing "recursively converts nested maps"
    (let [vars   {:user {:first-name "Bob" :last-name "Smith"}}
          result (encode/encode-variable-keys vars)]
      (t/is (= "Bob" (get-in result [:user :firstName])))
      (t/is (= "Smith" (get-in result [:user :lastName]))))))

(t/deftest encode-variable-keys-returns-nil-for-nil
  (t/testing "returns nil when given nil"
    (t/is (nil? (encode/encode-variable-keys nil)))))

(t/deftest encode-options-converts-to-js
  (t/testing "converts options to JS object"
    (let [opts       {:fetch-policy "network-only"}
          ^js result (encode/encode-options opts)]
      (t/is (object? result))
      (t/is (= "network-only" (.-fetchPolicy result))))))

(t/deftest encode-options-encodes-variables
  (t/testing "encodes nested variables with camelCase"
    (let [opts       {:variables {:user-id "123"}}
          ^js result (encode/encode-options opts)]
      (t/is (object? result))
      (t/is (= "123" (.. result -variables -userId))))))

(t/deftest encode-options-returns-empty-js-for-nil
  (t/testing "returns empty JS object for nil options"
    (let [result (encode/encode-options nil)]
      (t/is (object? result)))))
