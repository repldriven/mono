(ns com.repldriven.mono.server.streaming-test
  (:require
    [com.repldriven.mono.server.interface :as SUT]

    [clojure.test :refer [deftest is testing]]
    [ring.core.protocols :as protocols])
  (:import
    (java.io ByteArrayOutputStream)))

(deftest streaming-body-test
  (testing "write is handed the response's stream, which is closed after"
    (let [closed (atom false)
          out (proxy [ByteArrayOutputStream] [] (close [] (reset! closed true)))
          body (SUT/streaming-body (fn [out] (.write out (.getBytes "hi"))))]
      (protocols/write-body-to-stream body {} out)
      (is (= "hi" (.toString out "UTF-8")))
      (is @closed))))
