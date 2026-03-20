(ns com.repldriven.mono.bank-organization.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-organization
  "Creates a new Organization record map."
  [org-name org-type]
  (let [now (System/currentTimeMillis)]
    {:organization-id (encryption/generate-id "org")
     :name org-name
     :type org-type
     :status "active"
     :created-at now
     :updated-at now}))
