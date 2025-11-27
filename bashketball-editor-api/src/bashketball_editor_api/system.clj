(ns bashketball-editor-api.system
  "Integrant system configuration.

  Defines the application's component system with dependencies between
  configuration, database, authentication, GraphQL, and HTTP server."
  (:require
   [authn.core :as authn]
   [bashketball-editor-api.auth.github :as gh-auth]
   [bashketball-editor-api.config :as config]
   [bashketball-editor-api.git.cards :as git-cards]
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.git.sets :as git-sets]
   [bashketball-editor-api.graphql.schema :as gql-schema]
   [bashketball-editor-api.handler :as handler]
   [bashketball-editor-api.models.session :as session]
   [bashketball-editor-api.models.user :as user]
   [bashketball-editor-api.services.auth :as auth-svc]
   [bashketball-editor-api.services.card :as card-svc]
   [bashketball-editor-api.services.set :as set-svc]
   [clojure.tools.logging :as log]
   [db.connection-pool :as pool]
   [db.core :as db]
   [db.migrate :as migrate]
   [db.test-utils :as db.test]
   [integrant.core :as ig]
   [oidc-github.provider :as oidc-gh]
   [ring.adapter.jetty :as jetty]))

(defmethod ig/init-key ::config [_ {:keys [profile]}]
  (config/load-config (or profile :dev)))

(defmethod ig/init-key ::ensure-test-db [_ {:keys [config profile]}]
  (when (= profile :test)
    (db.test/ensure-database-exists!
     (get-in config [:database :database-url]))))

(defmethod ig/init-key ::db-pool [_ {:keys [config]}]
  (pool/create-pool
   (get-in config [:database :database-url])
   (get-in config [:database :c3p0-opts])))

(defmethod ig/halt-key! ::db-pool [_ datasource]
  (pool/close-pool! datasource))

(defmethod ig/init-key ::migrate [_ {:keys [db-pool]}]
  (binding [db/*datasource* db-pool]
    (migrate/migrate)))

(defmethod ig/init-key ::user-repo [_ _]
  (user/create-user-repository))

(defmethod ig/init-key ::session-repo [_ {:keys [config]}]
  (session/create-session-repository
   (get-in config [:session :ttl-ms])))

(defmethod ig/init-key ::github-claims-provider [_ {:keys [config]}]
  (oidc-gh/->GitHubClaimsProvider
   nil
   (get-in config [:auth :cache-ttl-ms])))

(defmethod ig/init-key ::auth-service [_ {:keys [user-repo config github-claims-provider]}]
  (auth-svc/create-auth-service
   user-repo
   (get-in config [:github :oauth :client-id])
   (get-in config [:github :oauth :client-secret])
   github-claims-provider))

(defmethod ig/init-key ::authenticator [_ {:keys [auth-service session-repo config]}]
  (authn/create-authenticator
   {:credential-validator (:credential-validator auth-service)
    :claims-provider (:claims-provider auth-service)
    :session-store session-repo
    :session-ttl-ms (get-in config [:session :ttl-ms])
    :session-cookie-name (get-in config [:session :cookie-name])
    :session-cookie-secure? (get-in config [:session :cookie-secure?])
    :session-cookie-http-only? (get-in config [:session :cookie-http-only?])
    :session-cookie-same-site (get-in config [:session :cookie-same-site])}))

(defmethod ig/init-key ::git-repo [_ {:keys [config]}]
  (let [git-config {:repo-path (get-in config [:git :repo-path])
                    :remote-url (get-in config [:git :remote-url])
                    :branch (get-in config [:git :branch])
                    :writer? (get-in config [:git :writer?])}
        repo       (git-repo/create-git-repo git-config)]
    (when (:remote-url git-config)
      (git-repo/clone-or-open git-config))
    (log/info "Git repository initialized at" (:repo-path git-config)
              "(writer:" (:writer? git-config) ")")
    repo))

(defmethod ig/halt-key! ::git-repo [_ repo]
  (.close repo))

(defmethod ig/init-key ::card-repo [_ {:keys [git-repo]}]
  (git-cards/create-card-repository git-repo))

(defmethod ig/init-key ::set-repo [_ {:keys [git-repo]}]
  (git-sets/create-set-repository git-repo))

(defmethod ig/init-key ::card-service [_ {:keys [card-repo]}]
  (card-svc/create-card-service card-repo))

(defmethod ig/init-key ::set-service [_ {:keys [set-repo card-repo]}]
  (set-svc/create-set-service set-repo card-repo))

(defmethod ig/init-key ::resolver-map [_ _]
  (gql-schema/resolver-map))

(defmethod ig/init-key ::github-oidc-client [_ {:keys [config]}]
  (gh-auth/create-github-oidc-client
   {:client-id (get-in config [:github :oauth :client-id])
    :client-secret (get-in config [:github :oauth :client-secret])
    :redirect-uri (get-in config [:github :oauth :redirect-uri])
    :scopes ["user:email" "read:user"]}))

(defmethod ig/init-key ::handler [_ {:keys [resolver-map
                                            github-oidc-client
                                            authenticator
                                            db-pool
                                            user-repo
                                            git-repo
                                            card-repo
                                            set-repo
                                            config]}]
  (handler/create-handler
   resolver-map
   github-oidc-client
   authenticator
   db-pool
   user-repo
   git-repo
   card-repo
   set-repo
   (:session config)
   config))

(defmethod ig/init-key ::server [_ {:keys [handler config port-override]}]
  (let [port (or port-override (get-in config [:server :port]))
        host (get-in config [:server :host])]
    (jetty/run-jetty handler
                     {:port port
                      :host host
                      :join? false})))

(defmethod ig/halt-key! ::server [_ server]
  (.stop server))

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
   ::user-repo {}
   ::session-repo {:config (ig/ref ::config)}
   ::github-claims-provider {:config (ig/ref ::config)}
   ::auth-service {:user-repo (ig/ref ::user-repo)
                   :config (ig/ref ::config)
                   :github-claims-provider (ig/ref ::github-claims-provider)}
   ::authenticator {:auth-service (ig/ref ::auth-service)
                    :session-repo (ig/ref ::session-repo)
                    :config (ig/ref ::config)}
   ::git-repo {:config (ig/ref ::config)}
   ::card-repo {:git-repo (ig/ref ::git-repo)}
   ::set-repo {:git-repo (ig/ref ::git-repo)}
   ::card-service {:card-repo (ig/ref ::card-repo)}
   ::set-service {:set-repo (ig/ref ::set-repo)
                  :card-repo (ig/ref ::card-repo)}
   ::resolver-map {}
   ::github-oidc-client {:config (ig/ref ::config)}
   ::handler {:resolver-map (ig/ref ::resolver-map)
              :github-oidc-client (ig/ref ::github-oidc-client)
              :authenticator (ig/ref ::authenticator)
              :db-pool (ig/ref ::db-pool)
              :user-repo (ig/ref ::user-repo)
              :git-repo (ig/ref ::git-repo)
              :card-repo (ig/ref ::card-repo)
              :set-repo (ig/ref ::set-repo)
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
