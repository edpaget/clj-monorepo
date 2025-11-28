(ns bashketball-editor-ui.views.card-view-test
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.context.auth :as auth]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.views.card-view :refer [card-view]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [goog.object :as obj]
   [uix.core :refer [$]]))

;; Enable keyword access on JS objects (needed for route params)
(extend-type object
  ILookup
  (-lookup ([o k] (obj/get o (name k)))
    ([o k not-found] (obj/get o (name k) not-found))))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-player-card-response
  #js {:request #js {:query q/CARD_QUERY
                     :variables #js {:slug "test-player"
                                     :setSlug "core-set"}}
       :result #js {:data #js {:card #js {:__typename "PlayerCard"
                                          :slug "test-player"
                                          :name "Test Player"
                                          :setSlug "core-set"
                                          :imagePrompt "A basketball player"
                                          :sht 7
                                          :pss 6
                                          :def 5
                                          :speed 8
                                          :size "MD"
                                          :deckSize 15
                                          :abilities #js ["Slam Dunk" "Fast Break"]}}}})

(def mock-play-card-response
  #js {:request #js {:query q/CARD_QUERY
                     :variables #js {:slug "test-play"
                                     :setSlug "core-set"}}
       :result #js {:data #js {:card #js {:__typename "PlayCard"
                                          :slug "test-play"
                                          :name "Test Play"
                                          :setSlug "core-set"
                                          :imagePrompt nil
                                          :fate 3
                                          :play "Execute the play"}}}})

(defn with-providers
  "Wrap component with required providers for testing."
  ([component mocks initial-path]
   (with-providers component mocks initial-path {}))
  ([component mocks initial-path {:keys [logged-in?] :or {logged-in? true}}]
   (let [auth-state {:loading? false
                     :logged-in? logged-in?
                     :user (when logged-in? {:id "test-user"})
                     :refetch (fn [])}]
     ($ rr/MemoryRouter {:initialEntries #js [initial-path]}
        ($ (.-Provider auth/auth-context) {:value auth-state}
           ($ MockedProvider {:mocks mocks}
              ($ rr/Routes
                 ($ rr/Route {:path "cards/:setSlug/:slug" :element component}))))))))

;; -----------------------------------------------------------------------------
;; Basic render tests
;; -----------------------------------------------------------------------------

(t/deftest card-view-shows-loading-test
  (t/testing "shows loading spinner initially"
    (tlr/render (with-providers ($ card-view)
                  #js [mock-player-card-response]
                  "/cards/core-set/test-player"))
    (t/is (some? (js/document.querySelector "[class*='animate-spin']")))))

(t/deftest card-view-has-back-button-test
  (t/testing "shows back button"
    (tlr/render (with-providers ($ card-view)
                  #js [mock-player-card-response]
                  "/cards/core-set/test-player"))
    (t/is (some? (tlr/get-by-role "button" {:name "Back"})))))

(t/deftest card-view-loads-player-card-test
  (t/async done
           (t/testing "loads and displays player card"
             (tlr/render (with-providers ($ card-view)
                           #js [mock-player-card-response]
                           "/cards/core-set/test-player"))
             (-> (tlr/wait-for #(tlr/get-all-by-text "Test Player"))
                 (.then (fn []
                          (let [matches (tlr/get-all-by-text "Test Player")]
                            (t/is (= 2 (count matches))))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-view-shows-edit-button-test
  (t/async done
           (t/testing "shows edit button after loading"
             (tlr/render (with-providers ($ card-view)
                           #js [mock-player-card-response]
                           "/cards/core-set/test-player"))
             (-> (tlr/wait-for #(tlr/get-by-role "button" {:name "Edit"}))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-role "button" {:name "Edit"})))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-view-shows-card-type-badge-test
  (t/async done
           (t/testing "shows card type badge"
             (tlr/render (with-providers ($ card-view)
                           #js [mock-player-card-response]
                           "/cards/core-set/test-player"))
             (-> (tlr/wait-for #(tlr/get-by-text "Player"))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-text "Player")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-view-shows-player-stats-test
  (t/async done
           (t/testing "shows player stats"
             (tlr/render (with-providers ($ card-view)
                           #js [mock-player-card-response]
                           "/cards/core-set/test-player"))
             (-> (tlr/wait-for #(tlr/get-by-text "SHT"))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-text "SHT")))
                          (t/is (some? (tlr/get-by-text "PSS")))
                          (t/is (some? (tlr/get-by-text "DEF")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
