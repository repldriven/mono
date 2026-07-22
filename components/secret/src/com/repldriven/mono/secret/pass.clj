(ns com.repldriven.mono.secret.pass
  (:require
    [clojure.java.shell :as shell]
    [clojure.string :as str]
    [com.repldriven.mono.secret.protocol :as protocol]
    [com.repldriven.mono.error.interface :as error]))

(defn- pass-show
  [entry]
  (let [{:keys [exit out err]} (shell/sh "pass" "show" entry)]
    (if (zero? exit)
      ;; The secret is the first line; pass entries conventionally
      ;; carry any metadata on later lines.
      (first (str/split-lines out))
      (error/fail :secret/pass-read-failed
                  {:message (str "`pass show` failed for entry: " entry)
                   :entry entry
                   :exit exit
                   :err err}))))

(defrecord PassSecretProvider [secret-map]
  protocol/SecretProvider
    (-get-secret [_ secret-id]
      (if-let [entry (get secret-map secret-id)]
        (error/let-nom> [secret (pass-show entry)]
          (.getBytes ^String secret "UTF-8"))
        (error/fail :secret/unknown-secret-id
                    {:message (str "No pass entry mapped for secret id: "
                                   secret-id)
                     :secret-id secret-id}))))
