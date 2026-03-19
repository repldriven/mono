(ns com.repldriven.mono.encryption.id
  (:require
    [buddy.core.codecs :as codecs]
    [buddy.core.nonce :as nonce]))

(defn generate
  "Returns a URL-safe base64 ID with the given prefix."
  [prefix]
  (str prefix "." (codecs/bytes->b64-str (nonce/random-bytes 16) true)))
