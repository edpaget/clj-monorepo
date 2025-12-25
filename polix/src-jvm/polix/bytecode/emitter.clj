(ns polix.bytecode.emitter
  "ASM bytecode emission for constraint evaluation.

  Generates JVM bytecode for evaluating constraints against documents.
  Uses ASM's MethodVisitor API to emit optimized bytecode for each
  operator type."
  (:import
   [org.objectweb.asm Opcodes MethodVisitor Label]))

(defn emit-null-check
  "Emits bytecode for null check with branch to open-residual label.

  Stack: value -> value (if not null)
  Jumps to open-label if value is null.

  Note: When called from class-generator, null checks are handled at the
  path level, so this may be a no-op in that context."
  [^MethodVisitor mv ^Label open-label]
  ;; Don't duplicate - the caller should have the value on stack
  ;; and we just need to check and potentially jump
  (.visitInsn mv Opcodes/DUP)
  (.visitJumpInsn mv Opcodes/IFNULL open-label))

(defn emit-string-equals
  "Emits bytecode for string equality check.

  Stack: value -> boolean (0 or 1)
  Pushes expected string and calls Object.equals."
  [^MethodVisitor mv ^String expected]
  (.visitLdcInsn mv expected)
  (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                    "java/lang/Object"
                    "equals"
                    "(Ljava/lang/Object;)Z"
                    false))

