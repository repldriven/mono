(ns com.repldriven.mono.bank-api.account-products.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]

    [com.repldriven.mono.account-products.interface
     :as account-products]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (java.time Instant)))

(defn- millis->iso
  [ms]
  (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-version
  [version]
  (-> version
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

(defn create-product
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        body (get-in request [:parameters :body])
        result
        (account-products/new-product
         {:record-db record-db :record-store record-store}
         org-id
         body)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 201
       :body (format-version (:version result))})))

(defn create-version
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request
                                     [:parameters :path])
        body (get-in request [:parameters :body])
        result
        (account-products/new-version
         {:record-db record-db :record-store record-store}
         org-id
         product-id
         body)]
    (cond
     (error/anomaly? result)
     (if (= :account-products/draft-exists
            (error/kind result))
       {:status 409 :body (error-response 409 result)}
       {:status 500 :body (error-response 500 result)})

     :else
     {:status 201
      :body (format-version (:version result))})))

(defn publish-version
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]}
        (get-in request [:parameters :path])
        result
        (account-products/publish
         {:record-db record-db :record-store record-store}
         org-id
         product-id
         version-id)]
    (cond
     (error/anomaly? result)
     (let [kind (error/kind result)]
       (cond
        (= :account-products/version-not-found kind)
        {:status 404 :body (error-response 404 result)}

        (= :account-products/not-draft kind)
        {:status 409 :body (error-response 409 result)}

        :else
        {:status 500 :body (error-response 500 result)}))

     :else
     {:status 200 :body (format-version result)})))
