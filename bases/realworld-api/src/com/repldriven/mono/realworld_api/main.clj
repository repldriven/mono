(ns com.repldriven.mono.realworld-api.main
  (:require
    com.repldriven.mono.auth.interface
    com.repldriven.mono.command.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.jdbc.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.migrator.interface
    com.repldriven.mono.realworld-store.interface
    com.repldriven.mono.server.interface

    [com.repldriven.mono.realworld-api.api :as api]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom->]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(defn start
  [config-file profile]
  (nom-> (env/config config-file profile)
         system/defs
         (assoc-in [:system/defs :server :handler] api/app)
         system/start))

(defn stop
  [system]
  (system/stop system))

(defn -main
  [& args]
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "realworld-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            sys (start config-file (keyword profile))]
        (if (error/anomaly? sys)
          (cli/exit false
                    (str "Failed to start ["
                         (error/kind sys)
                         "]: "
                         (or (:message (error/payload sys)) "Unknown error")))
          (do (system/stop-on-shutdown sys)
              (log/info "realworld-api started")
              @(promise)))))))
