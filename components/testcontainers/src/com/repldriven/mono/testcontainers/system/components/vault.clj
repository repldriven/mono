(ns com.repldriven.mono.testcontainers.system.components.vault
  (:require
    [com.repldriven.mono.testcontainers.container :as container]
    [com.repldriven.mono.log.interface :as log]
    [clojure.string :as string])
  (:import
    (java.time Duration)
    (org.testcontainers.vault VaultContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-exposed-ports [8200])
(def default-docker-image-name "hashicorp/vault:1.21")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-ports vault-token
                       secret-in-vault init-commands]}
               config]
           (log/info "Starting vault container")
           (let [c (-> (DockerImageName/parse docker-image-name)
                       (.asCompatibleSubstituteFor "vault")
                       (VaultContainer.))]
             (when vault-token (.withVaultToken c vault-token))
             (when secret-in-vault
               (let [path (first secret-in-vault)
                     key-values (rest secret-in-vault)
                     kv-put-cmd (str "kv put " path
                                     " " (string/join " " key-values))]
                 (.withInitCommand c (into-array String [kv-put-cmd]))))
             (when init-commands
               (.withInitCommand c (into-array String init-commands)))
             (.withStartupTimeout c (Duration/ofSeconds 60))
             (container/start! c exposed-ports)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping vault container")
                  (container/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-ports default-exposed-ports}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-ports [:vector int?]]]
   :system/instance-schema map?})
