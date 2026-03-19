(ns com.repldriven.mono.bank-cash-account-product.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn new-product
  "Creates a cash account product as an initial draft v1.
  Returns {:version <map>} or anomaly."
  [{:keys [record-db record-store]} org-id version-data]
  (let [product-id (encryption/generate-id "prd")
        version (domain/new-version org-id product-id 1 version-data)]
    (error/let-nom> [_
                     (fdb/transact record-db
                                   record-store
                                   "cash-account-product-versions"
                                   (fn [store]
                                     (fdb/save-record
                                      store
                                      (schema/CashAccountProductVersion->java
                                       version))))]
      {:version version})))

(defn new-version
  "Creates a new draft version for a product. Computes
  next version-number from existing versions. Returns
  {:version <map>} or anomaly."
  [{:keys [record-db record-store]} org-id product-id version-data]
  (fdb/transact
   record-db
   record-store
   "cash-account-product-versions"
   (fn [store]
     (let [existing (fdb/scan-records store
                                      {:prefix [org-id product-id]
                                       :limit 1000})
           records (:records existing)
           latest (->> records
                       (map schema/pb->CashAccountProductVersion)
                       (sort-by :version-number)
                       last)]
       (if (and latest (= "draft" (:status latest)))
         (error/reject
          :bank-cash-account-product/draft-exists
          {:message
           "Cannot create a new version while the latest version is still a draft"})
         (let [next-num (inc (count records))
               version
               (domain/new-version org-id product-id next-num version-data)]
           (fdb/save-record store
                            (schema/CashAccountProductVersion->java version))
           {:version version}))))))

(defn get-version
  "Loads a version by org-id, product-id, and version-id.
  Returns the version map, nil if not found, or anomaly."
  [{:keys [record-db record-store]} org-id product-id version-id]
  (error/let-nom> [result
                   (fdb/transact
                    record-db
                    record-store
                    "cash-account-product-versions"
                    (fn [store]
                      (fdb/load-record store org-id product-id version-id)))]
    (when result (schema/pb->CashAccountProductVersion result))))

(defn get-versions
  "Lists versions. With product-id, scans that product;
  without, scans all products for the org. Returns
  {:versions [<map> ...]} or anomaly."
  ([{:keys [record-db record-store]} org-id]
   (error/let-nom>
     [result
      (fdb/transact
       record-db
       record-store
       "cash-account-product-versions"
       (fn [store] (fdb/scan-records store {:prefix [org-id] :limit 1000})))]
     {:versions (mapv schema/pb->CashAccountProductVersion (:records result))}))
  ([{:keys [record-db record-store]} org-id product-id]
   (error/let-nom>
     [result
      (fdb/transact
       record-db
       record-store
       "cash-account-product-versions"
       (fn [store]
         (fdb/scan-records store {:prefix [org-id product-id] :limit 100})))]
     {:versions (mapv schema/pb->CashAccountProductVersion
                      (:records result))})))

(defn get-published
  "Returns the highest-version-number published version for
  a product, or nil if none published. Returns anomaly on
  error."
  [{:keys [record-db record-store]} org-id product-id]
  (error/let-nom>
    [result
     (fdb/transact
      record-db
      record-store
      "cash-account-product-versions"
      (fn [store]
        (fdb/scan-records store {:prefix [org-id product-id] :limit 1000})))]
    (->> (:records result)
         (map schema/pb->CashAccountProductVersion)
         (filter #(= "published" (:status %)))
         (sort-by :version-number)
         last)))

(defn publish
  "Publishes a draft version. Returns the published
  version map or anomaly."
  [{:keys [record-db record-store]} org-id product-id version-id]
  (fdb/transact
   record-db
   record-store
   "cash-account-product-versions"
   (fn [store]
     (let [bytes (fdb/load-record store org-id product-id version-id)]
       (cond (nil? bytes)
             (error/reject
              :bank-cash-account-product/version-not-found
              {:message "Version not found"})
             :else
             (let [version (schema/pb->CashAccountProductVersion bytes)]
               (if-not (= "draft" (:status version))
                 (error/reject
                  :bank-cash-account-product/not-draft
                  {:message "Only draft versions can be published"})
                 (let [published (domain/publish version)]
                   (fdb/save-record
                    store
                    (schema/CashAccountProductVersion->java published))
                   published))))))))
