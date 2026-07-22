(ns com.repldriven.mono.secret.env
  (:require
    [com.repldriven.mono.secret.protocol :as protocol]
    [com.repldriven.mono.error.interface :as error]))

(defrecord EnvSecretProvider [secret-map]
  protocol/SecretProvider
    (-get-secret [_ secret-id]
      (if-let [var-name (get secret-map secret-id)]
        (if-let [value (System/getenv var-name)]
          (.getBytes ^String value "UTF-8")
          (error/fail :secret/env-not-set
                      {:message (str "Environment variable not set: " var-name)
                       :secret-id secret-id
                       :env-var var-name}))
        (error/fail :secret/unknown-secret-id
                    {:message (str "No env var mapped for secret id: "
                                   secret-id)
                     :secret-id secret-id}))))
