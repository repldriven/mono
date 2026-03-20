(ns com.repldriven.mono.bank-bootstrap.core
  (:require
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.bank-organization.interface
     :as organizations]))

(defn- find-organization
  "Returns the first organization matching name, or nil."
  [fdb-config name]
  (error/let-nom> [orgs (organizations/get-organizations fdb-config)]
    (->> orgs
         (filter #(= name (:name %)))
         first)))

(defn- ensure-initial-balance
  "Credits the default balance with initial-balance if it is
  still zero."
  [fdb-config account-id currency initial-balance]
  (if-not initial-balance
    nil
    (error/let-nom>
      [balance (balances/get-balance fdb-config
                                     account-id
                                     :balance-type-default
                                     currency
                                     :balance-status-posted)]
      (if (pos? (:credit balance))
        (do (log/info "Bootstrap initial balance"
                      "already credited:"
                      (:credit balance))
            nil)
        (error/let-nom>
          [_ (balances/save fdb-config
                            (assoc balance
                                   :credit initial-balance
                                   :updated-at
                                   (System/currentTimeMillis)))]
          (log/info "Credited bootstrap initial balance:"
                    initial-balance)
          nil)))))

(defn bootstrap
  "Idempotent bootstrap: ensures internal organization,
  account, and initial balance exist. Returns map of IDs
  or anomaly."
  [fdb-config seed]
  (log/info "Bootstrap starting")
  (let [{:keys [organization-name currencies initial-balance]}
        seed
        currency (first currencies)]
    (error/let-nom>
      [existing (find-organization fdb-config organization-name)
       {:keys [organization-id party-id product-id
               version-id account-id]}
       (if existing
         (let [account (first (:accounts existing))]
           (log/info "Bootstrap organization exists:"
                     (:organization-id existing))
           {:organization-id (:organization-id existing)
            :party-id (get-in existing [:party :party-id])
            :product-id (:product-id account)
            :version-id (:version-id account)
            :account-id (:account-id account)})
         (error/let-nom>
           [result (organizations/new-organization
                    fdb-config
                    organization-name
                    :organisation-type-internal
                    currencies)]
           (let [org (:organization result)
                 account (first (:accounts org))]
             (log/info "Created bootstrap organization:"
                       (:organization-id org))
             {:organization-id (:organization-id org)
              :party-id (get-in org [:party :party-id])
              :product-id (:product-id account)
              :version-id (:version-id account)
              :account-id (:account-id account)})))
       _ (ensure-initial-balance fdb-config
                                 account-id
                                 currency
                                 initial-balance)]
      (log/info "Bootstrap complete")
      {:organization-id organization-id
       :party-id party-id
       :product-id product-id
       :version-id version-id
       :account-id account-id})))
