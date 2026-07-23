(ns com.repldriven.mono.encryption.token
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]]

    [buddy.core.codecs :as codecs]
    [buddy.core.hash :as hash]
    [buddy.core.nonce :as nonce]))

(defn generate
  [prefix]
  (try-nom :encryption/generate-token
           "Failed to generate token"
           (str prefix
                (codecs/bytes->str (codecs/bytes->b64 (nonce/random-bytes 32)
                                                      true)))))

;; Hashing a nil or non-byte-able token throws out of buddy rather than
;; returning anything, and the input is usually a token off the wire.
(defn digest
  [raw-token]
  (try-nom :encryption/hash-token
           "Failed to hash token"
           (codecs/bytes->hex (hash/sha256 raw-token))))
