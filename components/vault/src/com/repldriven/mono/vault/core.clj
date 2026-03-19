(ns com.repldriven.mono.vault.core
  (:require
    [com.repldriven.mono.vault.client :as client]))

(defn create-client [uri] (client/create uri))

(defn authenticate-client!
  [client auth-type credentials]
  (client/authenticate! client auth-type credentials))

(defn read-secret
  [client mount path & opts]
  (if (some? opts)
    (client/read-secret client mount path opts)
    (client/read-secret client mount path)))
