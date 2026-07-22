(ns com.repldriven.mono.secret.gcp
  (:require
    [com.repldriven.mono.secret.protocol :as protocol]
    [com.repldriven.mono.error.interface :as error]))

(defrecord SecretManagerProvider [project secret-map]
  protocol/SecretProvider
    (-get-secret [_ secret-id]
      ;; TODO: accessSecretVersion on (secret-map secret-id) through a
      ;; GCP Secret Manager client wrapper brick (Workload Identity
      ;; auth), returning the payload bytes. Stubbed until that brick
      ;; exists so the prod wiring has a provider to bind.
      (error/fail :secret/provider-not-implemented
                  {:message "GCP Secret Manager provider not implemented"
                   :secret-id secret-id
                   :project project})))
