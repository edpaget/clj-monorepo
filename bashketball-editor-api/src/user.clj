(ns user
  "Development REPL utilities.

  Provides functions for starting, stopping, and restarting the system
  from the REPL."
  (:require
   [bashketball-editor-api.system :as system]))

(defonce ^:private system-state (atom nil))

(defn start
  "Starts the development system.

  Optionally takes a profile keyword (defaults to :dev)."
  ([]
   (start :dev))
  ([profile]
   (when @system-state
     (println "System already running. Call (stop) first or use (restart)."))
   (when-not @system-state
     (println "Starting system with profile:" profile)
     (reset! system-state (system/start-system profile))
     (println "System started successfully.")
     :started)))

(defn stop
  "Stops the running system."
  []
  (if @system-state
    (do
      (println "Stopping system...")
      (system/stop-system @system-state)
      (reset! system-state nil)
      (println "System stopped.")
      :stopped)
    (do
      (println "System not running.")
      :not-running)))

(defn restart
  "Restarts the system.

  Optionally takes a profile keyword (defaults to :dev)."
  ([]
   (restart :dev))
  ([profile]
   (stop)
   (start profile)))

(defn system
  "Returns the currently running system, or nil if not started."
  []
  @system-state)

(comment
  ;; Start the system
  (start)
  (start :dev)

  ;; Stop the system
  (stop)

  ;; Restart the system
  (restart)

  ;; Get the running system
  (system)

  ;; Access specific components
  (::system/config (system))
  (::system/db-pool (system))
  (::system/handler (system)))
