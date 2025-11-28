(ns bashketball-editor-api.util.slug
  "URL-safe slug utilities.

  Provides functions for generating and validating URL-safe slugs used as
  identifiers for cards and card sets."
  (:require
   [clojure.string :as str]
   [malli.core :as m]))

(def url-safe-slug-regex
  "Regex pattern for valid URL-safe slugs.

  Matches lowercase alphanumeric strings with hyphens as separators.
  Does not allow leading/trailing hyphens or consecutive hyphens."
  #"^[a-z0-9]+(?:-[a-z0-9]+)*$")

(def UrlSafeSlug
  "Malli schema for URL-safe slugs.

  Validates that a string is a valid URL-safe slug: lowercase alphanumeric
  with single hyphens as word separators, no leading/trailing hyphens."
  [:and
   :string
   [:fn {:error/message "Slug must be URL-safe: lowercase alphanumeric with hyphens, no leading/trailing/consecutive hyphens"}
    #(boolean (re-matches url-safe-slug-regex %))]])

(defn url-safe?
  "Returns true if the string is a valid URL-safe slug."
  [s]
  (boolean (and (string? s) (re-matches url-safe-slug-regex s))))

(defn slugify
  "Converts a string to a URL-safe slug.

  Converts to lowercase, replaces non-alphanumeric characters with hyphens,
  removes leading/trailing hyphens, and collapses consecutive hyphens."
  [s]
  (-> s
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-+" "")
      (str/replace #"-+$" "")))

(defn validate-slug!
  "Validates that a slug is URL-safe, throwing an exception if not.

  Returns the slug if valid. Use this after generating or receiving a slug
  to ensure it meets URL safety requirements."
  [slug context]
  (if (m/validate UrlSafeSlug slug)
    slug
    (throw (ex-info "Invalid slug: must be URL-safe"
                    {:slug slug
                     :context context
                     :error (m/explain UrlSafeSlug slug)}))))
