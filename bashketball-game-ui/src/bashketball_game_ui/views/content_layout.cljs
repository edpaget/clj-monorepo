(ns bashketball-game-ui.views.content-layout
  "Layout component for content pages with optional sidebar navigation."
  (:require
   [bashketball-game-ui.content.registry :as registry]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui sidebar-nav
  "Sidebar navigation showing all content in a category."
  [{:keys [category current-slug]}]
  (let [items (registry/list-by-category category)]
    ($ :nav {:class "space-y-1"}
       (for [{:keys [slug frontmatter]} items]
         (let [title (or (:title frontmatter) slug)
               active? (= slug current-slug)]
           ($ router/link
              {:key slug
               :to (str "/" category "/" slug)
               :class (if active?
                        "block px-3 py-2 text-sm font-medium text-blue-600 bg-blue-50 rounded-md"
                        "block px-3 py-2 text-sm text-gray-600 hover:text-gray-900 hover:bg-gray-50 rounded-md")}
              title))))))

(defui content-layout
  "Two-column layout with sidebar for content pages."
  [{:keys [category]}]
  (let [params (router/use-params)
        slug (:slug params)]
    ($ :div {:class "flex min-h-screen bg-slate-100"}
       ($ :aside {:class "w-64 flex-shrink-0 hidden md:block py-8 pl-6"}
          ($ :div {:class "sticky top-6"}
             ($ sidebar-nav {:category category :current-slug slug})))
       ($ :main {:class "flex-1 min-w-0 bg-white py-8 px-10 shadow-sm"}
          ($ router/outlet)))))
