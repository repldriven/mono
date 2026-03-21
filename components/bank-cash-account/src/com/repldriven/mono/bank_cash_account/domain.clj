(ns com.repldriven.mono.bank-cash-account.domain
  (:refer-clojure :exclude [name])
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
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
  (update-account-status :cash-account-status-closing account))

(defn- uk-scan-address
  [store]
  (let [sort-code "040004"
        account-number
        (format "%08d"
                (fdb/allocate-counter store "bank" "counters" sort-code))]
    {:scheme "uk.scan"
     :identifier {:scan {:sort-code sort-code
                         :account-number account-number}}}))

(defn- validate-currency
  "Validates currency against version's allowed-currencies.
  Returns nil on success, rejection anomaly on failure."
  [currency version]
  (let [allowed (:allowed-currencies version)]
    (when (and (seq allowed) (not (some #{currency} allowed)))
      (error/reject :cash-account/invalid-currency
                    "Currency not allowed for this product"))))

(defn- validate-party-status
  "Returns a rejection anomaly if the party is not active,
  nil otherwise."
  [party]
  (let [status (:status party)]
    (when (not= :party-status-active status)
      (let [s (subs (clojure.core/name status) (count "party-status-"))]
        (error/reject (keyword "cash-account"
                               (str "party-" s))
                      (str "Party is " s))))))

(defn new-account
  "Creates a new account map with status opened and payment
  addresses. Validates currency against version and party
  is active."
  [store data version party _existing-accounts]
  (let [{:keys [organization-id party-id product-id currency
                name]}
        data
        {:keys [version-id]} version]
    (let-nom>
      [_ (validate-currency currency version)
       _ (validate-party-status party)]
      (let [now (System/currentTimeMillis)]
        {:organization-id organization-id
         :party-id party-id
         :product-id product-id
         :version-id version-id
         :currency currency
         :name name
         :account-id (encryption/generate-id "acc")
         :account-status :cash-account-status-opened
         :payment-addresses [(uk-scan-address store)]
         :created-at now
         :updated-at now}))))

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

(def ^:private lifecycle-transitions
  {:cash-account-status-closing :cash-account-status-closed})

(defn transition-lifecyle
  "Returns account with next status, or nil if no
  transition applies."
  [_store account]
  (when-let [next-status (lifecycle-transitions (:account-status account))]
    (update-account-status next-status account)))
