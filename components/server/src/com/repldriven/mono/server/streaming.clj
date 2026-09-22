(ns com.repldriven.mono.server.streaming
  (:require
    [ring.core.protocols :as protocols])
  (:import
    (java.io IOException OutputStream)))

(defn body
  [write]
  (reify
   protocols/StreamableResponseBody
     (write-body-to-stream [_ _ out]
       (let [^OutputStream out out]
         (try (write out)
              (finally (try (.close out) (catch IOException _ nil))))))))
