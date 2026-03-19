(ns com.repldriven.mono.cli.interface
  (:require
    [com.repldriven.mono.cli.core :as core]))

(defn validate-args [program-name args] (core/validate-args program-name args))

(defn exit [ok? msg] (core/exit ok? msg))

