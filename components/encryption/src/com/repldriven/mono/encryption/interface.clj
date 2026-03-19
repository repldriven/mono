(ns com.repldriven.mono.encryption.interface
  (:require
    [com.repldriven.mono.encryption.token :as token]
    [com.repldriven.mono.encryption.bytes :as bytes]
    [com.repldriven.mono.encryption.id :as id]
    [com.repldriven.mono.encryption.rsa :as rsa]))

(defn create-key-pair [opts] (rsa/create-key-pair opts))

(defn private-key-pkcs8-encoded->rsa
  [encoded-key]
  (rsa/pkcs8-encoded->private-key encoded-key))

(defn public-key-x509-encoded->rsa
  [encoded-key]
  (rsa/x509-encoded->public-key encoded-key))

(defn public-key->der-string [k] (rsa/public-key->der-string k))

(defn private-key->der-string [k] (rsa/private-key->der-string k))

(defn generate-id [prefix] (id/generate prefix))

(defn generate-token [prefix] (token/generate prefix))

(defn hash-token [raw-key] (token/digest raw-key))

(defn bytes-equals? [a b] (bytes/equals? a b))
