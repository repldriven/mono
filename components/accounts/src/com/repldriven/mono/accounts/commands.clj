(ns com.repldriven.mono.accounts.commands
  (:refer-clojure :exclude [get load read update])
  (:require
    [com.repldriven.mono.accounts.domain :as domain]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn- payment-address-pb->avro
  "Flattens protojure oneof :identifier wrapper to flat
  Avro-compatible shape."
  [{:keys [scheme identifier]}]
  {:scheme scheme
   :scan (:scan identifier)
   :value (:value identifier)})

(defn- account-pb->avro
  "Converts protobuf Account to Avro-compatible map."
  [pb]
  (let [account (schema/pb->Account pb)]
    (clojure.core/update account
                         :payment-addresses
                         #(mapv payment-address-pb->avro %))))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "account")
                                (account-pb->avro result))})))

(defn- load
  "Loads a raw record by composite PK from the store.
  Returns the protobuf record or a rejection anomaly if
  not found."
  [store organization-id account-id]
  (or (fdb/load-record store organization-id account-id)
      (error/reject :account/not-found "Account not found")))

(defn- load-party-accounts
  "Returns the existing accounts for a party."
  [store party-id]
  (fdb/load-records store "Account" "party_id" party-id))

(defn- save
  "Saves account to store, writes changelog entry with
  serialized changelog proto, returns protobuf record or
  anomaly."
  [store account changelog]
  (error/let-nom>
    [_ (fdb/save-record store (schema/Account->java account))
     _ (fdb/write-changelog
        store
        "accounts"
        (:account-id account)
        (schema/AccountChangelog->pb
         (assoc changelog
                :organization-id
                (:organization-id account))))]
    (schema/Account->pb account)))

(defn- resolve-published-version
  "Returns the latest published version for the given
  product, or a rejection anomaly if none found."
  [store organization-id product-id]
  (let [result (fdb/scan-records
                store
                {:prefix [organization-id product-id]
                 :limit 1000})
        versions (->> (:records result)
                      (map schema/pb->AccountProductVersion)
                      (filter #(= "published" (:status %)))
                      (sort-by :version-number))]
    (or (last versions)
        (error/reject :account/product-not-published
                      "No published product version found"))))

(defn- validate-currency
  "Validates currency against version's allowed-currencies.
  Returns nil on success, rejection anomaly on failure."
  [currency version]
  (let [allowed (:allowed-currencies version)]
    (when (and (seq allowed)
               (not (some #{currency} allowed)))
      (error/reject :account/invalid-currency
                    "Currency not allowed for this product"))))

(defn- party-status->rejection
  "Returns a rejection anomaly for the given party status,
  or nil if the party is active."
  [status]
  (when (not= :active status)
    (let [s (name status)]
      (error/reject (keyword "account"
                             (str "party-" s))
                    (str "Party is " s)))))

(defn- open-account
  "Opens an account within a multi-store transaction.
  Resolves published product version, validates currency,
  validates the party is active, then creates the account
  with opened status and payment addresses."
  [config data]
  (let [{:keys [record-db record-store]} config
        {:keys [organization-id party-id product-id
                currency]}
        data]
    (fdb/transact-multi
     record-db
     record-store
     (fn [open-store]
       (error/let-nom>
         [version-store (open-store
                         "account-product-versions")
          version (resolve-published-version
                   version-store
                   organization-id
                   product-id)
          _ (validate-currency currency version)
          party-store (open-store "parties")
          party-rec (or (fdb/load-record party-store
                                         organization-id
                                         party-id)
                        (error/reject
                         :account/party-unknown
                         "Party not found"))
          party (schema/pb->Party party-rec)
          _ (party-status->rejection (:status party))
          acct-store (open-store "accounts")
          existing (->> (load-party-accounts
                         acct-store
                         party-id)
                        (map schema/pb->Account))
          account (domain/open-account
                   acct-store
                   (assoc data
                          :version-id
                          (:version-id version))
                   existing)]
         (save acct-store
               account
               {:account-id (:account-id account)
                :status-after
                (:account-status account)}))))))

(defn- read
  "Loads account by id. Returns protobuf record or anomaly."
  [config organization-id account-id]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  record-store
                  "accounts"
                  (fn [store]
                    (load store organization-id account-id)))))

(defn- update
  "Loads account by id, applies f, saves back. Returns
  protobuf record or anomaly."
  [config organization-id account-id f]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  record-store
                  "accounts"
                  (fn [store]
                    (error/let-nom>
                      [loaded (error/nom->>
                               (load store
                                     organization-id
                                     account-id)
                               schema/pb->Account)
                       updated (f loaded)]
                      (save store
                            updated
                            {:account-id account-id
                             :status-before
                             (:account-status loaded)
                             :status-after
                             (:account-status updated)}))))))

(defn open
  "Opens a new account."
  [config data]
  (->response config (open-account config data)))

(defn get
  "Returns the current account or rejection anomaly."
  [config data]
  (let [{:keys [organization-id account-id]} data]
    (->response config
                (read config organization-id account-id))))

(defn close
  "Closes an account."
  [config data]
  (let [{:keys [organization-id account-id]} data]
    (->response config
                (update config
                        organization-id
                        account-id
                        domain/close-account))))

