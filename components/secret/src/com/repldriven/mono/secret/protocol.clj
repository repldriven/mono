(ns com.repldriven.mono.secret.protocol)

(defprotocol SecretProvider
  (-get-secret [this secret-id]))
