(ns polix.policy
  "Policy definition, management, and analysis.

  Provides the `defpolicy` macro for defining declarative policies, the
  `Policy` record for representing compiled policies, and analysis functions
  for extracting parameter requirements and document paths.

  ## Analysis Example

      (require '[polix.policy :as policy])

      ;; Analyze a policy's requirements
      (policy/analyze-policy [:and [:= :doc/role :param/role]
                                   [:> :doc/level :param/min]])
      ;; => {:params #{:role :min}
      ;;     :doc-keys #{[:role] [:level]}
      ;;     :parameterized? true}"
  (:require
   [polix.parser :as parser]
   [polix.result :as r]))

(defrecord Policy [name docstring schema ast])

(defmacro defpolicy
  "Defines a policy with a `name`, optional `docstring`, and policy expression.

  A policy is a declarative rule that evaluates to boolean true/false.
  The macro parses the policy expression into an AST and extracts the
  required document schema.

  Examples:

      (defpolicy MyPolicy
        \"Optional docstring\"
        [:= :doc/actor-role \"admin\"])

      (defpolicy AnotherPolicy
        [:or [:= :doc/role \"admin\"]
             [:= :doc/role \"user\"]])

  Returns a `def` form that creates a [[Policy]] record, or throws on parse error."
  [name & args]
  (let [[docstring expr] (if (string? (first args))
                           [(first args) (second args)]
                           [nil (first args)])
        parse-result     (parser/parse-policy expr)
        _                (when (r/error? parse-result)
                           (let [error (r/unwrap parse-result)]
                             (throw (ex-info (str "Policy parse error: " (:message error))
                                             (assoc error :policy-name name)))))
        ast              (r/unwrap parse-result)
        schema           (parser/extract-doc-keys ast)]
    `(def ~name
       ~@(when docstring [docstring])
       (->Policy '~name ~docstring ~schema '~ast))))

;;; ---------------------------------------------------------------------------
;;; Policy Analysis
;;; ---------------------------------------------------------------------------

(defn analyze-policy
  "Analyzes a policy to determine its requirements and characteristics.

  Takes a policy expression or AST. Returns a map with:

  - `:params` — set of required parameter keys
  - `:doc-keys` — set of document paths accessed
  - `:parameterized?` — true if policy requires any params

  This analysis operates on the policy structure alone without resolving
  policy references. For full analysis including referenced policies,
  pass a registry to the 2-arity version.

      (analyze-policy [:= :doc/role :param/role])
      ;=> {:params #{:role}
      ;    :doc-keys #{[:role]}
      ;    :parameterized? true}

      (analyze-policy [:and [:= :doc/role \"admin\"]
                            [:> :doc/level 5]])
      ;=> {:params #{}
      ;    :doc-keys #{[:role] [:level]}
      ;    :parameterized? false}"
  ([policy]
   (analyze-policy policy nil))
  ([policy _registry]
   (let [ast      (if (and (map? policy) (:type policy))
                    policy
                    (r/unwrap (parser/parse-policy policy)))
         params   (parser/extract-param-keys ast)
         doc-keys (parser/extract-doc-keys ast)]
     {:params params
      :doc-keys doc-keys
      :parameterized? (boolean (seq params))})))

(defn required-params
  "Returns the set of required parameter keys for a policy.

  Convenience function that calls [[analyze-policy]] and extracts `:params`.

      (required-params [:= :doc/role :param/role])
      ;=> #{:role}

      (required-params [:= :doc/role \"admin\"])
      ;=> #{}"
  ([policy]
   (:params (analyze-policy policy)))
  ([policy registry]
   (:params (analyze-policy policy registry))))
