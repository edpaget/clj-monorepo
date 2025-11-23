(ns bashketball-editor-api.server
  "Application entry point.

  Provides functions to start and stop the server."
  (:require
   [bashketball-editor-api.system :as system]
   [clojure.tools.logging :as log]))

(defonce ^:private state (atom nil))

(defn stop!
  "Stops the application server."
  []
  (when-let [sys @state]
    (log/info "Stopping system...")
    (system/stop-system sys)
    (reset! state nil)
    (log/info "System stopped successfully")))

(defn start!
  "Starts the application server.

  Optionally takes a profile keyword (defaults to `:dev`). Stores the system
  in an atom for later shutdown."
  ([]
   (start! :dev))
  ([profile]
   (when @state
     (log/warn "System already running, stopping first...")
     (stop!))
   (log/info "Starting system with profile:" profile)
   (let [sys (system/start-system profile)]
     (reset! state sys)
     (log/info "System started successfully")
     sys)))

(defn restart!
  "Restarts the application server with the same profile."
  []
  (stop!)
  (start!))

(defn -main
  "Main entry point for running the application."
  [& args]
  (let [profile (if (seq args)
                  (keyword (first args))
                  :dev)]
    (start! profile)
    (.. Runtime getRuntime (addShutdownHook (Thread. stop!)))))
