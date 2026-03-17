(ns com.repldriven.mono.cash-account-products.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]

    [clojure.string :as str]))

(defn- ->enum
  "Coerces a string or keyword to a lower-case, hyphenated
  keyword suitable for protobuf enum fields."
  [v]
  (when v
    (keyword (str/replace (str/lower-case (name v)) "_" "-"))))

(defn- ->balance-product
  "Coerces balance-product string fields to keywords."
  [{:keys [balance-type balance-status]}]
  {:balance-type (->enum balance-type)
   :balance-status (->enum balance-status)})

(defn new-version
  "Creates a new CashAccountProductVersion record map in
  draft status."
  [organization-id product-id version-number data]
  (let [now (System/currentTimeMillis)]
    (cond-> (merge {:organization-id organization-id
                    :product-id product-id
                    :version-id (encryption/generate-id "prv")
                    :version-number version-number
                    :status "draft"
                    :created-at now
                    :updated-at now}
                   (select-keys data
                                [:name :allowed-currencies
                                 :valid-from :valid-to]))
            (:account-type data)
            (assoc :account-type (->enum (:account-type data)))
            (:balance-sheet-side data)
            (assoc :balance-sheet-side
                   (->enum (:balance-sheet-side data)))
            (seq (:balance-products data))
            (assoc :balance-products
                   (mapv ->balance-product
                         (:balance-products data))))))

(defn publish
  "Sets version status to published."
  [version]
  (assoc version
         :status "published"
         :updated-at (System/currentTimeMillis)))
