(ns com.repldriven.mono.cash-accounts.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn update-account-status
  "Returns account with updated status and timestamp."
  [status account]
  (assoc account
         :account-status status
         :updated-at (System/currentTimeMillis)))

(defn close-account
  "Returns account with status closing."
  [account]
  (update-account-status :closing account))

(defn- uk-scan-address
  [store]
  (let [sort-code "040004"
        account-number (format "%08d"
                               (fdb/allocate-counter store
                                                     "bank"
                                                     "counters"
                                                     sort-code))]
    {:scheme "uk.scan"
     :identifier {:scan {:sort-code sort-code
                         :account-number account-number}}}))

(defn add-payment-addresses
  [store account]
  (assoc account :payment-addresses [(uk-scan-address store)]))

(defn open-account
  "Creates a new account map with status opened and payment
  addresses. existing-accounts is the list of accounts the
  party already has (unused for now)."
  [store data _existing-accounts]
  (let [now (System/currentTimeMillis)]
    (add-payment-addresses
     store
     (assoc data
            :account-id (encryption/generate-id "acc")
            :account-status :opened
            :created-at now
            :updated-at now))))

(defn balances
  "Returns balances for each balance-product."
  [account-id currency balance-products]
  (let [now (System/currentTimeMillis)]
    (mapv (fn [{:keys [balance-type balance-status]}]
            {:account-id account-id
             :balance-type balance-type
             :balance-status balance-status
             :currency currency
             :credit 0
             :debit 0
             :created-at now
             :updated-at now})
          balance-products)))

(def ^:private lifecycle-transitions {:closing :closed})

(defn transition-lifecyle
  "Returns account with next status, or nil if no
  transition applies."
  [_store account]
  (when-let [next-status (lifecycle-transitions
                          (:account-status account))]
    (update-account-status next-status account)))
