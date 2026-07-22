(ns com.repldriven.mono.event-processor.interface
  "System-only brick: registers an `:event-processor/event-processor`
  donut.system component that subscribes a `processor/process` handler
  to an event channel on the bus and stops the subscription on shutdown."
  (:require
    com.repldriven.mono.event-processor.system))
