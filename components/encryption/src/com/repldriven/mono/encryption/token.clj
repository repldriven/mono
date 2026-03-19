(ns com.repldriven.mono.encryption.token
  (:require
    [buddy.core.codecs :as codecs]
    [buddy.core.hash :as hash]
    [buddy.core.nonce :as nonce]))

(defn generate
  "Returns a random token string with the given prefix."
  [prefix]
  (str prefix
       (codecs/bytes->str (codecs/bytes->b64 (nonce/random-bytes 32) true))))

(defn digest
  "Returns the hex-encoded SHA-256 hash of a raw token."
  [raw-token]
  (codecs/bytes->hex (hash/sha256 raw-token)))
