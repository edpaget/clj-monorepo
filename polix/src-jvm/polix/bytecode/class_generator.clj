(ns polix.bytecode.class-generator
  "Generates JVM bytecode for compiled policy evaluation.

  Creates classes implementing clojure.lang.IFn that evaluate constraints
  directly in bytecode, avoiding Clojure dispatch overhead."
  (:require
   [clojure.string :as str]
   [polix.bytecode.emitter :as emit]
   [polix.optimized.analyzer :as analyzer])
  (:import
   [org.objectweb.asm ClassWriter MethodVisitor Opcodes Label Type]
   [clojure.lang IFn RT Keyword DynamicClassLoader]
   [java.util.regex Pattern]))

(def ^:private class-counter
  "Counter for generating unique class names."
  (atom 0))

(defn- next-class-name
  "Generates a unique internal class name."
  []
  (str "polix/compiled/Policy$" (swap! class-counter inc)))

(defn- keyword->safe-name
  "Converts a keyword to a safe identifier string."
  [kw]
  (if-let [ns (namespace kw)]
    (str ns "_" (name kw))
    (name kw)))

(defn- path->field-name
  "Converts a path to a valid Java field name."
  [path]
  (-> (str/join "_" (map keyword->safe-name path))
      (str/replace #"[^a-zA-Z0-9_]" "_")))

(defn- constraint->field-base
  "Generates a base field name for constraint-related fields."
  [path idx]
  (str (path->field-name path) "_" idx))

(defn- generate-default-constructor
  "Generates a default no-arg constructor."
  [^ClassWriter cw]
  (let [mv (.visitMethod cw Opcodes/ACC_PUBLIC "<init>" "()V" nil nil)]
    (.visitCode mv)
    (.visitVarInsn mv Opcodes/ALOAD 0)
    (.visitMethodInsn mv Opcodes/INVOKESPECIAL "java/lang/Object" "<init>" "()V" false)
    (.visitInsn mv Opcodes/RETURN)
    (.visitMaxs mv 1 1)
    (.visitEnd mv)))

(defn- generate-static-fields
  "Generates static fields for pre-computed residuals and values."
  [^ClassWriter cw constraint-set]
  ;; SATISFIED field
  (.visitField cw
               (bit-or Opcodes/ACC_PRIVATE Opcodes/ACC_STATIC Opcodes/ACC_FINAL)
               "SATISFIED"
               "Ljava/lang/Object;"
               nil nil)

  ;; For each path, generate OPEN and CONFLICT_TEMPLATE fields
  (doseq [[path constraints] constraint-set
          :when (vector? path)]
    (let [field-base (path->field-name path)]
      ;; Open residual field
      (.visitField cw
                   (bit-or Opcodes/ACC_PRIVATE Opcodes/ACC_STATIC Opcodes/ACC_FINAL)
                   (str "OPEN_" field-base)
                   "Ljava/lang/Object;"
                   nil nil)

      ;; Path keywords array
      (.visitField cw
                   (bit-or Opcodes/ACC_PRIVATE Opcodes/ACC_STATIC Opcodes/ACC_FINAL)
                   (str "PATH_" field-base)
                   "[Ljava/lang/Object;"
                   nil nil)

      ;; Conflict template for each constraint
      (doseq [idx (range (count constraints))]
        (.visitField cw
                     (bit-or Opcodes/ACC_PRIVATE Opcodes/ACC_STATIC Opcodes/ACC_FINAL)
                     (str "CONFLICT_" field-base "_" idx)
                     "[Ljava/lang/Object;"
                     nil nil))

      ;; Set fields for :in/:not-in operators
      (doseq [[idx c] (map-indexed vector constraints)
              :when (#{:in :not-in} (:op c))]
        (.visitField cw
                     (bit-or Opcodes/ACC_PRIVATE Opcodes/ACC_STATIC Opcodes/ACC_FINAL)
                     (str "SET_" field-base "_" idx)
                     "Lclojure/lang/IPersistentSet;"
                     nil nil))

      ;; Pattern fields for :matches/:not-matches operators
      (doseq [[idx c] (map-indexed vector constraints)
              :when (#{:matches :not-matches} (:op c))]
        (.visitField cw
                     (bit-or Opcodes/ACC_PRIVATE Opcodes/ACC_STATIC Opcodes/ACC_FINAL)
                     (str "PATTERN_" field-base "_" idx)
                     "Ljava/util/regex/Pattern;"
                     nil nil)))))

(defn- emit-create-keyword-from-kw
  "Emits bytecode to create a keyword from an existing keyword.

  Preserves namespace if present."
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

(defn- emit-create-keyword
  "Emits bytecode to create a non-namespaced keyword from a string."
  [^MethodVisitor mv ^String s]
  (.visitInsn mv Opcodes/ACONST_NULL)  ; namespace = null
  (.visitLdcInsn mv s)                  ; name = s
  (.visitMethodInsn mv Opcodes/INVOKESTATIC
                    "clojure/lang/Keyword"
                    "intern"
                    "(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Keyword;"
                    false))

(defn- emit-create-vector
  "Emits bytecode to create a persistent vector from items on stack."
  [^MethodVisitor mv items]
  ;; Create array
  (.visitLdcInsn mv (int (count items)))
  (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")

  ;; Store each item
  (doseq [[idx _] (map-indexed vector items)]
    (.visitInsn mv Opcodes/DUP)
    (.visitLdcInsn mv (int idx)))

  ;; Items are already on stack in reverse, need different approach
  ;; Actually, let's use RT.vector(Object...)
  (.visitMethodInsn mv Opcodes/INVOKESTATIC
                    "clojure/lang/RT"
                    "vector"
                    "([Ljava/lang/Object;)Lclojure/lang/IPersistentVector;"
                    false))

(defn- generate-clinit
  "Generates the static initializer to set up pre-computed values."
  [^ClassWriter cw ^String class-name constraint-set]
  (let [mv (.visitMethod cw Opcodes/ACC_STATIC "<clinit>" "()V" nil nil)]
    (.visitCode mv)

    ;; SATISFIED = PersistentArrayMap.EMPTY
    (.visitFieldInsn mv Opcodes/GETSTATIC
                     "clojure/lang/PersistentArrayMap"
                     "EMPTY"
                     "Lclojure/lang/PersistentArrayMap;")
    (.visitFieldInsn mv Opcodes/PUTSTATIC class-name "SATISFIED" "Ljava/lang/Object;")

    ;; For each path
    (doseq [[path constraints] constraint-set
            :when (vector? path)]
      (let [field-base (path->field-name path)]

        ;; PATH_xxx = keyword array (for document access)
        (.visitLdcInsn mv (int (count path)))
        (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
        (doseq [[idx kw] (map-indexed vector path)]
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int idx))
          (emit-create-keyword-from-kw mv kw)
          (.visitInsn mv Opcodes/AASTORE))
        (.visitFieldInsn mv Opcodes/PUTSTATIC class-name
                         (str "PATH_" field-base) "[Ljava/lang/Object;")

        ;; Create path vector for residuals
        (.visitLdcInsn mv (int (count path)))
        (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
        (doseq [[idx kw] (map-indexed vector path)]
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int idx))
          (emit-create-keyword-from-kw mv kw)
          (.visitInsn mv Opcodes/AASTORE))
        (.visitMethodInsn mv Opcodes/INVOKESTATIC
                          "clojure/lang/RT"
                          "vector"
                          "([Ljava/lang/Object;)Lclojure/lang/IPersistentVector;"
                          false)
        ;; Store path-vec in local 0 for reuse
        (.visitVarInsn mv Opcodes/ASTORE 0)

        ;; Initialize SET_ fields FIRST (before they're referenced in OPEN_/CONFLICT_ templates)
        (doseq [[idx c] (map-indexed vector constraints)
                :when (#{:in :not-in} (:op c))]
          ;; Create set from value
          (let [s (:value c)
                items (vec s)]
            (.visitLdcInsn mv (int (count items)))
            (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
            (doseq [[i item] (map-indexed vector items)]
              (.visitInsn mv Opcodes/DUP)
              (.visitLdcInsn mv (int i))
              (cond
                (string? item) (.visitLdcInsn mv item)
                (integer? item) (do
                                  (.visitLdcInsn mv (Long/valueOf (long item)))
                                  (.visitMethodInsn mv Opcodes/INVOKESTATIC
                                                    "java/lang/Long" "valueOf"
                                                    "(J)Ljava/lang/Long;" false))
                :else (.visitLdcInsn mv item))
              (.visitInsn mv Opcodes/AASTORE))
            (.visitMethodInsn mv Opcodes/INVOKESTATIC
                              "clojure/lang/PersistentHashSet"
                              "create"
                              "([Ljava/lang/Object;)Lclojure/lang/PersistentHashSet;"
                              false)
            (.visitFieldInsn mv Opcodes/PUTSTATIC class-name
                             (str "SET_" field-base "_" idx)
                             "Lclojure/lang/IPersistentSet;")))

        ;; Initialize PATTERN_ fields
        (doseq [[idx c] (map-indexed vector constraints)
                :when (#{:matches :not-matches} (:op c))]
          (.visitLdcInsn mv (str (:value c)))
          (.visitMethodInsn mv Opcodes/INVOKESTATIC
                            "java/util/regex/Pattern"
                            "compile"
                            "(Ljava/lang/String;)Ljava/util/regex/Pattern;"
                            false)
          (.visitFieldInsn mv Opcodes/PUTSTATIC class-name
                           (str "PATTERN_" field-base "_" idx)
                           "Ljava/util/regex/Pattern;"))

        ;; OPEN_xxx = {path-vec [constraints...]}
        ;; Create constraint forms vector (SET_/PATTERN_ fields now available)
        (.visitLdcInsn mv (int (count constraints)))
        (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
        (doseq [[idx c] (map-indexed vector constraints)]
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int idx))
          ;; Create [op value] vector
          (.visitLdcInsn mv (int 2))
          (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int 0))
          (emit-create-keyword mv (name (:op c)))
          (.visitInsn mv Opcodes/AASTORE)
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int 1))
          ;; Push the value - handle sets by loading from SET_ field
          (let [v (:value c)]
            (cond
              (string? v) (.visitLdcInsn mv v)
              (integer? v) (do
                             (.visitLdcInsn mv (Long/valueOf (long v)))
                             (.visitMethodInsn mv Opcodes/INVOKESTATIC
                                               "java/lang/Long" "valueOf"
                                               "(J)Ljava/lang/Long;" false))
              (instance? Boolean v) (.visitFieldInsn mv Opcodes/GETSTATIC
                                                     "java/lang/Boolean"
                                                     (if v "TRUE" "FALSE")
                                                     "Ljava/lang/Boolean;")
              (set? v) (.visitFieldInsn mv Opcodes/GETSTATIC class-name
                                        (str "SET_" field-base "_" idx)
                                        "Lclojure/lang/IPersistentSet;")
              (instance? java.util.regex.Pattern v) (.visitFieldInsn mv Opcodes/GETSTATIC class-name
                                                                     (str "PATTERN_" field-base "_" idx)
                                                                     "Ljava/util/regex/Pattern;")
              :else (throw (ex-info "Unsupported value type in bytecode"
                                    {:value v :type (type v)}))))
          (.visitInsn mv Opcodes/AASTORE)
          (.visitMethodInsn mv Opcodes/INVOKESTATIC
                            "clojure/lang/RT"
                            "vector"
                            "([Ljava/lang/Object;)Lclojure/lang/IPersistentVector;"
                            false)
          (.visitInsn mv Opcodes/AASTORE))
        (.visitMethodInsn mv Opcodes/INVOKESTATIC
                          "clojure/lang/RT"
                          "vector"
                          "([Ljava/lang/Object;)Lclojure/lang/IPersistentVector;"
                          false)
        ;; constraint-forms-vec in local 1
        (.visitVarInsn mv Opcodes/ASTORE 1)

        ;; Build open residual map: {path-vec constraint-forms-vec}
        (.visitLdcInsn mv (int 2))
        (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
        (.visitInsn mv Opcodes/DUP)
        (.visitLdcInsn mv (int 0))
        (.visitVarInsn mv Opcodes/ALOAD 0)
        (.visitInsn mv Opcodes/AASTORE)
        (.visitInsn mv Opcodes/DUP)
        (.visitLdcInsn mv (int 1))
        (.visitVarInsn mv Opcodes/ALOAD 1)
        (.visitInsn mv Opcodes/AASTORE)
        (.visitMethodInsn mv Opcodes/INVOKESTATIC
                          "clojure/lang/PersistentArrayMap"
                          "createAsIfByAssoc"
                          "([Ljava/lang/Object;)Lclojure/lang/PersistentArrayMap;"
                          false)
        (.visitFieldInsn mv Opcodes/PUTSTATIC class-name
                         (str "OPEN_" field-base) "Ljava/lang/Object;")

        ;; CONFLICT_xxx_N templates for each constraint (after SET_/PATTERN_ are initialized)
        (doseq [[idx c] (map-indexed vector constraints)]
          ;; Create template: [path-vec, constraint-form]
          (.visitLdcInsn mv (int 2))
          (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int 0))
          (.visitVarInsn mv Opcodes/ALOAD 0) ; path-vec
          (.visitInsn mv Opcodes/AASTORE)
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int 1))
          ;; Create constraint form [op value]
          (.visitLdcInsn mv (int 2))
          (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int 0))
          (emit-create-keyword mv (name (:op c)))
          (.visitInsn mv Opcodes/AASTORE)
          (.visitInsn mv Opcodes/DUP)
          (.visitLdcInsn mv (int 1))
          (let [v (:value c)]
            (cond
              (string? v) (.visitLdcInsn mv v)
              (integer? v) (do
                             (.visitLdcInsn mv (Long/valueOf (long v)))
                             (.visitMethodInsn mv Opcodes/INVOKESTATIC
                                               "java/lang/Long" "valueOf"
                                               "(J)Ljava/lang/Long;" false))
              (set? v) (do
                         ;; Load the set from the SET_ field (now initialized)
                         (.visitFieldInsn mv Opcodes/GETSTATIC class-name
                                          (str "SET_" field-base "_" idx)
                                          "Lclojure/lang/IPersistentSet;"))
              (instance? Boolean v) (.visitFieldInsn mv Opcodes/GETSTATIC
                                                     "java/lang/Boolean"
                                                     (if v "TRUE" "FALSE")
                                                     "Ljava/lang/Boolean;")
              :else (.visitLdcInsn mv v)))
          (.visitInsn mv Opcodes/AASTORE)
          (.visitMethodInsn mv Opcodes/INVOKESTATIC
                            "clojure/lang/RT"
                            "vector"
                            "([Ljava/lang/Object;)Lclojure/lang/IPersistentVector;"
                            false)
          (.visitInsn mv Opcodes/AASTORE)
          (.visitFieldInsn mv Opcodes/PUTSTATIC class-name
                           (str "CONFLICT_" field-base "_" idx) "[Ljava/lang/Object;"))))

    (.visitInsn mv Opcodes/RETURN)
    (.visitMaxs mv 10 10)
    (.visitEnd mv)))

(defn- emit-path-access
  "Emits bytecode to get a value from document at path.

  For a single-segment path, emits: RT.get(document, keyword)
  For multi-segment paths, emits a chain of RT.get calls.

  Stack: document -> value (or null)"
  [^MethodVisitor mv ^String class-name path-field path-length]
  (if (= path-length 1)
    ;; Single segment: RT.get(document, keyword)
    (do
      (.visitFieldInsn mv Opcodes/GETSTATIC class-name path-field "[Ljava/lang/Object;")
      (.visitLdcInsn mv (int 0))
      (.visitInsn mv Opcodes/AALOAD)  ; stack: document, keyword
      (.visitMethodInsn mv Opcodes/INVOKESTATIC
                        "clojure/lang/RT"
                        "get"
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                        false))
    ;; Multi segment: chain of RT.get calls
    (do
      ;; Store document in temp, iterate through path
      (dotimes [i path-length]
        ;; Stack: current-obj
        ;; Load path[i] keyword
        (.visitFieldInsn mv Opcodes/GETSTATIC class-name path-field "[Ljava/lang/Object;")
        (.visitLdcInsn mv (int i))
        (.visitInsn mv Opcodes/AALOAD)
        ;; Stack: current-obj, keyword
        (.visitMethodInsn mv Opcodes/INVOKESTATIC
                          "clojure/lang/RT"
                          "get"
                          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                          false)
        ;; Stack: next-obj (or null)
        ))))

(defn- generate-invoke
  "Generates the invoke(Object) method."
  [^ClassWriter cw ^String class-name constraint-set analysis]
  (let [mv (.visitMethod cw
                         Opcodes/ACC_PUBLIC
                         "invoke"
                         "(Ljava/lang/Object;)Ljava/lang/Object;"
                         nil nil)
        paths (vec (filter #(vector? (first %)) constraint-set))]
    (.visitCode mv)

    (if (empty? paths)
      ;; No paths - always satisfied
      (do
        (.visitFieldInsn mv Opcodes/GETSTATIC class-name "SATISFIED" "Ljava/lang/Object;")
        (.visitInsn mv Opcodes/ARETURN))

      ;; Process paths
      (let [;; Pre-create all labels
            path-labels (mapv (fn [_] {:open (Label.)
                                       :continue (Label.)})
                              paths)
            satisfied-label (Label.)]

        ;; Process each path
        (doseq [[path-idx [path constraints]] (map-indexed vector paths)]
          (let [field-base (path->field-name path)
                {:keys [open continue]} (nth path-labels path-idx)]

            ;; Load document and get value at path
            (.visitVarInsn mv Opcodes/ALOAD 1)
            (emit-path-access mv class-name (str "PATH_" field-base) (count path))
            (.visitVarInsn mv Opcodes/ASTORE 2)

            ;; Null check -> return open residual
            (.visitVarInsn mv Opcodes/ALOAD 2)
            (.visitJumpInsn mv Opcodes/IFNULL open)

            ;; Check each constraint
            (doseq [[idx c] (map-indexed vector constraints)]
              (let [conflict-label (Label.)
                    pass-label (Label.)]
                ;; Load value for constraint check
                (.visitVarInsn mv Opcodes/ALOAD 2)

                ;; Emit constraint - jumps to conflict-label on failure
                ;; Skip null check since we already did it at path level
                (emit/emit-constraint mv c (:type-env analysis)
                                      {:open open
                                       :conflict conflict-label
                                       :class-name class-name
                                       :set-field (str "SET_" field-base "_" idx)
                                       :pattern-field (str "PATTERN_" field-base "_" idx)
                                       :skip-null-check true})

                ;; Passed - skip conflict handler
                (.visitJumpInsn mv Opcodes/GOTO pass-label)

                ;; Conflict handler
                ;; Build: {path-vec [[:conflict constraint-form witness]]}
                ;; Using local variables for clarity and correctness
                ;; Local 3 = path-vec, Local 4 = constraint-form
                (.visitLabel mv conflict-label)

                ;; Load template array (contains [path-vec, constraint-form])
                (.visitFieldInsn mv Opcodes/GETSTATIC class-name
                                 (str "CONFLICT_" field-base "_" idx)
                                 "[Ljava/lang/Object;")
                ;; Get path-vec and store in local 3
                (.visitInsn mv Opcodes/DUP)
                (.visitLdcInsn mv (int 0))
                (.visitInsn mv Opcodes/AALOAD)
                (.visitVarInsn mv Opcodes/ASTORE 3)

                ;; Get constraint-form and store in local 4
                (.visitLdcInsn mv (int 1))
                (.visitInsn mv Opcodes/AALOAD)
                (.visitVarInsn mv Opcodes/ASTORE 4)

                ;; Build conflict-entry: [:conflict constraint-form witness]
                (.visitLdcInsn mv (int 3))
                (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
                ;; arr[0] = :conflict
                (.visitInsn mv Opcodes/DUP)
                (.visitLdcInsn mv (int 0))
                (emit-create-keyword mv "conflict")
                (.visitInsn mv Opcodes/AASTORE)
                ;; arr[1] = constraint-form
                (.visitInsn mv Opcodes/DUP)
                (.visitLdcInsn mv (int 1))
                (.visitVarInsn mv Opcodes/ALOAD 4)
                (.visitInsn mv Opcodes/AASTORE)
                ;; arr[2] = witness
                (.visitInsn mv Opcodes/DUP)
                (.visitLdcInsn mv (int 2))
                (.visitVarInsn mv Opcodes/ALOAD 2)
                (.visitInsn mv Opcodes/AASTORE)
                ;; Convert to vector
                (.visitMethodInsn mv Opcodes/INVOKESTATIC
                                  "clojure/lang/RT"
                                  "vector"
                                  "([Ljava/lang/Object;)Lclojure/lang/IPersistentVector;"
                                  false)
                ;; Store conflict-entry in local 5
                (.visitVarInsn mv Opcodes/ASTORE 5)

                ;; Build constraints-vec: [conflict-entry]
                (.visitLdcInsn mv (int 1))
                (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
                (.visitInsn mv Opcodes/DUP)
                (.visitLdcInsn mv (int 0))
                (.visitVarInsn mv Opcodes/ALOAD 5)
                (.visitInsn mv Opcodes/AASTORE)
                (.visitMethodInsn mv Opcodes/INVOKESTATIC
                                  "clojure/lang/RT"
                                  "vector"
                                  "([Ljava/lang/Object;)Lclojure/lang/IPersistentVector;"
                                  false)
                ;; Store constraints-vec in local 6
                (.visitVarInsn mv Opcodes/ASTORE 6)

                ;; Build map: {path-vec constraints-vec}
                (.visitLdcInsn mv (int 2))
                (.visitTypeInsn mv Opcodes/ANEWARRAY "java/lang/Object")
                ;; arr[0] = path-vec
                (.visitInsn mv Opcodes/DUP)
                (.visitLdcInsn mv (int 0))
                (.visitVarInsn mv Opcodes/ALOAD 3)
                (.visitInsn mv Opcodes/AASTORE)
                ;; arr[1] = constraints-vec
                (.visitInsn mv Opcodes/DUP)
                (.visitLdcInsn mv (int 1))
                (.visitVarInsn mv Opcodes/ALOAD 6)
                (.visitInsn mv Opcodes/AASTORE)
                (.visitMethodInsn mv Opcodes/INVOKESTATIC
                                  "clojure/lang/PersistentArrayMap"
                                  "createAsIfByAssoc"
                                  "([Ljava/lang/Object;)Lclojure/lang/PersistentArrayMap;"
                                  false)

                (.visitInsn mv Opcodes/ARETURN)

                (.visitLabel mv pass-label)))

            ;; All constraints passed - jump to continue
            (.visitJumpInsn mv Opcodes/GOTO continue)

            ;; Open residual handler
            (.visitLabel mv open)
            (.visitFieldInsn mv Opcodes/GETSTATIC class-name
                             (str "OPEN_" field-base)
                             "Ljava/lang/Object;")
            (.visitInsn mv Opcodes/ARETURN)

            ;; Continue point for this path
            (.visitLabel mv continue)))

        ;; All paths satisfied
        (.visitFieldInsn mv Opcodes/GETSTATIC class-name "SATISFIED" "Ljava/lang/Object;")
        (.visitInsn mv Opcodes/ARETURN)))

    ;; COMPUTE_MAXS will calculate these, but we need to call visitMaxs
    (.visitMaxs mv 10 10)
    (.visitEnd mv)))

(defn- generate-arity-stubs
  "Generates stub methods for other IFn arities that throw."
  [^ClassWriter cw]
  ;; invoke() - no args
  (let [mv (.visitMethod cw Opcodes/ACC_PUBLIC "invoke" "()Ljava/lang/Object;" nil nil)]
    (.visitCode mv)
    (.visitTypeInsn mv Opcodes/NEW "clojure/lang/ArityException")
    (.visitInsn mv Opcodes/DUP)
    (.visitLdcInsn mv (int 0))
    (.visitLdcInsn mv "CompiledPolicy")
    (.visitMethodInsn mv Opcodes/INVOKESPECIAL
                      "clojure/lang/ArityException"
                      "<init>"
                      "(ILjava/lang/String;)V"
                      false)
    (.visitInsn mv Opcodes/ATHROW)
    (.visitMaxs mv 4 1)
    (.visitEnd mv))

  ;; invoke(Object, Object) - two args
  (let [mv (.visitMethod cw Opcodes/ACC_PUBLIC "invoke"
                         "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                         nil nil)]
    (.visitCode mv)
    (.visitTypeInsn mv Opcodes/NEW "clojure/lang/ArityException")
    (.visitInsn mv Opcodes/DUP)
    (.visitLdcInsn mv (int 2))
    (.visitLdcInsn mv "CompiledPolicy")
    (.visitMethodInsn mv Opcodes/INVOKESPECIAL
                      "clojure/lang/ArityException"
                      "<init>"
                      "(ILjava/lang/String;)V"
                      false)
    (.visitInsn mv Opcodes/ATHROW)
    (.visitMaxs mv 4 3)
    (.visitEnd mv)))

(defn- load-class
  "Loads generated bytecode as a class."
  [^String internal-name ^bytes bytecode]
  (let [class-name (.replace internal-name "/" ".")
        loader (DynamicClassLoader.)]
    (.defineClass loader class-name bytecode nil)))

(defn generate-policy-class
  "Generates a bytecode-compiled policy evaluator.

  Takes a constraint set and returns an IFn instance that evaluates
  documents directly in JVM bytecode."
  ^IFn [constraint-set]
  (let [class-name (next-class-name)
        cw (ClassWriter. ClassWriter/COMPUTE_FRAMES)
        analysis (analyzer/analyze-constraint-set constraint-set)]

    ;; Class header
    (.visit cw Opcodes/V11
            (bit-or Opcodes/ACC_PUBLIC Opcodes/ACC_FINAL)
            class-name
            nil
            "java/lang/Object"
            (into-array String ["clojure/lang/IFn"]))

    ;; Generate static fields
    (generate-static-fields cw constraint-set)

    ;; Generate static initializer
    (generate-clinit cw class-name constraint-set)

    ;; Generate constructor
    (generate-default-constructor cw)

    ;; Generate invoke method
    (generate-invoke cw class-name constraint-set analysis)

    ;; Generate arity stubs
    (generate-arity-stubs cw)

    (.visitEnd cw)

    ;; Load and instantiate
    (let [bytecode (.toByteArray cw)
          clazz (load-class class-name bytecode)]
      (.newInstance clazz))))

(defn bytecode-eligible?
  "Returns true if the constraint set can be compiled to bytecode.

  Bytecode compilation requires:
  - No complex nodes (quantifiers, let bindings)
  - All operators are built-in
  - At least one path with constraints
  - All paths are non-empty vectors"
  [constraint-set]
  (let [analysis (analyzer/analyze-constraint-set constraint-set)
        paths (filter #(vector? (first %)) constraint-set)]
    (and (not (:has-complex analysis))
         (not (:has-custom-ops analysis))
         (seq paths)
         (every? #(and (seq (first %)) (seq (second %))) paths))))
