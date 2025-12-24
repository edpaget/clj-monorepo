(ns polix.bytecode.quantifiers
  "Bytecode emission for quantifier expressions (forall, exists, count).

  Generates tight JVM loops that iterate over collections without
  Clojure seq abstraction overhead."
  (:require
   [polix.optimized.analyzer :as analyzer])
  (:import
   [org.objectweb.asm MethodVisitor Opcodes Label]))

;;; ---------------------------------------------------------------------------
;;; AST Extraction Helpers
;;; ---------------------------------------------------------------------------

(defn- extract-constraints-from-body
  "Extracts constraint info from a quantifier body AST.

  Returns a vector of {:op :path :value} maps for each constraint."
  [body-node]
  (case (:value body-node)
    :and
    (mapv (fn [child]
            {:op (:value child)
             :path (get-in child [:children 0 :value])
             :value (get-in child [:children 1 :value])
             :binding-ns (get-in child [:children 0 :metadata :binding-ns])})
          (:children body-node))

    ;; Single constraint
    [{:op (:value body-node)
      :path (get-in body-node [:children 0 :value])
      :value (get-in body-node [:children 1 :value])
      :binding-ns (get-in body-node [:children 0 :metadata :binding-ns])}]))

(defn- emit-create-keyword
  "Emits bytecode to create a keyword."
  [^MethodVisitor mv kw]
  (if-let [ns (namespace kw)]
    (.visitLdcInsn mv ns)
    (.visitInsn mv Opcodes/ACONST_NULL))
  (.visitLdcInsn mv (name kw))
  (.visitMethodInsn mv Opcodes/INVOKESTATIC
                    "clojure/lang/Keyword"
                    "intern"
                    "(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Keyword;"
                    false))

(defn- emit-element-path-access
  "Emits bytecode to access a path on the current element.

  elem-local contains the element object."
  [^MethodVisitor mv elem-local path]
  (.visitVarInsn mv Opcodes/ALOAD elem-local)
  (doseq [segment path]
    (emit-create-keyword mv segment)
    (.visitMethodInsn mv Opcodes/INVOKESTATIC
                      "clojure/lang/RT"
                      "get"
                      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                      false)))

