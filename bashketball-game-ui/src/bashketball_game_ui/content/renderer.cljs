(ns bashketball-game-ui.content.renderer
  "Components for rendering processed markdown content."
  (:require
   [uix.core :refer [$ defui]]))

(defui table-of-contents
  "Renders a table of contents from heading metadata."
  [{:keys [toc]}]
  (when (seq toc)
    ($ :nav {:class "mb-8 p-4 bg-slate-50 rounded-lg border border-slate-200"}
       ($ :h2 {:class "text-sm font-semibold text-slate-900 uppercase tracking-wide mb-3"}
          "On this page")
       ($ :ul {:class "space-y-1"}
          (for [{:keys [level text id]} toc]
            ($ :li {:key id
                    :class (when (= level 3) "ml-4")}
               ($ :a {:href (str "#" id)
                      :class "text-sm text-slate-600 hover:text-blue-600 hover:underline block py-0.5"}
                  text)))))))

(defui prose
  "Renders HTML content with Tailwind Typography prose styling."
  [{:keys [html class]}]
  ($ :article {:class (str "prose prose-slate prose-headings:scroll-mt-4 "
                           "prose-a:text-blue-600 prose-a:no-underline hover:prose-a:underline "
                           "prose-table:text-sm prose-th:bg-slate-50 "
                           "prose-img:rounded-lg "
                           "max-w-prose "
                           class)
               :dangerouslySetInnerHTML #js {:__html html}}))

(defui content-page
  "Renders a full content page with title, ToC, and content."
  [{:keys [content]}]
  (let [{:keys [html toc frontmatter]} content
        {:keys [title description]} frontmatter]
    ($ :div {:class "max-w-prose"}
       ($ :header {:class "mb-8"}
          (when title
            ($ :h1 {:class "text-3xl font-bold text-slate-900 tracking-tight"} title))
          (when description
            ($ :p {:class "mt-3 text-lg text-slate-600 leading-relaxed"} description)))
       ($ table-of-contents {:toc toc})
       ($ prose {:html html}))))
