(ns bashketball-editor-api.context
  "Request-scoped user context for Git operations.

  Provides a dynamic variable bound per-request containing user information
  needed for Git commits and pushes. Repositories that need user context
  receive an accessor function that reads from this dynamic var.")

(def ^:dynamic *user-context*
  "Current user context for Git operations.

  Bound per-request by [[wrap-user-context]] middleware. Contains:
  - `:name` - User's display name for Git commits
  - `:email` - User's email for Git commits
  - `:github-token` - OAuth token for push/pull operations"
  nil)

(defn current-user-context
  "Returns the current user context.

  Throws if called outside of a request context (i.e., when `*user-context*`
  is not bound). Use this as the accessor function for Git repositories."
  []
  (or *user-context*
      (throw (ex-info "No user context available - operation requires authentication"
                      {:error :no-user-context}))))

(defn bind-user-context
  "Binds user context and executes the function.

  Useful for testing or when you need to explicitly set context."
  [user-ctx f]
  (binding [*user-context* user-ctx]
    (f)))
