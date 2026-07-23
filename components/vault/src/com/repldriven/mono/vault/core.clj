(ns com.repldriven.mono.vault.core
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.vault.client :as client]))

;; vault-clj signals every failure by throwing — a bad URI, a rejected
;; login, a missing path, an unreachable server. All of those are things
;; that happen to a running service rather than mistakes in its code.
(defn create-client
  [uri]
  (try-nom :vault/create-client
           "Failed to create Vault client"
           (client/create
            uri)))

(defn authenticate-client!
  [client auth-type credentials]
  (try-nom :vault/authenticate
           "Failed to authenticate Vault client"
           (client/authenticate! client auth-type credentials)))

(defn read-secret
  [client mount path & opts]
  (try-nom :vault/read-secret
           "Failed to read Vault secret"
           (if (some? opts)
             (client/read-secret client mount path opts)
             (client/read-secret client mount path))))
