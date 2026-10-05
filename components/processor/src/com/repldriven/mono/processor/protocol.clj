(ns com.repldriven.mono.processor.protocol)

(defprotocol Processor
  (process [this message]))

(defprotocol Keyed
  (performer-key [this message]))
