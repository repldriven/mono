(ns com.repldriven.mono.vault.client
  (:require
    vault.client.http
    [vault.core :as vault]
    [vault.secrets.kvv2 :as vault-kvv2]))

(defn create [uri] (vault/new-client uri))

(defn authenticate!
  [client auth-type credentials]
  (vault/authenticate! client auth-type credentials))

(defn read-secret
  ([client mount path] (vault-kvv2/read-secret client mount path))
  ([client mount path opts] (vault-kvv2/read-secret client mount path opts)))
