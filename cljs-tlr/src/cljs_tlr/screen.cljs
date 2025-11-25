(ns cljs-tlr.screen
  "Screen query utilities wrapping @testing-library/dom queries.

  The screen object represents the global document body and provides
  query methods for finding elements. This namespace wraps those queries
  with Clojure-friendly function names.

  Query variants follow a naming convention:

  - `get-by-*` - Throws if not found or multiple matches
  - `query-by-*` - Returns nil if not found, throws on multiple
  - `find-by-*` - Returns Promise, waits for element to appear
  - `get-all-by-*` - Returns array, throws if empty
  - `query-all-by-*` - Returns array (may be empty)
  - `find-all-by-*` - Returns Promise of array

  Query priority (recommended order):

  1. `*-by-role` - Best for accessibility
  2. `*-by-label-text` - Good for form fields
  3. `*-by-placeholder-text` - Fallback for inputs
  4. `*-by-text` - For non-interactive elements
  5. `*-by-display-value` - For filled form elements
  6. `*-by-test-id` - Last resort"
  (:require
   ["@testing-library/react" :refer [screen]]))

;; ByRole queries - preferred for accessibility

(defn get-by-role
  "Finds element by ARIA role. Throws if not found or multiple matches.

  Role can be a string like \"button\", \"textbox\", \"heading\", etc.
  Options map supports `:name`, `:level`, `:pressed`, `:checked`, etc."
  (^js [role] (.getByRole screen role))
  (^js [role opts] (.getByRole screen role (clj->js opts))))

(defn query-by-role
  "Finds element by ARIA role. Returns nil if not found."
  (^js [role] (.queryByRole screen role))
  (^js [role opts] (.queryByRole screen role (clj->js opts))))

(defn find-by-role
  "Finds element by ARIA role. Returns Promise that resolves when found."
  (^js [role] (.findByRole screen role))
  (^js [role opts] (.findByRole screen role (clj->js opts))))

(defn get-all-by-role
  "Finds all elements matching ARIA role. Throws if none found."
  (^js [role] (.getAllByRole screen role))
  (^js [role opts] (.getAllByRole screen role (clj->js opts))))

(defn query-all-by-role
  "Finds all elements matching ARIA role. Returns empty array if none."
  (^js [role] (.queryAllByRole screen role))
  (^js [role opts] (.queryAllByRole screen role (clj->js opts))))

;; ByLabelText queries - good for form fields

(defn get-by-label-text
  "Finds form element by its label text. Throws if not found."
  (^js [text] (.getByLabelText screen text))
  (^js [text opts] (.getByLabelText screen text (clj->js opts))))

(defn query-by-label-text
  "Finds form element by its label text. Returns nil if not found."
  (^js [text] (.queryByLabelText screen text))
  (^js [text opts] (.queryByLabelText screen text (clj->js opts))))

(defn find-by-label-text
  "Finds form element by its label text. Returns Promise."
  (^js [text] (.findByLabelText screen text))
  (^js [text opts] (.findByLabelText screen text (clj->js opts))))

(defn get-all-by-label-text
  "Finds all form elements matching label text. Throws if none."
  (^js [text] (.getAllByLabelText screen text))
  (^js [text opts] (.getAllByLabelText screen text (clj->js opts))))

(defn query-all-by-label-text
  "Finds all form elements matching label text. Returns empty array if none."
  (^js [text] (.queryAllByLabelText screen text))
  (^js [text opts] (.queryAllByLabelText screen text (clj->js opts))))

;; ByPlaceholderText queries

(defn get-by-placeholder-text
  "Finds input by placeholder attribute. Throws if not found."
  (^js [text] (.getByPlaceholderText screen text))
  (^js [text opts] (.getByPlaceholderText screen text (clj->js opts))))

(defn query-by-placeholder-text
  "Finds input by placeholder attribute. Returns nil if not found."
  (^js [text] (.queryByPlaceholderText screen text))
  (^js [text opts] (.queryByPlaceholderText screen text (clj->js opts))))

(defn find-by-placeholder-text
  "Finds input by placeholder attribute. Returns Promise."
  (^js [text] (.findByPlaceholderText screen text))
  (^js [text opts] (.findByPlaceholderText screen text (clj->js opts))))

(defn get-all-by-placeholder-text
  "Finds all inputs matching placeholder. Throws if none."
  (^js [text] (.getAllByPlaceholderText screen text))
  (^js [text opts] (.getAllByPlaceholderText screen text (clj->js opts))))

(defn query-all-by-placeholder-text
  "Finds all inputs matching placeholder. Returns empty array if none."
  (^js [text] (.queryAllByPlaceholderText screen text))
  (^js [text opts] (.queryAllByPlaceholderText screen text (clj->js opts))))

;; ByText queries - for non-interactive content

(defn get-by-text
  "Finds element by text content. Throws if not found."
  (^js [text] (.getByText screen text))
  (^js [text opts] (.getByText screen text (clj->js opts))))

(defn query-by-text
  "Finds element by text content. Returns nil if not found."
  (^js [text] (.queryByText screen text))
  (^js [text opts] (.queryByText screen text (clj->js opts))))

(defn find-by-text
  "Finds element by text content. Returns Promise."
  (^js [text] (.findByText screen text))
  (^js [text opts] (.findByText screen text (clj->js opts))))

(defn get-all-by-text
  "Finds all elements matching text content. Throws if none."
  (^js [text] (.getAllByText screen text))
  (^js [text opts] (.getAllByText screen text (clj->js opts))))

