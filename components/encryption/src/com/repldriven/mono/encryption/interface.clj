(ns com.repldriven.mono.encryption.interface
  "RSA keys, opaque tokens, and constant-time byte comparison.

  Everything here fails the way the rest of the workspace does, with
  an anomaly. That matters more than usual for this brick: encoded
  keys and raw tokens arrive from vaults, config files and request
  headers, so malformed input is an ordinary runtime condition rather
  than a programming error, and a thrown `InvalidKeySpecException`
  halfway down a request is the wrong shape for it."
  (:require
    [com.repldriven.mono.encryption.bytes :as bytes]
    [com.repldriven.mono.encryption.rsa :as rsa]
    [com.repldriven.mono.encryption.token :as token]))

(defn create-key-pair
  "Generate an RSA key pair, or return an anomaly — including when
  `opts` asks for an algorithm or size this brick does not support,
  in which case the anomaly says what is supported.

  Args:
  - opts: `{:algorithm \"RSA\" :key-size 512}`."
  [opts]
  (rsa/create-key-pair opts))

(defn private-key-pkcs8-encoded->rsa
  "Decode PKCS8-encoded bytes into an RSA private key, or return an
  anomaly when they are malformed.

  Args:
  - encoded-key: the PKCS8-encoded byte array."
  [encoded-key]
  (rsa/pkcs8-encoded->private-key encoded-key))

(defn public-key-x509-encoded->rsa
  "Decode X509-encoded bytes into an RSA public key, or return an
  anomaly when they are malformed.

  Args:
  - encoded-key: the X509-encoded byte array."
  [encoded-key]
  (rsa/x509-encoded->public-key encoded-key))

(defn public-key->der-string
  "DER-encode an RSA public key as a base64 string, or return an
  anomaly.

  Args:
  - k: a `java.security.PublicKey`."
  [k]
  (rsa/public-key->der-string k))

(defn private-key->der-string
  "DER-encode an RSA private key as a base64 string, or return an
  anomaly.

  Args:
  - k: a `java.security.PrivateKey`."
  [k]
  (rsa/private-key->der-string k))

(defn generate-token
  "Generate a random URL-safe token carrying `prefix`, or return an
  anomaly. 32 bytes of randomness, base64-encoded.

  Args:
  - prefix: a string prepended to the token, so its origin is
    recognisable."
  [prefix]
  (token/generate prefix))

(defn hash-token
  "Hex-encoded SHA-256 of a raw token, or an anomaly. Store this
  rather than the token itself.

  Args:
  - raw-token: the token string."
  [raw-token]
  (token/digest raw-token))

(defn bytes-equals?
  "Constant-time comparison of two byte arrays, so a caller comparing
  secrets does not leak their contents through how long the
  comparison took. Nil-safe, and never throws.

  Args:
  - a, b: byte arrays."
  [a b]
  (bytes/equals? a b))
