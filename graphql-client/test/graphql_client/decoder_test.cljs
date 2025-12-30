(ns graphql-client.decoder-test
  "Tests for the decoder builder."
  (:require
   [cljs.test :as t :include-macros true]
   [graphql-client.decoder :as decoder]
   [graphql-client.registry :as registry]))

(def User
  [:map {:graphql/type :User}
   [:id :string]
   [:first-name :string]
   [:status [:enum :status/ACTIVE :status/INACTIVE]]])

(def Post
  [:map {:graphql/type :Post}
   [:id :string]
   [:title :string]
   [:author User]])

(def type-registry
  (registry/build-typename-registry [User Post]))

(t/deftest build-decode-fn-decodes-by-typename
  (t/testing "decodes based on __typename"
    (let [decode-fn (decoder/build-decode-fn type-registry)
          data      {"__typename" "User"
                     "id" "123"
                     "firstName" "Alice"
                     "status" "ACTIVE"}
          result    (decode-fn data)]
      (t/is (= "123" (:id result)))
      (t/is (= "Alice" (:first-name result)))
      (t/is (= :status/ACTIVE (:status result))))))

(t/deftest build-decode-fn-handles-nested
  (t/testing "decodes nested typed objects"
    (let [decode-fn (decoder/build-decode-fn type-registry)
          data      {"__typename" "Post"
                     "id" "post-1"
                     "title" "Hello World"
                     "author" {"__typename" "User"
                               "id" "123"
                               "firstName" "Bob"
                               "status" "INACTIVE"}}
          result    (decode-fn data)]
      (t/is (= "post-1" (:id result)))
      (t/is (= "Hello World" (:title result)))
      (t/is (= "123" (get-in result [:author :id])))
      (t/is (= "Bob" (get-in result [:author :first-name])))
      (t/is (= :status/INACTIVE (get-in result [:author :status]))))))

(t/deftest build-decode-fn-converts-untyped-keys
  (t/testing "converts string keys in untyped wrapper maps"
    (let [decode-fn (decoder/build-decode-fn type-registry)
          data      {"data" {"__typename" "User"
                             "id" "123"
                             "firstName" "Alice"
                             "status" "ACTIVE"}}
          result    (decode-fn data)]
      (t/is (= "123" (get-in result [:data :id])))
      (t/is (= "Alice" (get-in result [:data :first-name]))))))

(t/deftest decode-js-response-converts-js
  (t/testing "converts JS objects and decodes by typename"
    (let [decode-fn (decoder/decode-js-response type-registry)
          js-data   #js {:__typename "User"
                         :id "456"
                         :firstName "Charlie"
                         :status "ACTIVE"}
          result    (decode-fn js-data)]
      (t/is (= "456" (:id result)))
      (t/is (= "Charlie" (:first-name result)))
      (t/is (= :status/ACTIVE (:status result))))))
