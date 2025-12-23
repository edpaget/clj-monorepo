(ns polix.residual-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.residual :as r]))

;;; ---------------------------------------------------------------------------
;;; Predicate Tests
;;; ---------------------------------------------------------------------------

(deftest satisfied?-test
  (testing "empty map is satisfied"
    (is (true? (r/satisfied? {}))))

  (testing "non-empty map is not satisfied"
    (is (false? (r/satisfied? {[:role] [[:= "admin"]]}))))

  (testing "nil is not satisfied"
    (is (false? (r/satisfied? nil))))

  (testing "non-map values are not satisfied"
    (is (false? (r/satisfied? true)))
    (is (false? (r/satisfied? [])))
    (is (false? (r/satisfied? "string")))))

(deftest contradiction?-test
  (testing "nil is a contradiction"
    (is (true? (r/contradiction? nil))))

  (testing "empty map is not a contradiction"
    (is (false? (r/contradiction? {}))))

  (testing "non-empty map is not a contradiction"
    (is (false? (r/contradiction? {[:role] [[:= "admin"]]}))))

  (testing "other values are not contradictions"
    (is (false? (r/contradiction? true)))
    (is (false? (r/contradiction? false)))))

(deftest residual?-test
  (testing "non-empty map is a residual"
    (is (true? (r/residual? {[:role] [[:= "admin"]]})))
    (is (true? (r/residual? {[:a] [[:= 1]] [:b] [[:> 2]]}))))

  (testing "empty map is not a residual"
    (is (false? (r/residual? {}))))

  (testing "nil is not a residual"
    (is (false? (r/residual? nil))))

  (testing "non-map values are not residuals"
    (is (false? (r/residual? [[:= "admin"]])))
    (is (false? (r/residual? "string")))))

(deftest result-type-test
  (testing "classifies results correctly"
    (is (= :satisfied (r/result-type {})))
    (is (= :contradiction (r/result-type nil)))
    (is (= :residual (r/result-type {[:role] [[:= "admin"]]})))))

;;; ---------------------------------------------------------------------------
;;; Constructor Tests
;;; ---------------------------------------------------------------------------

(deftest satisfied-test
  (testing "returns empty map"
    (is (= {} (r/satisfied)))
    (is (r/satisfied? (r/satisfied)))))

(deftest contradiction-test
  (testing "returns nil"
    (is (nil? (r/contradiction)))
    (is (r/contradiction? (r/contradiction)))))

(deftest residual-test
  (testing "creates single-key residual"
    (let [res (r/residual [:role] [[:= "admin"]])]
      (is (= {[:role] [[:= "admin"]]} res))
      (is (r/residual? res)))))

;;; ---------------------------------------------------------------------------
;;; Merge Tests (AND semantics)
;;; ---------------------------------------------------------------------------

