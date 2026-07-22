(ns com.repldriven.mono.secret.fixture
  (:require
    [com.repldriven.mono.secret.protocol :as protocol]
    [com.repldriven.mono.error.interface :as error]))

(defrecord FixtureSecretProvider [secrets]
  protocol/SecretProvider
    (-get-secret [_ secret-id]
      (if-let [value (get secrets secret-id)]
        (.getBytes ^String value "UTF-8")
        (error/fail :secret/unknown-secret-id
                    {:message (str "No fixture secret for secret id: "
                                   secret-id)
                     :secret-id secret-id}))))
