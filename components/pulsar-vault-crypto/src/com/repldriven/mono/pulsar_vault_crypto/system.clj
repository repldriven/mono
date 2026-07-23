(ns com.repldriven.mono.pulsar-vault-crypto.system
  (:require
    [com.repldriven.mono.pulsar-vault-crypto.core :as core]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.vault.interface :as vault]))

(def tenant-key-reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [client token tenant-id mount]} config]
           (log/info "Creating vault-backed crypto key reader for tenant:"
                     tenant-id)
           ;; vault returns an anomaly rather than throwing, so
           ;; failing to authenticate has to be raised here or
           ;; the component would start with a client that
           ;; cannot read anything.
           (let [auth (vault/authenticate-client! client :token token)]
             (when (error/anomaly? auth)
               ;; nosemgrep: no-raw-throw
               (throw (ex-info "Vault authentication failed"
                               {:tenant-id tenant-id :anomaly auth}))))
           (core/tenant-key-reader client tenant-id mount))))
   :system/config {:client system/required-component
                   :token system/required-component
                   :tenant-id system/required-component
                   :mount "secret"}
   :system/config-schema [:map [:client some?] [:token string?]
                          [:tenant-id string?]]
   :system/instance-schema some?})

(system/defcomponents :pulsar-vault-crypto
                      {:tenant-key-reader tenant-key-reader})
