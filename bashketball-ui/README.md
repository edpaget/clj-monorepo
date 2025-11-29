# bashketball-ui

Shared ClojureScript UI component library for Bashketball applications. Built with [UIx](https://github.com/pitch-io/uix) (React wrapper) and styled with Tailwind CSS.

## Installation

Add to your `deps.edn`:

```clojure
local/bashketball-ui {:local/root "../bashketball-ui"}
```

Ensure npm dependencies are installed:

```bash
npm install
```

## Components

### UI Components

Basic form and display components with Tailwind styling:

```clojure
(require '[bashketball-ui.components.button :refer [button]])
(require '[bashketball-ui.components.input :refer [input]])
(require '[bashketball-ui.components.label :refer [label]])
(require '[bashketball-ui.components.textarea :refer [textarea]])
(require '[bashketball-ui.components.select :refer [select select-trigger select-item]])
(require '[bashketball-ui.components.loading :refer [spinner skeleton loading-overlay loading-dots button-spinner]])

;; Button with variants
($ button {:variant :default} "Click me")
($ button {:variant :destructive :size :sm} "Delete")
($ button {:variant :outline :disabled true} "Disabled")

;; Form inputs
($ input {:placeholder "Enter text..." :on-change handler})
($ textarea {:rows 5 :value text :on-change handler})
($ select {:options [{:value "a" :label "Option A"}] :on-change handler})

;; Loading states
($ spinner {:size :lg})
($ skeleton {:variant :text})
($ loading-overlay {:message "Loading..."})
```

### Card Components

Card preview and list components for displaying trading cards:

```clojure
(require '[bashketball-ui.cards.card-preview :refer [card-preview]])
(require '[bashketball-ui.cards.card-list-item :refer [card-list-item]])

($ card-preview {:card card-data})
($ card-list-item {:card card-data :on-click handler})
```

### Authentication

Configurable authentication context and protected routes:

```clojure
(require '[bashketball-ui.context.auth :as auth])
(require '[bashketball-ui.components.protected-route :refer [protected-route require-auth]])

;; Create auth provider with your user hook
(def my-auth-provider
  (auth/create-auth-provider {:use-user-hook my-app.hooks/use-me}))

;; Create logout function for your backend
(def logout!
  (auth/create-logout-fn {:logout-url "/api/logout"}))

;; Use in your app
($ my-auth-provider
   ($ app-content))

;; Access auth state in components
(let [{:keys [user loading? logged-in? refetch]} (auth/use-auth)]
  ...)

;; Protected routes (redirects when not authenticated)
($ protected-route {:redirect-to "/"})

;; Require auth (shows login prompt instead of redirecting)
($ require-auth {:login-url "/auth/login" :login-label "Sign In"}
   ($ protected-content))
```

### Utilities

```clojure
(require '[bashketball-ui.utils :refer [cn]])
(require '[bashketball-ui.router :as router])

;; Merge Tailwind classes (uses clsx + tailwind-merge)
(cn "px-4 py-2" (when active? "bg-blue-500") "text-white")

;; React Router wrappers
(router/use-params)
(router/use-navigate)
($ router/link {:to "/path"} "Click")
($ router/outlet)
($ router/navigate {:to "/" :replace true})
```

### Core Setup

The `core` namespace extends JavaScript objects with `ILookup` for idiomatic property access:

```clojure
(require 'bashketball-ui.core)

;; Now you can use keyword access on JS objects
(:name js-user)  ;; instead of (.-name js-user)
(:id response)   ;; instead of (.-id response)
```

## Dependencies

- `uix.core` - React wrapper
- `metosin/malli` - Schema validation
- `bashketball-schemas` - Shared card schemas
- `clsx` - Class name utility (npm)
- `tailwind-merge` - Tailwind class merging (npm)
- `class-variance-authority` - Component variants (npm)
- `@radix-ui/react-select` - Accessible select component (npm)
- `lucide-react` - Icons (npm)
- `react-router-dom` - Routing (npm)
