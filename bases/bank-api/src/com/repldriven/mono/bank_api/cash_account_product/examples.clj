(ns com.repldriven.mono.bank-api.cash-account-product.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def VersionNotFound
  {:value {:title "REJECTED"
           :type "cash-account-products/version-not-found"
           :status 404
           :detail "Version not found"}})

(def NoPublishedVersion
  {:value {:title "REJECTED"
           :type "cash-account-products/no-published-version"
           :status 404
           :detail "No published version found"}})

(def NotDraft
  {:value {:title "REJECTED"
           :type "cash-account-products/not-draft"
           :status 409
           :detail "Only draft versions can be published"}})

(def registry
  (examples-registry [#'VersionNotFound #'NoPublishedVersion #'NotDraft]))

(def CashAccountProductVersion
  {:organization-id "org_01JMABC"
   :product-id "prd_01JMABC123"
   :version-id "prv_01JMABC456"
   :version-number 1
   :status "draft"
   :name "Current Account"
   :account-type :current
   :balance-sheet-side :liability
   :allowed-currencies ["GBP" "EUR"]
   :balance-products [{:balance-type :default :balance-status :posted}]
   :valid-from "2025-01-01"
   :valid-to "2025-12-31"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def CashAccountProductVersionList {:versions [CashAccountProductVersion]})

(def DraftCashAccountProductRequest
  {:name "Current Account"
   :account-type :current
   :balance-sheet-side :liability
   :allowed-currencies ["GBP" "EUR"]
   :balance-products [{:balance-type :default :balance-status :posted}]
   :valid-from "2025-01-01"
   :valid-to "2025-12-31"})

(def DraftCashAccountProductVersionRequest
  {:name "Current Account"
   :account-type :current
   :balance-sheet-side :liability
   :allowed-currencies ["GBP" "EUR"]
   :balance-products [{:balance-type :default :balance-status :posted}]
   :valid-from "2025-01-01"
   :valid-to "2025-12-31"})
