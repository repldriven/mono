(ns com.repldriven.mono.bank-api.currency.components
  (:require
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def Currency [:enum {:json-schema/example "EUR"} "EUR" "GBP" "USD"])

(def registry (components-registry [#'Currency]))
