(ns com.repldriven.mono.bank-idv.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-idv
  "Creates a new IDV map with status pending."
  [data]
  (let [{:keys [organization-id party-id]} data
        now (System/currentTimeMillis)]
    {:organization-id organization-id
     :party-id party-id
     :verification-id (encryption/generate-id "idv")
     :status :idv-status-pending
     :created-at now
     :updated-at now}))

(defn accept-idv
  "Returns IDV with status accepted."
  [idv]
  (assoc idv
         :status :idv-status-accepted
         :updated-at (System/currentTimeMillis)))
