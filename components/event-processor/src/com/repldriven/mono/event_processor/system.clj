(ns com.repldriven.mono.event-processor.system
  (:require
    [com.repldriven.mono.event.interface :as event]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private event-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [processor bus event-channel]} config]
                         (log/info "Starting event-processor:" event-channel)
                         (event/process bus
                                        #(processor/process processor %)
                                        {:event-channel event-channel}))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when-let [stop-fn (:stop instance)]
                    (log/info "Stopping event-processor")
                    (stop-fn)))
   :system/config {:processor system/required-component
                   :bus system/required-component
                   :event-channel system/required-component}
   :system/instance-schema some?})

(system/defcomponents :event-processor {:event-processor event-processor})
