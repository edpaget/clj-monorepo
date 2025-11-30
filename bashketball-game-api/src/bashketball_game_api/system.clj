(ns bashketball-game-api.system
  "Integrant system configuration.

  Defines the application's component system with dependencies between
  configuration, database, authentication, subscriptions, GraphQL, and HTTP server."
  (:require
   [authn.core :as authn]
   [bashketball-game-api.auth.google :as google-auth]
   [bashketball-game-api.config :as config]
   [bashketball-game-api.graphql.resolvers.card :as card-resolvers]
   [bashketball-game-api.graphql.resolvers.deck :as deck-resolvers]
   [bashketball-game-api.graphql.resolvers.game :as game-resolvers]
   [bashketball-game-api.graphql.resolvers.user :as user-resolvers]
   [bashketball-game-api.handler :as handler]
   [bashketball-game-api.models.deck :as deck]
   [bashketball-game-api.models.game :as game]
   [bashketball-game-api.models.session :as session]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.services.auth :as auth-service]
   [bashketball-game-api.services.catalog :as catalog]
   [bashketball-game-api.services.deck :as deck-service]
   [bashketball-game-api.services.game :as game-service]
   [bashketball-game-api.subscriptions.core :as subs]
   [clojure.tools.logging :as log]
   [db.connection-pool :as pool]
   [db.core :as db]
   [db.migrate :as migrate]
   [db.test-utils :as db.test]
   [integrant.core :as ig]
   [ring.adapter.jetty :as jetty]))

;; ---------------------------------------------------------------------------
;; Configuration

(defmethod ig/init-key ::config [_ {:keys [profile]}]
  (config/load-config (or profile :dev)))

;; ---------------------------------------------------------------------------
;; Database

(defmethod ig/init-key ::ensure-test-db [_ {:keys [config profile]}]
  (when (= profile :test)
    (db.test/ensure-database-exists!
     (get-in config [:database :database-url]))))

(defmethod ig/init-key ::db-pool [_ {:keys [config]}]
  (let [url  (get-in config [:database :database-url])
        opts (get-in config [:database :c3p0-opts])]
    (log/info "Creating database connection pool for" url)
    (pool/create-pool url opts)))

(defmethod ig/halt-key! ::db-pool [_ datasource]
  (log/info "Closing database connection pool")
  (pool/close-pool! datasource))

