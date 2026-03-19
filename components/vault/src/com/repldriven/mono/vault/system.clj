(ns com.repldriven.mono.vault.system
  (:require
    [com.repldriven.mono.vault.client :as client]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(def vault-client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [api-url]} config]
                         (log/info "Creating vault api client:" api-url)
                         (client/create api-url))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Relinquishing vault api client")))
   :system/config {:api-url system/required-component}
   :system/config-schema [:map [:api-url string?]]
   :system/instance-schema some?})

(system/defcomponents :vault {:client vault-client})
