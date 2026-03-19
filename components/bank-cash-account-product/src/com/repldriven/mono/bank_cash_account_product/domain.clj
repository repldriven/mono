(ns com.repldriven.mono.bank-cash-account-product.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

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
                                [:name :account-type :balance-sheet-side
                                 :allowed-currencies :valid-from
                                 :valid-to]))
            (seq (:balance-products data))
            (assoc :balance-products
                   (:balance-products data)))))

(defn publish
  "Sets version status to published."
  [version]
  (assoc version
         :status "published"
         :updated-at (System/currentTimeMillis)))
