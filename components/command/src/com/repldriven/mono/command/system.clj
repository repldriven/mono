(ns com.repldriven.mono.command.system
  (:require
    [com.repldriven.mono.command.dispatcher :as dispatcher]
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents
 :command
 {:dispatcher
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [bus command-channel command-response-channel]} config]
           (dispatcher/start bus command-channel command-response-channel))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when instance (dispatcher/stop instance)))
   :system/config {:bus system/required-component
                   :command-channel nil
                   :command-response-channel nil}}})