(defn query-all-by-text
  "Finds all elements matching text content. Returns empty array if none."
  (^js [text] (.queryAllByText screen text))
  (^js [text opts] (.queryAllByText screen text (clj->js opts))))

;; ByDisplayValue queries - for filled form elements

(defn get-by-display-value
  "Finds input/select/textarea by current value. Throws if not found."
  (^js [value] (.getByDisplayValue screen value))
  (^js [value opts] (.getByDisplayValue screen value (clj->js opts))))

(defn query-by-display-value
  "Finds input/select/textarea by current value. Returns nil if not found."
  (^js [value] (.queryByDisplayValue screen value))
  (^js [value opts] (.queryByDisplayValue screen value (clj->js opts))))

(defn find-by-display-value
  "Finds input/select/textarea by current value. Returns Promise."
  (^js [value] (.findByDisplayValue screen value))
  (^js [value opts] (.findByDisplayValue screen value (clj->js opts))))

(defn get-all-by-display-value
  "Finds all inputs matching current value. Throws if none."
  (^js [value] (.getAllByDisplayValue screen value))
  (^js [value opts] (.getAllByDisplayValue screen value (clj->js opts))))

(defn query-all-by-display-value
  "Finds all inputs matching current value. Returns empty array if none."
  (^js [value] (.queryAllByDisplayValue screen value))
  (^js [value opts] (.queryAllByDisplayValue screen value (clj->js opts))))

;; ByAltText queries - for images

(defn get-by-alt-text
  "Finds image by alt attribute. Throws if not found."
  (^js [text] (.getByAltText screen text))
  (^js [text opts] (.getByAltText screen text (clj->js opts))))

(defn query-by-alt-text
  "Finds image by alt attribute. Returns nil if not found."
  (^js [text] (.queryByAltText screen text))
  (^js [text opts] (.queryByAltText screen text (clj->js opts))))

(defn find-by-alt-text
  "Finds image by alt attribute. Returns Promise."
  (^js [text] (.findByAltText screen text))
  (^js [text opts] (.findByAltText screen text (clj->js opts))))

(defn get-all-by-alt-text
  "Finds all images matching alt attribute. Throws if none."
  (^js [text] (.getAllByAltText screen text))
  (^js [text opts] (.getAllByAltText screen text (clj->js opts))))

(defn query-all-by-alt-text
  "Finds all images matching alt attribute. Returns empty array if none."
  (^js [text] (.queryAllByAltText screen text))
  (^js [text opts] (.queryAllByAltText screen text (clj->js opts))))

;; ByTitle queries

(defn get-by-title
  "Finds element by title attribute. Throws if not found."
  (^js [title] (.getByTitle screen title))
  (^js [title opts] (.getByTitle screen title (clj->js opts))))

(defn query-by-title
  "Finds element by title attribute. Returns nil if not found."
  (^js [title] (.queryByTitle screen title))
  (^js [title opts] (.queryByTitle screen title (clj->js opts))))

(defn find-by-title
  "Finds element by title attribute. Returns Promise."
  (^js [title] (.findByTitle screen title))
  (^js [title opts] (.findByTitle screen title (clj->js opts))))

(defn get-all-by-title
  "Finds all elements matching title attribute. Throws if none."
  (^js [title] (.getAllByTitle screen title))
  (^js [title opts] (.getAllByTitle screen title (clj->js opts))))

(defn query-all-by-title
  "Finds all elements matching title attribute. Returns empty array if none."
  (^js [title] (.queryAllByTitle screen title))
  (^js [title opts] (.queryAllByTitle screen title (clj->js opts))))

;; ByTestId queries - last resort

(defn get-by-test-id
  "Finds element by data-testid attribute. Throws if not found.

  Use as a last resort when other queries don't apply. Test IDs are
  invisible to users and don't test accessibility."
  (^js [id] (.getByTestId screen id))
  (^js [id opts] (.getByTestId screen id (clj->js opts))))

(defn query-by-test-id
  "Finds element by data-testid attribute. Returns nil if not found."
  (^js [id] (.queryByTestId screen id))
  (^js [id opts] (.queryByTestId screen id (clj->js opts))))

(defn find-by-test-id
  "Finds element by data-testid attribute. Returns Promise."
  (^js [id] (.findByTestId screen id))
  (^js [id opts] (.findByTestId screen id (clj->js opts))))

(defn get-all-by-test-id
  "Finds all elements matching data-testid. Throws if none."
  (^js [id] (.getAllByTestId screen id))
  (^js [id opts] (.getAllByTestId screen id (clj->js opts))))

(defn query-all-by-test-id
  "Finds all elements matching data-testid. Returns empty array if none."
  (^js [id] (.queryAllByTestId screen id))
  (^js [id opts] (.queryAllByTestId screen id (clj->js opts))))

;; Debug utilities

(defn debug
  "Prints the current DOM tree for debugging.

  With no arguments, prints the entire document body. With an element,
  prints just that element's subtree. Useful for understanding why
  queries aren't finding expected elements."
  ([] (.debug screen))
  ([element] (.debug screen element))
  ([element max-length] (.debug screen element max-length)))

(defn log-testing-playground-url
  "Logs a URL to testing-playground.com for the current DOM.

  Opens the URL to get query suggestions for elements in your
  current DOM state."
  []
  (.logTestingPlaygroundURL screen))
