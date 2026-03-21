(ns com.repldriven.mono.bank-bootstrap.core
  (:require
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface :as organizations]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]))

(defn- find-organization
  "Returns the first organization matching name, or nil."
  [config name]
  (let-nom> [orgs (organizations/get-organizations config)]
    (->> orgs
         (filter #(= name (:name %)))
         first)))

(defn- update-balance
  "Updates a balance to match the seed definition.
  Rejects if the balance does not exist."
  [config account-id balance]
  (let [{:keys [balance-type balance-status currency]}
        balance]
    (let-nom>
      [_ (balances/update-balance
          config
          (assoc balance :account-id account-id))]
      (log/info "Updated bootstrap balance:"
                balance-type
                currency
                balance-status))))

(defn- update-balances
  "Updates balances that already exist. Returns nil or
  the first anomaly."
  [config account-id balances]
  (reduce (fn [_ balance]
            (let [result (update-balance config
                                         account-id
                                         balance)]
              (if (error/anomaly? result)
                (reduced result)
                result)))
          nil
          balances))

(def ^:private result-keys
  [:organization-id
   :party-id
   :product-id
   :version-id
   :account-id])

(defn- org->result
  "Extracts bootstrap result keys from a rich
  organization map."
  [org]
  (let [account (first (:accounts org))]
    {:organization-id (:organization-id org)
     :party-id (get-in org [:party :party-id])
     :product-id (:product-id account)
     :version-id (:version-id account)
     :account-id (:account-id account)}))

(defn bootstrap
  "Idempotent bootstrap: ensures internal organization,
  account, and balances exist. Returns map of IDs or
  anomaly."
  [config seed]
  (log/info "Bootstrap starting")
  (let [{:keys [organization-name currencies balances]} seed]
    (let-nom>
      [existing (find-organization config organization-name)
       result
       (if existing
         (do (log/info "Bootstrap organization exists:"
                       (:organization-id existing))
             (org->result existing))
         (let-nom>
           [created (organizations/new-organization
                     config
                     organization-name
                     :organisation-type-internal
                     currencies)
            result (org->result (:organization created))
            _ (update-balances config
                               (:account-id result)
                               balances)]
           (log/info "Created bootstrap organization:"
                     (:organization-id result))
           result))]
      (log/info "Bootstrap complete")
      (select-keys result result-keys))))
