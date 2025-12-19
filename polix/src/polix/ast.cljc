(ns polix.ast
  "Abstract Syntax Tree (AST) data structures for policy expressions.

  Provides the core AST node representation used throughout policy parsing
  and evaluation.")

(defrecord ASTNode [type value position children])

(defn ast-node
  "Creates an AST node with position tracking.

  Takes a `type` (one of `::literal`, `::doc-accessor`, `::uri`, `::function-call`,
  or `::thunk`), a `value`, a `position` vector `[start-index end-index]` in the
  original expression, and optional `children` (vector of child AST nodes).

  Returns an `ASTNode` record."
  ([type value position]
   (->ASTNode type value position nil))
  ([type value position children]
   (->ASTNode type value position children)))
