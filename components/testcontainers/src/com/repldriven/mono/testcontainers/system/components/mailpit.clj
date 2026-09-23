(ns com.repldriven.mono.testcontainers.system.components.mailpit
  (:require
    [com.repldriven.mono.testcontainers.container :as container]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.time Duration)
    (org.testcontainers.containers GenericContainer)))

(def default-exposed-ports [1025 8025])
(def default-docker-image-name "axllent/mailpit:v1.31.1")
(def default-smtp-auth "test:test")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-ports smtp-auth]} config]
           (log/info "Starting mailpit container")
           (-> (GenericContainer. ^String docker-image-name)
               (doto (.withEnv "MP_SMTP_AUTH" ^String smtp-auth)
                     (.withEnv "MP_SMTP_AUTH_ALLOW_INSECURE" "true")
                     (.withStartupTimeout (Duration/ofSeconds 60)))
               (container/start! exposed-ports
                                 {:reuse? (container/reuse? config)})))))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping mailpit container")
                  (container/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-ports default-exposed-ports
                   :smtp-auth default-smtp-auth
                   :reuse nil}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-ports [:vector int?]] [:smtp-auth string?]
                          [:reuse {:optional true}
                           [:maybe [:or boolean? string?]]]]
   :system/instance-schema map?})
