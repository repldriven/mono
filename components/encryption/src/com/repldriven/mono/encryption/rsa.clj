(ns com.repldriven.mono.encryption.rsa
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]])
  (:import
    (java.security KeyFactory KeyPairGenerator PrivateKey PublicKey)
    (java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec)
    (java.util Base64)))

(defonce ^:private ^KeyPairGenerator key-pair-gen-rsa-512
  (when-not *compile-files*
    (doto (KeyPairGenerator/getInstance "RSA") (.initialize 512))))

(defonce ^:private ^KeyFactory key-factory-rsa
  (when-not *compile-files* (KeyFactory/getInstance "RSA")))

(defn- encode64 [bytes] (.encodeToString (Base64/getEncoder) bytes))

(def supported {:algorithm "RSA" :key-size 512})

(defn create-key-pair
  [opts]
  (let [requested (select-keys opts [:algorithm :key-size])]
    (if-not (= supported requested)
      ;; This was a `case` with a single clause and no default, so anything
      ;; else threw "No matching clause", which told the caller nothing
      ;; about what was on offer.
      (error/fail :encryption/create-key-pair
                  {:message "Unsupported key algorithm or size"
                   :requested requested
                   :supported supported})
      (try-nom :encryption/create-key-pair
               "Failed to generate key pair"
               (let [key-pair (.generateKeyPair key-pair-gen-rsa-512)]
                 {:private-key (.getPrivate key-pair)
                  :public-key (.getPublic key-pair)
                  :algorithm "RSA"
                  :key-size 512})))))

;; Encoded keys arrive from a vault, a config file or a database column, so
;; malformed bytes are an ordinary runtime condition rather than a bug:
;; InvalidKeySpecException for the wrong shape, NullPointerException for nil.
(defn pkcs8-encoded->private-key
  [encoded-key]
  (try-nom :encryption/private-key
           "Failed to decode PKCS8-encoded private key"
           (.generatePrivate key-factory-rsa
                             (PKCS8EncodedKeySpec. encoded-key))))

(defn x509-encoded->public-key
  [encoded-key]
  (try-nom :encryption/public-key
           "Failed to decode X509-encoded public key"
           (.generatePublic key-factory-rsa (X509EncodedKeySpec. encoded-key))))

(defn public-key->der-string
  [^PublicKey k]
  (try-nom :encryption/der-string
           "Failed to DER-encode public key"
           (-> k
               .getEncoded
               encode64
               (.replace "\n" ""))))

(defn private-key->der-string
  [^PrivateKey k]
  (try-nom :encryption/der-string
           "Failed to DER-encode private key"
           (-> k
               .getEncoded
               PKCS8EncodedKeySpec.
               .getEncoded
               encode64)))
