(ns com.repldriven.mono.system.reader.edn
  (:require
    [com.repldriven.mono.env.interface :as env]))

;; System edn-reader defmethod (extends aero/reader)
(defmethod env/edn-reader 'system
  [_ _ value]
  (keyword (name :donut.system) (name value)))
