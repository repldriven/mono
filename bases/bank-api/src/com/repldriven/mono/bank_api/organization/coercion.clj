(ns com.repldriven.mono.bank-api.organization.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private organisation-type-enum
  (coercion/enum-coercion {"internal" :organisation-type-internal
                           "customer" :organisation-type-customer}
                          :organisation-type-unknown))

(def organisation-type-enum-schema (:enum-schema organisation-type-enum))
