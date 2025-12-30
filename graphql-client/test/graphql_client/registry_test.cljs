(ns graphql-client.registry-test
  "Tests for registry utilities."
  (:require
   [cljs.test :as t :include-macros true]
   [graphql-client.registry :as registry]))

(def User
  [:map {:graphql/type :User}
   [:id :uuid]
   [:name :string]])

(def Post
  [:map {:graphql/type :Post}
   [:id :uuid]
   [:title :string]])

(def Comment
  [:map
   [:id :uuid]
   [:text :string]])

(t/deftest extract-graphql-type-finds-annotation
  (t/testing "extracts :graphql/type from schema properties"
    (t/is (= "User" (registry/extract-graphql-type User)))
    (t/is (= "Post" (registry/extract-graphql-type Post)))))

(t/deftest extract-graphql-type-returns-nil-when-absent
  (t/testing "returns nil when :graphql/type is not present"
    (t/is (nil? (registry/extract-graphql-type Comment)))))

(t/deftest build-typename-registry-creates-mapping
  (t/testing "builds typename->schema map from schemas"
    (let [registry (registry/build-typename-registry [User Post Comment])]
      (t/is (= User (get registry "User")))
      (t/is (= Post (get registry "Post")))
      (t/is (not (contains? registry "Comment"))))))

(t/deftest build-typename-registry-handles-empty
  (t/testing "handles empty schema list"
    (let [registry (registry/build-typename-registry [])]
      (t/is (= {} registry)))))