(defn emit-long-unbox
  "Emits bytecode to unbox a Number to long.

  Stack: Object -> long"
  [^MethodVisitor mv]
  (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/Number")
  (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                    "java/lang/Number"
                    "longValue"
                    "()J"
                    false))

(defn emit-long-compare
  "Emits bytecode for long comparison.

  Stack: value -> cmp-result (-1, 0, or 1)
  Expects value to be a Number, unboxes to long, compares with expected."
  [^MethodVisitor mv ^long expected]
  (emit-long-unbox mv)
  (.visitLdcInsn mv (Long/valueOf expected))
  (.visitInsn mv Opcodes/LCMP))

(defn emit-comparison-jump
  "Emits comparison jump based on operator.

  Stack: cmp-result (from LCMP) ->
  Jumps to conflict-label if comparison fails."
  [^MethodVisitor mv op ^Label conflict-label]
  (case op
    :>  (.visitJumpInsn mv Opcodes/IFLE conflict-label)
    :<  (.visitJumpInsn mv Opcodes/IFGE conflict-label)
    :>= (.visitJumpInsn mv Opcodes/IFLT conflict-label)
    :<= (.visitJumpInsn mv Opcodes/IFGT conflict-label)))

(defn emit-return-field
  "Emits bytecode to return a static field value.

  Loads the field and returns it."
  [^MethodVisitor mv class-name field-name field-desc]
  (.visitFieldInsn mv Opcodes/GETSTATIC class-name field-name field-desc)
  (.visitInsn mv Opcodes/ARETURN))

(defmulti emit-constraint
  "Emits bytecode for evaluating a single constraint.

  Dispatches on the operator keyword. Each method expects:
  - mv: MethodVisitor
  - constraint: {:op :key :value}
  - type-env: map of path -> type
  - labels: {:open Label :conflict Label :set-field String :pattern-field String
             :skip-null-check boolean - if true, don't emit null check (already done)}"
  (fn [_mv constraint _type-env _labels] (:op constraint)))

(defmethod emit-constraint :=
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (cond
    (string? value)
    (do
      (emit-string-equals mv value)
      (.visitJumpInsn mv Opcodes/IFEQ conflict))

    (integer? value)
    (do
      (emit-long-compare mv (long value))
      (.visitJumpInsn mv Opcodes/IFNE conflict))

    :else
    (do
      (.visitLdcInsn mv value)
      (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                        "java/lang/Object"
                        "equals"
                        "(Ljava/lang/Object;)Z"
                        false)
      (.visitJumpInsn mv Opcodes/IFEQ conflict))))

(defmethod emit-constraint :!=
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (cond
    (string? value)
    (do
      (emit-string-equals mv value)
      (.visitJumpInsn mv Opcodes/IFNE conflict))

    (integer? value)
    (do
      (emit-long-compare mv (long value))
      (.visitJumpInsn mv Opcodes/IFEQ conflict))

    :else
    (do
      (.visitLdcInsn mv value)
      (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                        "java/lang/Object"
                        "equals"
                        "(Ljava/lang/Object;)Z"
                        false)
      (.visitJumpInsn mv Opcodes/IFNE conflict))))

(defmethod emit-constraint :>
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (emit-long-compare mv (long value))
  (emit-comparison-jump mv :> conflict))

(defmethod emit-constraint :<
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (emit-long-compare mv (long value))
  (emit-comparison-jump mv :< conflict))

(defmethod emit-constraint :>=
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (emit-long-compare mv (long value))
  (emit-comparison-jump mv :>= conflict))

(defmethod emit-constraint :<=
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (emit-long-compare mv (long value))
  (emit-comparison-jump mv :<= conflict))

(defmethod emit-constraint :in
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict set-field class-name skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (.visitFieldInsn mv Opcodes/GETSTATIC class-name set-field "Lclojure/lang/IPersistentSet;")
  (.visitInsn mv Opcodes/SWAP)
  (.visitMethodInsn mv Opcodes/INVOKEINTERFACE
                    "clojure/lang/IPersistentSet"
                    "contains"
                    "(Ljava/lang/Object;)Z"
                    true)
  (.visitJumpInsn mv Opcodes/IFEQ conflict))

(defmethod emit-constraint :not-in
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict set-field class-name skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (.visitFieldInsn mv Opcodes/GETSTATIC class-name set-field "Lclojure/lang/IPersistentSet;")
  (.visitInsn mv Opcodes/SWAP)
  (.visitMethodInsn mv Opcodes/INVOKEINTERFACE
                    "clojure/lang/IPersistentSet"
                    "contains"
                    "(Ljava/lang/Object;)Z"
                    true)
  (.visitJumpInsn mv Opcodes/IFNE conflict))

(defmethod emit-constraint :matches
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict pattern-field class-name skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/CharSequence")
  (.visitFieldInsn mv Opcodes/GETSTATIC class-name pattern-field "Ljava/util/regex/Pattern;")
  (.visitInsn mv Opcodes/SWAP)
  (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                    "java/util/regex/Pattern"
                    "matcher"
                    "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"
                    false)
  (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                    "java/util/regex/Matcher"
                    "matches"
                    "()Z"
                    false)
  (.visitJumpInsn mv Opcodes/IFEQ conflict))

(defmethod emit-constraint :not-matches
  [^MethodVisitor mv {:keys [value]} _type-env {:keys [open conflict pattern-field class-name skip-null-check]}]
  (when-not skip-null-check
    (emit-null-check mv open))
  (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/CharSequence")
  (.visitFieldInsn mv Opcodes/GETSTATIC class-name pattern-field "Ljava/util/regex/Pattern;")
  (.visitInsn mv Opcodes/SWAP)
  (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                    "java/util/regex/Pattern"
                    "matcher"
                    "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"
                    false)
  (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                    "java/util/regex/Matcher"
                    "matches"
                    "()Z"
                    false)
  (.visitJumpInsn mv Opcodes/IFNE conflict))

(defmethod emit-constraint :default
  [_mv constraint _type-env _labels]
  (throw (ex-info "Unsupported operator for bytecode compilation"
                  {:op (:op constraint)
                   :constraint constraint})))

(defn emit-version-guard
  "Emits bytecode for registry version check with fallback.

  If the current registry version doesn't match the compiled version,
  jumps to the fallback label for interpreted evaluation."
  [^MethodVisitor mv ^String class-name ^long compiled-version ^Label fallback-label]
  (.visitMethodInsn mv Opcodes/INVOKESTATIC
                    "polix/operators"
                    "registry_version"
                    "()Ljava/lang/Object;"
                    false)
  (.visitTypeInsn mv Opcodes/CHECKCAST "java/lang/Number")
  (.visitMethodInsn mv Opcodes/INVOKEVIRTUAL
                    "java/lang/Number"
                    "longValue"
                    "()J"
                    false)
  (.visitLdcInsn mv (Long/valueOf compiled-version))
  (.visitInsn mv Opcodes/LCMP)
  (.visitJumpInsn mv Opcodes/IFNE fallback-label))
