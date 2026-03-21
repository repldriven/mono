(ns com.repldriven.mono.pulsar.pulsar.crypto
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log]
    [clojure.java.io :as io])
  (:import
    (org.apache.pulsar.client.api CryptoKeyReader EncryptionKeyInfo)
    (java.util Map)))

(defn- get-crypto-key-pair
  [k named-kps]
  (let [k' (if (empty? k) (get named-kps :default-key) k)
        k'' (if (keyword? k') k' (keyword k'))]
    (get-in named-kps [:keys k''])))

(defn- read-file-as-bytes
  [f]
  (log/info "Reading Pulsar crypto key pair file:" f)
  (-> f
      io/resource
      io/file
      slurp
      .getBytes))

;; TODO: ->der-string does not work, require ->pem-string instead
(defn key-pair-generator
  [named-kps]
  (try-nom
   :pulsar/crypto-key-pair-generator
   "Failed to generate Pulsar crypto key pairs"
   (reduce-kv (fn [m k v]
                (assoc m
                       k
                       (let [kp (encryption/create-key-pair v)]
                         {:public-key (-> kp
                                          (get :public-key)
                                          (encryption/public-key->der-string))
                          :private-key
                          (->
                            kp
                            (get :private-key)
                            (encryption/private-key->der-string))})))
              {}
              named-kps)))

(defn key-pair-file-reader
  [named-kps]
  (try-nom :pulsar/crypto-key-pair-file-reader
           "Failed to read Pulsar crypto key pair files"
           (reduce-kv
            (fn [m k v]
              (assoc m
                     k
                     {:public-key (read-file-as-bytes (:public-key v))
                      :private-key (read-file-as-bytes (:private-key
                                                        v))}))
            {}
            named-kps)))

(defn- key->encryption-key-info [k] (doto (EncryptionKeyInfo.) (.setKey k)))

(defn key-reader
  [named-kps]
  (try-nom
   :pulsar/crypto-key-reader
   "Failed to create Pulsar crypto key reader"
   (reify
    CryptoKeyReader
      (^EncryptionKeyInfo getPublicKey
        [_this ^String keyName ^Map _metadata]
        (key->encryption-key-info (get (get-crypto-key-pair keyName named-kps)
                                       :public-key)))
      (^EncryptionKeyInfo getPrivateKey
        [_this ^String keyName ^Map _metadata]
        (key->encryption-key-info (get (get-crypto-key-pair keyName named-kps)
                                       :private-key))))))

(comment
  (let [key-name "tenant-key-1"
        metadata (java.util.HashMap. {"tenant-key-1" "1st key"})
        r (key-reader {:default-key key-name
                       :keys (key-pair-generator {key-name {:algorithm "RSA"
                                                            :key-size 512}})})]
    {:private-key (encryption/private-key-pkcs8-encoded->rsa
                   (.getKey (.getPrivateKey r key-name metadata)))
     :public-key (encryption/public-key-x509-encoded->rsa
                  (.getKey (.getPublicKey r key-name metadata)))}))
