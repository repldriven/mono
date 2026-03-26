(ns com.repldriven.mono.bank-monolith.main
  (:require
    com.repldriven.mono.bank-cash-account.interface
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.bank-transaction.interface
    com.repldriven.mono.command.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.bank-interest.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.bank-bootstrap.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    [com.repldriven.mono.bank-api.api :as api]
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

(defn stop [system] (system/stop system))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "bank-monolith"
                                                              args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            sys (start config-file (keyword profile))]
        (if (error/anomaly? sys)
          (cli/exit false
                    (str "Failed to start [" (error/kind sys)
                         "]: " (or (:message sys) "Unknown error")))
          (do (log/info "System started successfully") @(promise)))))))
