(ns com.repldriven.mono.encryption.rsa
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

(defn create-key-pair
  "Generates an RSA key pair for the given opts."
  [opts]
  (case (select-keys opts [:algorithm :key-size])
    {:algorithm "RSA" :key-size 512}
    (when-let [key-pair (.generateKeyPair key-pair-gen-rsa-512)]
      {:private-key (.getPrivate key-pair)
       :public-key (.getPublic key-pair)
       :algorithm "RSA"
       :key-size 512})))

(defn pkcs8-encoded->private-key
  "Decodes a PKCS8-encoded byte array to an RSA private key."
  [encoded-key]
  (.generatePrivate key-factory-rsa (PKCS8EncodedKeySpec. encoded-key)))

(defn x509-encoded->public-key
  "Decodes an X509-encoded byte array to an RSA public key."
  [encoded-key]
  (.generatePublic key-factory-rsa (X509EncodedKeySpec. encoded-key)))

(defn public-key->der-string
  "Returns the DER-encoded base64 string of an RSA public key."
  [^PublicKey k]
  (-> k
      .getEncoded
      encode64
      (.replace "\n" "")))

(defn private-key->der-string
  "Returns the DER-encoded base64 string of an RSA private key."
  [^PrivateKey k]
  (-> k
      .getEncoded
      PKCS8EncodedKeySpec.
      .getEncoded
      encode64))
