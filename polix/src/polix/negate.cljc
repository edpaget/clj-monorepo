(ns polix.negate
  "Policy negation through AST transformation.

  Provides pure transformation of policy ASTs to their logical negations.
  Negation enables computing what would contradict a policy, useful for
  generating counter-examples and reasoning about policy violations.

  Constraint-level negation is handled by [[polix.operators/negate-constraint]].
  This namespace handles policy-level (AST) transformations:

  - De Morgan's laws for `:and` / `:or`
  - Quantifier swapping (`:forall` ↔ `:exists`)
  - Double negation elimination
  - Delegation to operator negation for comparisons

  Non-invertible expressions produce `::complex` marker nodes."
  (:require
   [polix.ast :as ast]
   [polix.operators :as ops]))

;;; ---------------------------------------------------------------------------
;;; Complex Markers
;;; ---------------------------------------------------------------------------

(defn complex-marker
  "Creates a complex marker node for non-invertible expressions.

  Complex markers indicate that the expression cannot be meaningfully
  negated and should be treated as opaque during further processing."
  [original-node reason]
  (ast/ast-node ::complex
                {:reason reason :original original-node}
                (:position original-node)))

(defn complex?
  "Returns true if the node is a complex marker."
  [node]
  (= ::complex (:type node)))

;;; ---------------------------------------------------------------------------
;;; AST Negation
;;; ---------------------------------------------------------------------------

(declare negate)

(defn- negate-function-call
  "Negates a function call AST node.

  Handles boolean connectives with De Morgan's laws, double negation
  elimination, and delegates comparison operators to the operator registry."
  [node]
  (let [op       (:value node)
        children (:children node)]
    (case op
      ;; De Morgan's laws
      :and
      (ast/ast-node ::ast/function-call
                    :or
                    (:position node)
                    (mapv negate children)
                    (:metadata node))

      :or
      (ast/ast-node ::ast/function-call
                    :and
                    (:position node)
                    (mapv negate children)
                    (:metadata node))

      ;; Double negation elimination
      :not
      (first children)

      ;; Comparison operators - delegate to operator protocol
      (if-let [neg-op (ops/negate-op op)]
        (ast/ast-node ::ast/function-call
                      neg-op
                      (:position node)
                      children
                      (:metadata node))
        ;; Unknown operator - mark as complex
        (complex-marker node :unknown-operator)))))

(defn- negate-quantifier
  "Negates a quantifier AST node.

  Swaps `:forall` ↔ `:exists` and negates the body."
  [node]
  (let [quantifier (:value node)
        body       (first (:children node))
        neg-quant  (case quantifier
                     :forall :exists
                     :exists :forall
                     ;; Unknown quantifier
                     nil)]
    (if neg-quant
      (ast/ast-node ::ast/quantifier
                    neg-quant
                    (:position node)
                    [(negate body)]
                    (:metadata node))
      (complex-marker node :unknown-quantifier))))

(defn negate
  "Negates an AST node, returning the negated AST.

  Recursively transforms the AST according to negation rules:

  - Function calls: De Morgan for `:and`/`:or`, unwrap `:not`, delegate operators
  - Quantifiers: swap `:forall` ↔ `:exists` with negated body
  - Literals/accessors: cannot be negated, produce complex markers

  Returns a new AST node representing the negation.

  Examples (showing logical equivalence, not exact AST structure):

      ;; Comparison operators
      (negate (parse [:= :doc/role \"admin\"]))
      ;; equivalent to: [:!= :doc/role \"admin\"]

      ;; De Morgan's laws
      (negate (parse [:and [:= :doc/x 1] [:= :doc/y 2]]))
      ;; equivalent to: [:or [:!= :doc/x 1] [:!= :doc/y 2]]

      ;; Quantifier swapping
      (negate (parse [:forall [u :doc/users] [:= :u/active true]]))
      ;; equivalent to: [:exists [u :doc/users] [:!= :u/active true]]"
  [node]
  (case (:type node)
    ::ast/function-call
    (negate-function-call node)

    ::ast/quantifier
    (negate-quantifier node)

    ;; Literals and accessors cannot be meaningfully negated on their own
    ;; In a well-formed policy, these are operands to comparison operators
    ::ast/literal
    (complex-marker node :literal-cannot-negate)

    ::ast/doc-accessor
    (complex-marker node :accessor-cannot-negate)

    ;; Value functions (like :fn/count) cannot be negated
    ::ast/value-fn
    (complex-marker node :value-fn-cannot-negate)

    ;; Thunks cannot be negated (opaque runtime values)
    ::ast/thunk
    (complex-marker node :thunk-cannot-negate)

    ;; Already complex - keep as is
    ::complex
    node

    ;; Unknown node type
    (complex-marker node :unknown-node-type)))

;;; ---------------------------------------------------------------------------
;;; Utilities
;;; ---------------------------------------------------------------------------

(defn has-complex?
  "Returns true if the AST contains any complex (non-invertible) markers.

  Traverses the entire AST tree to check for complex nodes."
  [ast]
  (boolean
   (some #(complex? %)
         (tree-seq (fn [n] (or (:children n)
                               (get-in n [:metadata :binding :where])))
                   (fn [n] (concat (:children n)
                                   (when-let [w (get-in n [:metadata :binding :where])]
                                     [w])))
                   ast))))

(defn double-negate
  "Applies double negation to an AST.

  Useful for testing that negation is self-inverse. Note that double
  negation may not produce an identical AST due to simplifications
  like `:not` elimination, but should be logically equivalent."
  [ast]
  (negate (negate ast)))
