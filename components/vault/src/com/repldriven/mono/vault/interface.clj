(ns com.repldriven.mono.vault.interface
  (:require
    com.repldriven.mono.vault.system
    [com.repldriven.mono.vault.core :as core]))

(defn create-client [uri] (core/create-client uri))

(defn authenticate-client!
  [client auth-type credentials]
  (core/authenticate-client! client auth-type credentials))

(defn read-secret
  [client mount path & opts]
  (apply core/read-secret client mount path opts))
