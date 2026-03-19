(ns build
  (:require
    [com.repldriven.mono.build.proto :as proto]))

(defn gen-proto [opts] (proto/gen-proto opts))
