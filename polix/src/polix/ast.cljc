(ns polix.ast
  "Abstract Syntax Tree (AST) data structures for policy expressions.

  Provides the core AST node representation used throughout policy parsing
  and evaluation.")

(defrecord ASTNode [type value position children metadata])

(defn ast-node
  "Creates an AST node with position tracking.

  Takes a `type` (one of `::literal`, `::doc-accessor`, `::quantifier`,
  `::function-call`, or `::thunk`), a `value`, a `position` vector
  `[start-index end-index]` in the original expression, optional `children`
  (vector of child AST nodes), and optional `metadata` map for additional
  node-specific data like quantifier bindings.

  Returns an `ASTNode` record."
  ([type value position]
   (->ASTNode type value position nil nil))
  ([type value position children]
   (->ASTNode type value position children nil))
  ([type value position children metadata]
   (->ASTNode type value position children metadata)))