(defn- emit-constraint-check
  "Emits bytecode to check a single constraint against value on stack.

  Jumps to fail-label if constraint fails."
  [^MethodVisitor mv {:keys [op value]} ^Label fail-label]
  (case op
    :=
    (cond
      (string? value)
      (do
        (.visitLdcInsn mv value)
        (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                          "java/lang/Object" "equals"
                          "(Ljava/lang/Object;)Z" false)
        (.visitJumpInsn mv Opcodes/IFEQ fail-label))

      (instance? Boolean value)
      (do
        (.visitFieldInsn mv Opcodes/GETSTATIC
                         "java/lang/Boolean"
                         (if value "TRUE" "FALSE")
                         "Ljava/lang/Boolean;")
        (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                          "java/lang/Object" "equals"
                          "(Ljava/lang/Object;)Z" false)
        (.visitJumpInsn mv Opcodes/IFEQ fail-label))

      (integer? value)
      (do
        (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/Number")
        (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                          "java/lang/Number" "longValue" "()J" false)
        (.visitLdcInsn mv (Long/valueOf (long value)))
        (.visitInsn mv Opcodes/LCMP)
        (.visitJumpInsn mv Opcodes/IFNE fail-label)))

    :!=
    (cond
      (string? value)
      (do
        (.visitLdcInsn mv value)
        (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                          "java/lang/Object" "equals"
                          "(Ljava/lang/Object;)Z" false)
        (.visitJumpInsn mv Opcodes/IFNE fail-label)))

    :>
    (do
      (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/Number")
      (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                        "java/lang/Number" "longValue" "()J" false)
      (.visitLdcInsn mv (Long/valueOf (long value)))
      (.visitInsn mv Opcodes/LCMP)
      (.visitJumpInsn mv Opcodes/IFLE fail-label))

    :<
    (do
      (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/Number")
      (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                        "java/lang/Number" "longValue" "()J" false)
      (.visitLdcInsn mv (Long/valueOf (long value)))
      (.visitInsn mv Opcodes/LCMP)
      (.visitJumpInsn mv Opcodes/IFGE fail-label))

    :>=
    (do
      (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/Number")
      (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                        "java/lang/Number" "longValue" "()J" false)
      (.visitLdcInsn mv (Long/valueOf (long value)))
      (.visitInsn mv Opcodes/LCMP)
      (.visitJumpInsn mv Opcodes/IFLT fail-label))

    :<=
    (do
      (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/Number")
      (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                        "java/lang/Number" "longValue" "()J" false)
      (.visitLdcInsn mv (Long/valueOf (long value)))
      (.visitInsn mv Opcodes/LCMP)
      (.visitJumpInsn mv Opcodes/IFGT fail-label))))

;;; ---------------------------------------------------------------------------
;;; Forall Emission
;;; ---------------------------------------------------------------------------

(defn emit-forall
  "Emits bytecode for a forall quantifier.

  Structure:
  1. Get collection from document
  2. Null check -> return open residual
  3. Get iterator
  4. Loop: check hasNext, get next, check constraints
  5. If any constraint fails -> return conflict
  6. After loop -> continue or return satisfied

  Locals used:
  - 1: document
  - 2: collection
  - 3: iterator
  - 4: current element

  Context options:
  - `:continue-label` - if provided, jump here on success instead of returning"
  [^MethodVisitor mv ^String class-name quantifier-ast
   {:keys [open-field satisfied-field conflict-field continue-label]}]
  (let [body (first (:children quantifier-ast))
        binding (get-in quantifier-ast [:metadata :binding])
        coll-path (:path binding)
        constraints (extract-constraints-from-body body)
        null-label (Label.)
        loop-start (Label.)
        loop-end (Label.)
        conflict-label (Label.)
        coll-local 2
        iter-local 3
        elem-local 4]

    ;; Get collection from document
    (.visitVarInsn mv Opcodes/ALOAD 1)  ; document
    (doseq [segment coll-path]
      (emit-create-keyword mv segment)
      (.visitMethodInsn mv Opcodes/INVOKESTATIC
                        "clojure/lang/RT" "get"
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                        false))
    (.visitVarInsn mv Opcodes/ASTORE coll-local)

    ;; Null check on collection
    (.visitVarInsn mv Opcodes/ALOAD coll-local)
    (.visitJumpInsn mv Opcodes/IFNULL null-label)

    ;; Get iterator: RT.iter(collection)
    (.visitVarInsn mv Opcodes/ALOAD coll-local)
    (.visitMethodInsn mv Opcodes/INVOKESTATIC
                      "clojure/lang/RT" "iter"
                      "(Ljava/lang/Object;)Ljava/util/Iterator;"
                      false)
    (.visitVarInsn mv Opcodes/ASTORE iter-local)

    ;; Loop start
    (.visitLabel mv loop-start)

    ;; Check hasNext
    (.visitVarInsn mv Opcodes/ALOAD iter-local)
    (.visitMethodInsn mv Opcodes/INVOKEINTERFACE
                      "java/util/Iterator" "hasNext" "()Z" true)
    (.visitJumpInsn mv Opcodes/IFEQ loop-end)

    ;; Get next element
    (.visitVarInsn mv Opcodes/ALOAD iter-local)
    (.visitMethodInsn mv Opcodes/INVOKEINTERFACE
                      "java/util/Iterator" "next"
                      "()Ljava/lang/Object;" true)
    (.visitVarInsn mv Opcodes/ASTORE elem-local)

    ;; Check each constraint
    (doseq [constraint constraints]
      (let [elem-null-label (Label.)
            continue-label (Label.)]
        ;; Get value from element
        (emit-element-path-access mv elem-local (:path constraint))
        ;; Null check on element field
        (.visitInsn mv Opcodes/DUP)
        (.visitJumpInsn mv Opcodes/IFNULL elem-null-label)
        ;; Check constraint
        (emit-constraint-check mv constraint conflict-label)
        (.visitJumpInsn mv Opcodes/GOTO continue-label)
        ;; Element field is null -> treat as open (fail forall)
        (.visitLabel mv elem-null-label)
        (.visitInsn mv Opcodes/POP)
        (.visitJumpInsn mv Opcodes/GOTO null-label)
        (.visitLabel mv continue-label)))

    ;; Continue loop
    (.visitJumpInsn mv Opcodes/GOTO loop-start)

    ;; Loop end: all elements passed -> continue or return satisfied
    (.visitLabel mv loop-end)
    (if continue-label
      (.visitJumpInsn mv Opcodes/GOTO continue-label)
      (do
        (.visitFieldInsn mv Opcodes/GETSTATIC class-name satisfied-field
                         "Ljava/lang/Object;")
        (.visitInsn mv Opcodes/ARETURN)))

    ;; Null/open: return open residual
    (.visitLabel mv null-label)
    (.visitFieldInsn mv Opcodes/GETSTATIC class-name open-field
                     "Ljava/lang/Object;")
    (.visitInsn mv Opcodes/ARETURN)

    ;; Conflict: return conflict residual
    (.visitLabel mv conflict-label)
    (.visitFieldInsn mv Opcodes/GETSTATIC class-name conflict-field
                     "Ljava/lang/Object;")
    (.visitInsn mv Opcodes/ARETURN)))

;;; ---------------------------------------------------------------------------
;;; Exists Emission
;;; ---------------------------------------------------------------------------

(defn emit-exists
  "Emits bytecode for an exists quantifier.

  Structure:
  1. Get collection from document
  2. Null check -> return open residual
  3. Get iterator
  4. Loop: check hasNext, get next, check constraints
  5. If all constraints pass for any element -> continue or return satisfied
  6. After loop with no match -> return conflict

  Context options:
  - `:continue-label` - if provided, jump here on success instead of returning"
  [^MethodVisitor mv ^String class-name quantifier-ast
   {:keys [open-field satisfied-field conflict-field continue-label]}]
  (let [body (first (:children quantifier-ast))
        binding (get-in quantifier-ast [:metadata :binding])
        coll-path (:path binding)
        constraints (extract-constraints-from-body body)
        null-label (Label.)
        loop-start (Label.)
        loop-end (Label.)
        match-found (Label.)
        coll-local 2
        iter-local 3
        elem-local 4]

    ;; Get collection from document
    (.visitVarInsn mv Opcodes/ALOAD 1)  ; document
    (doseq [segment coll-path]
      (emit-create-keyword mv segment)
      (.visitMethodInsn mv Opcodes/INVOKESTATIC
                        "clojure/lang/RT" "get"
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                        false))
    (.visitVarInsn mv Opcodes/ASTORE coll-local)

    ;; Null check on collection
    (.visitVarInsn mv Opcodes/ALOAD coll-local)
    (.visitJumpInsn mv Opcodes/IFNULL null-label)

    ;; Get iterator
    (.visitVarInsn mv Opcodes/ALOAD coll-local)
    (.visitMethodInsn mv Opcodes/INVOKESTATIC
                      "clojure/lang/RT" "iter"
                      "(Ljava/lang/Object;)Ljava/util/Iterator;"
                      false)
    (.visitVarInsn mv Opcodes/ASTORE iter-local)

    ;; Loop start
    (.visitLabel mv loop-start)

    ;; Check hasNext
    (.visitVarInsn mv Opcodes/ALOAD iter-local)
    (.visitMethodInsn mv Opcodes/INVOKEINTERFACE
                      "java/util/Iterator" "hasNext" "()Z" true)
    (.visitJumpInsn mv Opcodes/IFEQ loop-end)

    ;; Get next element
    (.visitVarInsn mv Opcodes/ALOAD iter-local)
    (.visitMethodInsn mv Opcodes/INVOKEINTERFACE
                      "java/util/Iterator" "next"
                      "()Ljava/lang/Object;" true)
    (.visitVarInsn mv Opcodes/ASTORE elem-local)

    ;; Check constraints - if all pass, jump to match-found
    ;; If any fails, continue to next element
    (let [next-elem (Label.)]
      (doseq [constraint constraints]
        (let [elem-null-label (Label.)
              constraint-pass (Label.)]
          ;; Get value from element
          (emit-element-path-access mv elem-local (:path constraint))
          ;; Null check
          (.visitInsn mv Opcodes/DUP)
          (.visitJumpInsn mv Opcodes/IFNULL elem-null-label)
          ;; Check constraint - if fails, go to next element
          (emit-constraint-check mv constraint next-elem)
          ;; Constraint passed - continue to next constraint
          (.visitJumpInsn mv Opcodes/GOTO constraint-pass)
          ;; Element field is null -> fail this element, try next
          (.visitLabel mv elem-null-label)
          (.visitInsn mv Opcodes/POP)
          (.visitJumpInsn mv Opcodes/GOTO next-elem)
          ;; Continue point for this constraint
          (.visitLabel mv constraint-pass)))

      ;; All constraints passed for this element
      (.visitJumpInsn mv Opcodes/GOTO match-found)

      (.visitLabel mv next-elem))

    ;; Continue to next element
    (.visitJumpInsn mv Opcodes/GOTO loop-start)

    ;; Match found: continue or return satisfied
    (.visitLabel mv match-found)
    (if continue-label
      (.visitJumpInsn mv Opcodes/GOTO continue-label)
      (do
        (.visitFieldInsn mv Opcodes/GETSTATIC class-name satisfied-field
                         "Ljava/lang/Object;")
        (.visitInsn mv Opcodes/ARETURN)))

    ;; Loop end (no match): return conflict
    (.visitLabel mv loop-end)
    (.visitFieldInsn mv Opcodes/GETSTATIC class-name conflict-field
                     "Ljava/lang/Object;")
    (.visitInsn mv Opcodes/ARETURN)

    ;; Null/open: return open residual
    (.visitLabel mv null-label)
    (.visitFieldInsn mv Opcodes/GETSTATIC class-name open-field
                     "Ljava/lang/Object;")
    (.visitInsn mv Opcodes/ARETURN)))

;;; ---------------------------------------------------------------------------
;;; Quantifier Dispatch
;;; ---------------------------------------------------------------------------

(defn emit-quantifier
  "Emits bytecode for a quantifier based on its type."
  [^MethodVisitor mv ^String class-name quantifier-ast ctx]
  (case (:value quantifier-ast)
    :forall (emit-forall mv class-name quantifier-ast ctx)
    :exists (emit-exists mv class-name quantifier-ast ctx)))
