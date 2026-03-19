(ns com.repldriven.mono.bank-organization.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-organization
  "Creates a new Organization record map."
  [org-name]
  (let [now (System/currentTimeMillis)]
    {:organization-id (encryption/generate-id "org")
     :name org-name
     :status "active"
     :created-at now
     :updated-at now}))
