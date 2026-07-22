(ns com.repldriven.mono.processor.interface
  "Defines the `Processor` protocol — a single-method abstraction
  for anything that consumes a message and produces a result.
  Other bricks (e.g. command-processor) bind a concrete
  implementation into the system."
  (:require
    [com.repldriven.mono.processor.protocol :as protocol]))

(def ^{:doc "The `Processor` protocol with method `(process [this message])`."}
     Processor
  protocol/Processor)

(defn process
  "Dispatch `message` through `processor`'s `process` implementation.

  Args:
  - processor: a value satisfying the `Processor` protocol.
  - message: the message to process."
  [processor message]
  (protocol/process processor message))
