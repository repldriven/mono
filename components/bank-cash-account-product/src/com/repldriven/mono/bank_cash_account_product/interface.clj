(ns com.repldriven.mono.bank-cash-account-product.interface
  (:require
    [com.repldriven.mono.bank-cash-account-product.core :as core]))

(defn new-product [config org-id data] (core/new-product config org-id data))

(defn new-version
  [config org-id product-id data]
  (core/new-version config org-id product-id data))

(defn get-version
  [config org-id product-id version-id]
  (core/get-version config org-id product-id version-id))

(defn get-versions
  ([config org-id] (core/get-versions config org-id))
  ([config org-id product-id] (core/get-versions config org-id product-id)))

(defn get-published
  [config org-id product-id]
  (core/get-published config org-id product-id))

(defn publish
  [config org-id product-id version-id]
  (core/publish config org-id product-id version-id))
