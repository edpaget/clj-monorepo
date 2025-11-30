(ns bashketball-schemas.types
  "Common type schemas shared across the Bashketball ecosystem.

   Provides cross-platform Malli schemas for types that need special
   handling across Clojure and ClojureScript environments.")

(def iso8601-pattern
  "Regex pattern for ISO8601 datetime strings.

   Matches formats like:
   - 2024-01-15T10:30:00Z
   - 2024-01-15T10:30:00.123Z
   - 2024-01-15T10:30:00+00:00"
  #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})?")

(def DateTime
  "Cross-platform datetime schema.

   Accepts multiple formats for flexibility across platforms:
   - ISO8601 strings (from GraphQL responses)
   - `java.time.Instant` (Clojure JVM)
   - `js/Date` (ClojureScript)

   The `inst?` predicate validates native platform instant types.
   Maps to GraphQL `Date` scalar via `:graphql/scalar` property."
  [:or {:graphql/scalar :Date}
   [:re iso8601-pattern]
   inst?])
