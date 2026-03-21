(ns com.repldriven.mono.bank-idv.watcher
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn party-changelog-handler
  "Returns a watcher handler that initiates IDV when a party
  is created with pending status. Watches the parties store."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->PartyChangelog changelog-bytes)]
      (when (= :party-status-pending (:status-after changelog))
        (let [store (record-store ctx "idvs")
              org-id (:organization-id changelog)
              idv (domain/new-idv {:organization-id org-id
                                   :party-id (:party-id changelog)})]
          (fdb/save-record store (schema/Idv->java idv))
          (fdb/write-changelog store
                               "idvs"
                               (:verification-id idv)
                               (schema/IdvChangelog->pb
                                {:organization-id org-id
                                 :verification-id (:verification-id idv)
                                 :status-after
                                 :idv-status-pending})))))))

(defn idv-changelog-handler
  "Returns a watcher handler that transitions a pending IDV
  to accepted. Captures record-store to open the idvs store
  within the same transaction."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)]
      (when (= :idv-status-pending (:status-after changelog))
        (let [store (record-store ctx "idvs")
              org-id (:organization-id changelog)
              record
              (fdb/load-record store org-id (:verification-id changelog))]
          (when record
            (let [idv (schema/pb->Idv record)
                  accepted (domain/accept-idv idv)]
              (fdb/save-record store (schema/Idv->java accepted))
              (fdb/write-changelog store
                                   "idvs"
                                   (:verification-id accepted)
                                   (schema/IdvChangelog->pb
                                    {:organization-id org-id
                                     :verification-id (:verification-id
                                                       accepted)
                                     :status-before :idv-status-pending
                                     :status-after
                                     :idv-status-accepted})))))))))
