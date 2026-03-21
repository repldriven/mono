(ns com.repldriven.mono.bank-cash-account-product.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-version
  "Creates a new CashAccountProductVersion record map in
  draft status."
  [organization-id product-id version-number data]
  (let [{:keys [name account-type balance-sheet-side
                allowed-currencies balance-products
                valid-from valid-to]}
        data
        now (System/currentTimeMillis)]
    (cond-> {:organization-id organization-id
             :product-id product-id
             :version-id (encryption/generate-id "prv")
             :version-number version-number
             :status "draft"
             :name name
             :account-type account-type
             :balance-sheet-side balance-sheet-side
             :created-at now
             :updated-at now}

            (seq allowed-currencies)
            (assoc :allowed-currencies allowed-currencies)

            valid-from
            (assoc :valid-from valid-from)

            valid-to
            (assoc :valid-to valid-to)

            (seq balance-products)
            (assoc :balance-products balance-products))))

(defn publish
  "Sets version status to published."
  [version]
  (assoc version
         :status "published"
         :updated-at (System/currentTimeMillis)))
