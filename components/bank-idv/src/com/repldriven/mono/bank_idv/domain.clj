(ns com.repldriven.mono.bank-idv.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-idv
  "Creates a new IDV map with status pending."
  [data]
  (let [now (System/currentTimeMillis)]
    (assoc data
           :verification-id (encryption/generate-id "idv")
           :status :idv-status-pending
           :created-at now
           :updated-at now)))

(defn accept-idv
  "Returns IDV with status accepted."
  [idv]
  (assoc idv
         :status :idv-status-accepted
         :updated-at (System/currentTimeMillis)))
