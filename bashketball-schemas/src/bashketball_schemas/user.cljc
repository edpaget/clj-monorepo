(ns bashketball-schemas.user
  "User schema shared across API and UI.

   Defines the core user representation used for authentication
   and display across all Bashketball applications.")

(def Email
  "Email address schema with basic format validation."
  [:re #"^[^\s@]+@[^\s@]+\.[^\s@]+$"])

(def User
  "User schema representing an authenticated user.

   Users are identified by UUID and authenticated via OAuth providers
   (GitHub for editor, Google for game)."
  [:map
   [:id :uuid]
   [:email Email]
   [:name {:optional true} [:maybe [:string {:max 255}]]]
   [:avatar-url {:optional true} [:maybe :string]]])

(def GoogleId
  "Google OAuth subject identifier."
  [:string {:min 1 :max 255}])

(def GitHubId
  "GitHub user ID (numeric, stored as string)."
  [:string {:min 1 :max 50}])
