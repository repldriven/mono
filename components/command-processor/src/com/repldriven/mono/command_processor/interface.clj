(ns com.repldriven.mono.command-processor.interface
  "Registers a `:command-processor` component-kind that wires a
  `Processor` implementation to a command channel on the bus.
  Loading this namespace installs the component-kind defmethod;
  there are no callable fns."
  (:require
    com.repldriven.mono.command-processor.system))
