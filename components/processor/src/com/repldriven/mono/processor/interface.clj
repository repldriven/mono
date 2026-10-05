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

(def
  ^{:doc
    "The `Keyed` protocol with method `(performer-key [this message])`:
  a processor that also satisfies it names a narrower key than the one a
  message was sent under, for the subscription to choose its performer
  by. It must return a key every message sharing it was sent under one
  key with, or nil to use the send key."}
  Keyed
  protocol/Keyed)

(defn performer-key-fn
  "A fn of a message returning `processor`'s performer key for it, or nil
  when `processor` does not satisfy `Keyed`.

  Args:
  - processor: a value satisfying the `Processor` protocol."
  [processor]
  (when (satisfies? protocol/Keyed processor)
    (fn [message] (protocol/performer-key processor message))))