(deftest merge-residuals-test
  (testing "merging with nil produces nil"
    (is (nil? (r/merge-residuals nil {})))
    (is (nil? (r/merge-residuals {} nil)))
    (is (nil? (r/merge-residuals nil {[:x] [[:= 1]]})))
    (is (nil? (r/merge-residuals {[:x] [[:= 1]]} nil))))

  (testing "merging with empty returns other"
    (let [res {[:role] [[:= "admin"]]}]
      (is (= res (r/merge-residuals {} res)))
      (is (= res (r/merge-residuals res {})))))

  (testing "merging two empty returns empty"
    (is (= {} (r/merge-residuals {} {}))))

  (testing "merging disjoint keys combines them"
    (let [r1  {[:role] [[:= "admin"]]}
          r2  {[:level] [[:> 5]]}
          res (r/merge-residuals r1 r2)]
      (is (= {[:role] [[:= "admin"]]
              [:level] [[:> 5]]} res))))

  (testing "merging same key combines constraints"
    (let [r1  {[:level] [[:> 5]]}
          r2  {[:level] [[:< 100]]}
          res (r/merge-residuals r1 r2)]
      (is (= {[:level] [[:> 5] [:< 100]]} res))))

  (testing "merging complex residuals"
    (let [r1  {[:role] [[:= "admin"]] [:level] [[:> 5]]}
          r2  {[:level] [[:< 100]] [:status] [[:in #{"active"}]]}
          res (r/merge-residuals r1 r2)]
      (is (= {[:role] [[:= "admin"]]
              [:level] [[:> 5] [:< 100]]
              [:status] [[:in #{"active"}]]} res)))))

;;; ---------------------------------------------------------------------------
;;; Combine Tests (OR semantics)
;;; ---------------------------------------------------------------------------

(deftest combine-residuals-test
  (testing "combining with satisfied returns satisfied"
    (is (= {} (r/combine-residuals {} {[:x] [[:= 1]]})))
    (is (= {} (r/combine-residuals {[:x] [[:= 1]]} {}))))

  (testing "combining two contradictions returns contradiction"
    (is (nil? (r/combine-residuals nil nil))))

  (testing "combining contradiction with residual returns residual"
    (let [res {[:x] [[:= 1]]}]
      (is (= res (r/combine-residuals nil res)))
      (is (= res (r/combine-residuals res nil)))))

  (testing "combining two residuals produces complex"
    (let [r1  {[:role] [[:= "admin"]]}
          r2  {[:role] [[:= "moderator"]]}
          res (r/combine-residuals r1 r2)]
      (is (r/has-complex? res))
      (is (= :or (get-in res [::r/complex :type]))))))

;;; ---------------------------------------------------------------------------
;;; Accessor Tests
;;; ---------------------------------------------------------------------------

(deftest residual-keys-test
  (testing "returns keys from residual"
    (let [res {[:role] [[:= "admin"]]
               [:level] [[:> 5]]}]
      (is (= #{[:role] [:level]} (r/residual-keys res)))))

  (testing "excludes special keys"
    (let [res {[:role] [[:= "admin"]]
               ::r/cross-key [{:left [:a] :op := :right [:b]}]
               ::r/complex {:type :or}}]
      (is (= #{[:role]} (r/residual-keys res)))))

  (testing "returns nil for non-map"
    (is (nil? (r/residual-keys nil)))))

(deftest constraints-for-test
  (testing "returns constraints for path"
    (let [res {[:role] [[:= "admin"] [:!= "guest"]]}]
      (is (= [[:= "admin"] [:!= "guest"]] (r/constraints-for res [:role])))))

  (testing "returns nil for missing path"
    (let [res {[:role] [[:= "admin"]]}]
      (is (nil? (r/constraints-for res [:level])))))

  (testing "returns nil for non-map"
    (is (nil? (r/constraints-for nil [:role])))))

(deftest has-complex?-test
  (testing "detects complex residuals"
    (is (true? (r/has-complex? {::r/complex {:type :or}})))
    (is (false? (r/has-complex? {[:role] [[:= "admin"]]})))
    (is (false? (r/has-complex? {})))
    (is (false? (r/has-complex? nil)))))

(deftest has-cross-key?-test
  (testing "detects cross-key constraints"
    (is (true? (r/has-cross-key? {::r/cross-key [{:left [:a] :op := :right [:b]}]})))
    (is (false? (r/has-cross-key? {[:role] [[:= "admin"]]})))
    (is (false? (r/has-cross-key? {})))
    (is (false? (r/has-cross-key? nil)))))

;;; ---------------------------------------------------------------------------
;;; Transformation Tests
;;; ---------------------------------------------------------------------------

(deftest add-constraint-test
  (testing "adds to empty residual"
    (let [res (r/add-constraint {} [:role] [:= "admin"])]
      (is (= {[:role] [[:= "admin"]]} res))))

  (testing "appends to existing constraints"
    (let [res (-> {}
                  (r/add-constraint [:role] [:= "admin"])
                  (r/add-constraint [:role] [:!= "guest"]))]
      (is (= {[:role] [[:= "admin"] [:!= "guest"]]} res))))

  (testing "adds to new path"
    (let [res (-> {[:role] [[:= "admin"]]}
                  (r/add-constraint [:level] [:> 5]))]
      (is (= {[:role] [[:= "admin"]]
              [:level] [[:> 5]]} res))))

  (testing "returns nil for nil residual"
    (is (nil? (r/add-constraint nil [:role] [:= "admin"])))))

(deftest remove-path-test
  (testing "removes path from residual"
    (let [res (r/remove-path {[:role] [[:= "admin"]] [:level] [[:> 5]]} [:role])]
      (is (= {[:level] [[:> 5]]} res))))

  (testing "removing last path returns empty"
    (let [res (r/remove-path {[:role] [[:= "admin"]]} [:role])]
      (is (= {} res))
      (is (r/satisfied? res))))

  (testing "returns nil for nil residual"
    (is (nil? (r/remove-path nil [:role])))))

(deftest map-constraints-test
  (testing "transforms constraints"
    (let [res    {[:role] [[:= "admin"]]
                  [:level] [[:> 5]]}
          mapped (r/map-constraints res
                                    (fn [path constraints]
                                      [path (mapv (fn [[op v]] [op (str v "-mapped")]) constraints)]))]
      (is (= {[:role] [[:= "admin-mapped"]]
              [:level] [[:> "5-mapped"]]} mapped))))

  (testing "can filter out paths"
    (let [res      {[:role] [[:= "admin"]]
                    [:level] [[:> 5]]}
          filtered (r/map-constraints res
                                      (fn [path constraints]
                                        (when (= path [:role])
                                          [path constraints])))]
      (is (= {[:role] [[:= "admin"]]} filtered)))))

;;; ---------------------------------------------------------------------------
;;; Conversion Tests
;;; ---------------------------------------------------------------------------

(deftest residual->constraints-test
  (testing "converts residual to constraint sequence"
    (let [res         {[:role] [[:= "admin"]]
                       [:level] [[:> 5] [:< 100]]}
          constraints (r/residual->constraints res)]
      (is (= 3 (count constraints)))
      (is (some #(= {:path [:role] :op := :value "admin"} %) constraints))
      (is (some #(= {:path [:level] :op :> :value 5} %) constraints))
      (is (some #(= {:path [:level] :op :< :value 100} %) constraints))))

  (testing "returns nil for non-residual"
    (is (nil? (r/residual->constraints {})))
    (is (nil? (r/residual->constraints nil)))))

(deftest constraints->residual-test
  (testing "converts constraints to residual"
    (let [constraints [{:path [:role] :op := :value "admin"}
                       {:path [:level] :op :> :value 5}
                       {:path [:level] :op :< :value 100}]
          res         (r/constraints->residual constraints)]
      (is (= {[:role] [[:= "admin"]]
              [:level] [[:> 5] [:< 100]]} res))))

  (testing "empty constraints produces satisfied"
    (is (r/satisfied? (r/constraints->residual [])))))

(deftest roundtrip-test
  (testing "residual -> constraints -> residual"
    (let [original  {[:role] [[:= "admin"]]
                     [:level] [[:> 5]]}
          roundtrip (-> original
                        r/residual->constraints
                        r/constraints->residual)]
      (is (= original roundtrip)))))
