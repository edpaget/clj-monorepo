(ns bashketball-ui.router
  "React Router integration.

  Provides routing components and hooks for navigation."
  (:require
   ["react-router-dom" :as rr]))

;; Components
(def browser-router rr/BrowserRouter)
(def routes rr/Routes)
(def route rr/Route)
(def link rr/Link)
(def nav-link rr/NavLink)
(def outlet rr/Outlet)
(def navigate rr/Navigate)

;; Hooks
(def use-navigate rr/useNavigate)
(def use-params rr/useParams)
(def use-search-params rr/useSearchParams)
(def use-location rr/useLocation)
(def use-match rr/useMatch)
