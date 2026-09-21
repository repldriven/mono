(ns com.repldriven.mono.auth.token
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.utility.interface :as util]

    [buddy.sign.jwt :as jwt]

    [clojure.string :as str])
  (:import
    (java.nio.charset StandardCharsets)
    (java.util Base64)))

(def default-ttl-seconds (* 60 60 24 7))

(def default-schemes #{"token" "bearer"})

(defn- now-seconds
  []
  (quot (util/now) 1000))

(defn header->token
  [header schemes]
  (when (string? header)
    (let [[scheme credential] (str/split (str/trim header) #"\s+" 2)]
      (when (and credential (contains? schemes (str/lower-case scheme)))
        (let [credential (str/trim credential)]
          (when (seq credential) credential))))))

(defn sign
  [signer claims]
  (let [{:keys [secret ttl-seconds]} signer
        ttl (or ttl-seconds default-ttl-seconds)
        now (now-seconds)]
    (if-not (and (string? secret) (seq secret))
      (error/fail :auth/sign-token "Signer has no secret")
      (try-nom :auth/sign-token
               "Failed to sign token"
               (jwt/sign (assoc claims :iat now :exp (+ now ttl))
                         secret
                         {:alg :hs256})))))

(defn verify
  [signer jwt-string]
  (let [{:keys [secret]} signer]
    (cond
     (not (and (string? secret) (seq secret)))
     (error/fail :auth/verify-token "Signer has no secret")

     (not (and (string? jwt-string) (seq jwt-string)))
     (error/unauthorized :auth/invalid-token "Token is missing")

     :else
     ;; buddy throws for a bad signature, malformed token and expiry alike.
     ;; All three are the caller presenting something we will not accept,
     ;; so they collapse to one unauthorized anomaly rather than leaking
     ;; which.
     (let [result (try-nom :auth/verify-token
                           "Failed to verify token"
                           (jwt/unsign jwt-string secret {:alg :hs256}))]
       (if (error/anomaly? result)
         (error/unauthorized :auth/invalid-token "Token is invalid or expired")
         result)))))

(defn unverified-claims
  [jwt-string]
  (when (string? jwt-string)
    (let [[_ payload] (str/split jwt-string #"\." 3)
          decoded (when payload
                    (try-nom :auth/unverified-claims
                             "Payload is not base64url"
                             (String. (.decode (Base64/getUrlDecoder)
                                               ^String payload)
                                      StandardCharsets/UTF_8)))
          claims (when (string? decoded)
                   (json/read-str decoded :key-fn keyword))]
      (when (and (map? claims) (not (error/anomaly? claims))) claims))))
