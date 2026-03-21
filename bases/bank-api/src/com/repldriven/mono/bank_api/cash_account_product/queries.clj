(ns com.repldriven.mono.bank-api.cash-account-product.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     cash-account-products]
    [com.repldriven.mono.error.interface :as error]))

(defn list-all-versions
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        result (cash-account-products/get-versions {:record-db record-db
                                                    :record-store record-store}
                                                   org-id)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200
       :body {:versions (:versions result)}})))

(defn get-published-version
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-published {:record-db record-db
                                                     :record-store record-store}
                                                    org-id
                                                    product-id)]
    (cond (error/anomaly? result)
          {:status 500
           :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response
                  404 "REJECTED"
                  "cash-account-products/no-published-version"
                  "No published version found")}
          :else
          {:status 200 :body result})))

(defn list-versions
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-versions {:record-db record-db
                                                    :record-store record-store}
                                                   org-id
                                                   product-id)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200
       :body {:versions (:versions result)}})))

(defn get-version
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-version {:record-db record-db
                                                   :record-store record-store}
                                                  org-id
                                                  product-id
                                                  version-id)]
    (cond (error/anomaly? result)
          {:status 500
           :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response
                  404 "REJECTED"
                  "cash-account-products/version-not-found"
                  "Version not found")}
          :else
          {:status 200 :body result})))