(defmethod ig/init-key ::migrate [_ {:keys [db-pool]}]
  (log/info "Running database migrations")
  (binding [db/*datasource* db-pool]
    (migrate/migrate)))

;; ---------------------------------------------------------------------------
;; Repositories

(defmethod ig/init-key ::user-repo [_ _]
  (log/info "Creating user repository")
  (user/create-user-repository))

(defmethod ig/init-key ::session-repo [_ {:keys [config]}]
  (log/info "Creating session repository")
  (let [ttl-ms (get-in config [:session :ttl-ms])]
    (session/create-session-repository ttl-ms)))

(defmethod ig/init-key ::deck-repo [_ _]
  (log/info "Creating deck repository")
  (deck/create-deck-repository))

(defmethod ig/init-key ::game-repo [_ _]
  (log/info "Creating game repository")
  (game/create-game-repository))

;; ---------------------------------------------------------------------------
;; Card Catalog (Phase 4)

(defmethod ig/init-key ::card-catalog [_ _]
  (log/info "Creating card catalog")
  (catalog/create-card-catalog))

;; ---------------------------------------------------------------------------
;; Services (Phase 3+)

(defmethod ig/init-key ::deck-service [_ {:keys [deck-repo card-catalog]}]
  (log/info "Creating deck service")
  (deck-service/create-deck-service deck-repo card-catalog))

(defmethod ig/init-key ::game-service [_ {:keys [game-repo deck-service card-catalog subscription-manager]}]
  (log/info "Creating game service")
  (game-service/create-game-service game-repo deck-service card-catalog subscription-manager))

(defmethod ig/init-key ::auth-service [_ {:keys [user-repo]}]
  (log/info "Creating auth service")
  (auth-service/create-auth-service user-repo))

(defmethod ig/init-key ::authenticator [_ {:keys [auth-service session-repo config]}]
  (log/info "Creating authenticator")
  (authn/create-authenticator
   {:credential-validator (:credential-validator auth-service)
    :claims-provider (:claims-provider auth-service)
    :session-store session-repo
    :session-ttl-ms (get-in config [:session :ttl-ms])
    :session-cookie-name (get-in config [:session :cookie-name])
    :session-cookie-secure? (get-in config [:session :cookie-secure?])
    :session-cookie-http-only? (get-in config [:session :cookie-http-only?])
    :session-cookie-same-site (get-in config [:session :cookie-same-site])}))

(defmethod ig/init-key ::google-oidc-client [_ {:keys [config]}]
  (log/info "Creating Google OIDC client")
  (let [google-config (:google config)]
    (google-auth/create-google-oidc-client
     {:client-id (:client-id google-config)
      :client-secret (:client-secret google-config)
      :redirect-uri (:redirect-uri google-config)
      :scopes (:scopes google-config)})))

;; ---------------------------------------------------------------------------
;; Subscriptions (Phase 6)

(defmethod ig/init-key ::subscription-manager [_ _]
  (log/info "Creating subscription manager")
  (subs/create-subscription-manager))

;; ---------------------------------------------------------------------------
;; GraphQL (Phase 4+)

(defmethod ig/init-key ::resolver-map [_ {:keys [card-catalog deck-service game-service user-repo]}]
  (log/info "Creating GraphQL resolver map")
  {:resolvers (merge #_{:clj-kondo/ignore [:unresolved-var]}
               card-resolvers/resolvers
                     #_{:clj-kondo/ignore [:unresolved-var]}
                     deck-resolvers/resolvers
                     #_{:clj-kondo/ignore [:unresolved-var]}
                     game-resolvers/resolvers
                     #_{:clj-kondo/ignore [:unresolved-var]}
                     user-resolvers/resolvers)
   :card-catalog card-catalog
   :deck-service deck-service
   :game-service game-service
   :user-repo user-repo})

;; ---------------------------------------------------------------------------
;; HTTP Handler & Server

(defmethod ig/init-key ::handler [_ {:keys [google-oidc-client
                                            authenticator
                                            db-pool
                                            user-repo
                                            resolver-map
                                            subscription-manager
                                            config]}]
  (log/info "Creating HTTP handler")
  (handler/create-handler
   {:google-oidc-client google-oidc-client
    :authenticator authenticator
    :user-repo user-repo
    :db-pool db-pool
    :resolver-map resolver-map
    :subscription-manager subscription-manager
    :config config}))

(defmethod ig/init-key ::server [_ {:keys [handler config port-override]}]
  (let [port (or port-override (get-in config [:server :port]))
        host (get-in config [:server :host])]
    (log/info "Starting HTTP server on" (str host ":" port))
    (jetty/run-jetty handler
                     {:port             port
                      :host             host
                      :join?            false
                      :virtual-threads? true})))

(defmethod ig/halt-key! ::server [_ server]
  (log/info "Stopping HTTP server")
  (.stop server))

;; ---------------------------------------------------------------------------
;; System Configuration

(defn system-config
  "Returns the Integrant system configuration map.

  Defines all components and their dependencies."
  [profile]
  {::config {:profile profile}
   ::ensure-test-db {:config (ig/ref ::config)
                     :profile profile}
   ::db-pool {:config (ig/ref ::config)
              :ensure-test-db (ig/ref ::ensure-test-db)}
   ::migrate {:db-pool (ig/ref ::db-pool)}

   ;; Repositories
   ::user-repo {:migrate (ig/ref ::migrate)}
   ::session-repo {:config (ig/ref ::config)
                   :migrate (ig/ref ::migrate)}
   ::deck-repo {:migrate (ig/ref ::migrate)}
   ::game-repo {:migrate (ig/ref ::migrate)}

   ;; Card Catalog
   ::card-catalog {}

   ;; Services
   ::deck-service {:deck-repo (ig/ref ::deck-repo)
                   :card-catalog (ig/ref ::card-catalog)}
   ::game-service {:game-repo (ig/ref ::game-repo)
                   :deck-service (ig/ref ::deck-service)
                   :card-catalog (ig/ref ::card-catalog)
                   :subscription-manager (ig/ref ::subscription-manager)}
   ::auth-service {:user-repo (ig/ref ::user-repo)
                   :config (ig/ref ::config)}
   ::authenticator {:auth-service (ig/ref ::auth-service)
                    :session-repo (ig/ref ::session-repo)
                    :config (ig/ref ::config)}
   ::google-oidc-client {:config (ig/ref ::config)}
   ::subscription-manager {}

   ;; GraphQL
   ::resolver-map {:card-catalog (ig/ref ::card-catalog)
                   :deck-service (ig/ref ::deck-service)
                   :game-service (ig/ref ::game-service)
                   :user-repo (ig/ref ::user-repo)}

   ;; HTTP
   ::handler {:google-oidc-client (ig/ref ::google-oidc-client)
              :authenticator (ig/ref ::authenticator)
              :db-pool (ig/ref ::db-pool)
              :user-repo (ig/ref ::user-repo)
              :resolver-map (ig/ref ::resolver-map)
              :subscription-manager (ig/ref ::subscription-manager)
              :config (ig/ref ::config)}
   ::server {:handler (ig/ref ::handler)
             :config (ig/ref ::config)}})

(defn start-system
  "Starts the application system.

  Accepts an optional `opts` map with:
  - `:exclude-keys` - set of component keys to exclude from initialization
  - `:port` - override the server port (use 0 for auto-select)"
  ([]
   (start-system :dev))
  ([profile]
   (start-system profile {}))
  ([profile {:keys [exclude-keys port]}]
   (let [config (cond-> (system-config profile)
                  exclude-keys (as-> cfg (apply dissoc cfg exclude-keys))
                  port         (assoc-in [::server :port-override] port))]
     (ig/init config))))

(defn stop-system
  "Stops the application system."
  [system]
  (ig/halt! system))
