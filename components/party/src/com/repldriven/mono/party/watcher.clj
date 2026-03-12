(ns com.repldriven.mono.party.watcher
  (:require
    [com.repldriven.mono.party.domain :as domain]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn idv-changelog-handler
  "Returns a watcher handler that transitions parties from
  pending to active when their IDV is accepted. Captures
  record-store to open stores within the same transaction."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)]
      (when (= :accepted (:status-after changelog))
        (let [idv-store (record-store ctx "idvs")
              org-id (:organization-id changelog)
              idv-record (fdb/load-record
                          idv-store
                          org-id
                          (:verification-id changelog))]
          (when idv-record
            (let [idv (schema/pb->Idv idv-record)
                  party-store (record-store ctx "parties")
                  party-record (fdb/load-record
                                party-store
                                org-id
                                (:party-id idv))]
              (when party-record
                (let [party (schema/pb->Party party-record)]
                  (when (= :pending (:status party))
                    (let [activated (domain/activate-party party)]
                      (fdb/save-record
                       party-store
                       (schema/Party->java activated))
                      (fdb/write-changelog
                       party-store
                       "parties"
                       (:party-id activated)
                       (schema/PartyChangelog->pb
                        {:organization-id org-id
                         :party-id (:party-id activated)
                         :status-before :pending
                         :status-after :active})))))))))))))
