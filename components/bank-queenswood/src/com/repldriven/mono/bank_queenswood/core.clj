(ns com.repldriven.mono.bank-queenswood.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.bank-party.interface :as party]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn- find-organization
  "Returns the first organization matching name, or nil."
  [fdb-config name]
  (error/let-nom> [orgs (organizations/get-organizations fdb-config)]
    (->> orgs
         (filter #(= name (:name %)))
         first)))

(defn- ensure-organization
  "Returns existing or newly created organization id."
  [fdb-config org-name]
  (error/let-nom> [existing (find-organization fdb-config org-name)]
    (if existing
      (do (log/info "Queenswood organization exists:"
                    (:organization-id existing))
          (:organization-id existing))
      (error/let-nom>
        [result (organizations/new-organization
                 fdb-config
                 org-name)]
        (log/info "Created Queenswood organization:"
                  (:organization-id (:organization result)))
        (:organization-id (:organization result))))))

(defn- find-party
  "Scans parties store for an internal party with this org-id.
  Returns the party map or nil."
  [record-db record-store org-id]
  (fdb/transact
   record-db
   record-store
   "parties"
   (fn [store]
     (->> (fdb/scan-records store {:prefix [org-id] :limit 100})
          :records
          (map schema/pb->Party)
          (filter #(= :party-type-internal (:type %)))
          first))))

(defn- ensure-party
  "Returns existing or newly created internal party id."
  [fdb-config org-id display-name]
  (error/let-nom> [existing (find-party (:record-db fdb-config)
                                        (:record-store fdb-config)
                                        org-id)]
    (if existing
      (do (log/info "Queenswood party exists:"
                    (:party-id existing))
          (:party-id existing))
      (error/let-nom>
        [created (party/create
                  fdb-config
                  {:organization-id org-id
                   :type :party-type-internal
                   :display-name display-name})]
        (log/info "Created Queenswood party:"
                  (:party-id created))
        (:party-id created)))))

(defn- ->keyword
  "Coerces a string or keyword to a keyword."
  [v]
  (if (keyword? v) v (keyword v)))

(defn- keywordize-balance-product
  [bp]
  (-> bp
      (update :balance-type ->keyword)
      (update :balance-status ->keyword)))

(defn- ensure-product
  "Returns {:product-id ... :version-id ...} for existing or
  newly created product."
  [fdb-config org-id seed]
  (let [{:keys [product-name account-type balance-sheet-side
                currency balance-products]}
        seed]
    (error/let-nom>
      [result (products/get-versions fdb-config org-id)]
      (let [existing (->> (:versions result)
                          (filter #(= product-name (:name %)))
                          first)]
        (if existing
          (do (log/info "Queenswood product exists:"
                        (:product-id existing))
              {:product-id (:product-id existing)
               :version-id (:version-id existing)})
          (error/let-nom>
            [created (products/new-product
                      fdb-config
                      org-id
                      {:name product-name
                       :account-type (->keyword account-type)
                       :balance-sheet-side (->keyword
                                            balance-sheet-side)
                       :allowed-currencies [currency]
                       :balance-products
                       (mapv keywordize-balance-product
                             balance-products)})
             published (products/publish
                        fdb-config
                        org-id
                        (get-in created [:version :product-id])
                        (get-in created [:version :version-id]))]
            (log/info "Created Queenswood product:"
                      (:product-id published))
            {:product-id (:product-id published)
             :version-id (:version-id published)}))))))

(defn- find-account
  "Finds an account for this party. Returns the account map
  or nil."
  [record-db record-store party-id]
  (fdb/transact record-db
                record-store
                "cash-accounts"
                (fn [store]
                  (->> (fdb/load-records store
                                         "CashAccount"
                                         "party_id"
                                         party-id)
                       (map schema/pb->CashAccount)
                       first))))

(defn- ensure-account
  "Returns existing or newly created account id."
  [fdb-config org-id party-id product-id currency]
  (error/let-nom>
    [existing (find-account (:record-db fdb-config)
                            (:record-store fdb-config)
                            party-id)]
    (if existing
      (do (log/info "Queenswood account exists:"
                    (:account-id existing))
          (:account-id existing))
      (error/let-nom>
        [account (cash-accounts/open
                  fdb-config
                  {:organization-id org-id
                   :party-id party-id
                   :product-id product-id
                   :currency currency})]
        (log/info "Created Queenswood account:"
                  (:account-id account))
        (:account-id account)))))

(defn bootstrap
  "Idempotent bootstrap: ensures Queenswood organization,
  internal party, cash-account product, and account exist.
  Returns map of IDs or anomaly."
  [fdb-config seed]
  (log/info "Queenswood bootstrap starting")
  (error/let-nom>
    [org-id (ensure-organization fdb-config
                                 (:organization-name seed))
     party-id (ensure-party fdb-config
                            org-id
                            (:party-display-name seed))
     {:keys [product-id version-id]} (ensure-product fdb-config
                                                     org-id
                                                     seed)
     account-id (ensure-account fdb-config
                                org-id
                                party-id
                                product-id
                                (:currency seed))]
    (log/info "Queenswood bootstrap complete")
    {:organization-id org-id
     :party-id party-id
     :product-id product-id
     :version-id version-id
     :account-id account-id}))
