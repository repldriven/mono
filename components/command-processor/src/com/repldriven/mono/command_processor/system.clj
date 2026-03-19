(ns com.repldriven.mono.command-processor.system
  (:require
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private command-processor
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [processor bus command-channel command-response-channel]}
               config]
           (log/info "Starting command-processor:" command-channel)
           (command/process bus
                            #(processor/process processor %)
                            {:command-channel command-channel
                             :command-response-channel
                             command-response-channel}))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when-let [stop-fn (:stop instance)]
                    (log/info "Stopping command-processor")
                    (stop-fn)))
   :system/config {:processor system/required-component
                   :bus system/required-component
                   :command-channel system/required-component
                   :command-response-channel system/required-component}
   :system/instance-schema some?})

(system/defcomponents :command-processor {:command-processor command-processor})
