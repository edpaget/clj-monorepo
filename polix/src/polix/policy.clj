(ns polix.policy
  "Policy definition and management.

  Provides the `defpolicy` macro for defining declarative policies and the
  `Policy` record for representing compiled policies."
  (:require
   [cats.core :as m]
   [cats.monad.either :as either]
   [polix.parser :as parser]))

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
        ast (-> (parser/parse-policy expr)
                (either/branch-left
                 (fn [error]
                   (throw (ex-info (str "Policy parse error: " (:message error))
                                   (assoc error :policy-name name)))))
                (m/extract))
        schema (parser/extract-doc-keys ast)]
    `(def ~name
       ~@(when docstring [docstring])
       (->Policy '~name ~docstring ~schema '~ast))))
