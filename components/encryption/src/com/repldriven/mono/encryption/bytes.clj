(ns com.repldriven.mono.encryption.bytes
  (:import
    (java.security MessageDigest)))

(defn equals?
  "Constant-time comparison of two byte arrays."
  [^bytes a ^bytes b]
  (MessageDigest/isEqual a b))
