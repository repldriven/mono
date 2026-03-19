(ns com.repldriven.mono.bank-api.auth
  (:require
    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.bank-api-key.interface :as bank-api-key]
    [com.repldriven.mono.utility.interface :as util]
    [clojure.string :as str]))

(def ^:private api-key-cache (cache/create 60000))

(defn- extract-bearer
  [request]
  (some-> (get-in request [:headers "authorization"])
          (str/split #" " 2)
          (as-> parts (when (= "Bearer" (first parts)) (second parts)))))

(defn- verify-org-key
  [request raw-key]
  (let [key-hash (encryption/hash-token raw-key)
        {:keys [record-db record-store]} request
        api-key (cache/lookup api-key-cache
                              key-hash
                              #(bank-api-key/get-api-key {:record-db record-db
                                                          :record-store
                                                          record-store}
                                                         key-hash))]
    (when (and (map? api-key) (zero? (:revoked-at api-key 0)))
      {:role :org :organization-id (:organization-id api-key)})))

(def authenticate
  {:name ::authenticate
   :enter (fn [ctx]
            (let [request (:request ctx)
                  raw-key (extract-bearer request)
                  admin-api-key (:admin-api-key request)]
              (cond (nil? raw-key)
                    ctx
                    (encryption/bytes-equals? (util/str->bytes raw-key)
                                              (util/str->bytes admin-api-key))
                    (assoc-in ctx [:request :auth] {:role :admin})
                    :else
                    (if-let [auth (verify-org-key request raw-key)]
                      (assoc-in ctx [:request :auth] auth)
                      ctx))))})
