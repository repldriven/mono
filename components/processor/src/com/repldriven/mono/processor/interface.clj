(ns com.repldriven.mono.processor.interface
  (:require
    [com.repldriven.mono.processor.protocol :as protocol]))

(def Processor protocol/Processor)

(defn process [processor message] (protocol/process processor message))
