(ns com.repldriven.mono.sse.response-test
  (:require
    [com.repldriven.mono.sse.response :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]])
  (:import
    (java.io ByteArrayOutputStream IOException OutputStream)))

(deftest frame-test
  (testing "an event names itself, its id and its data"
    (is (= "event: note\nid: 1\ndata: {}\n\n"
           (SUT/frame {:event "note" :id 1 :data "{}"}))))
  (testing "data over several lines is a data line each"
    (is (= "data: a\ndata: b\n\n" (SUT/frame {:data "a\nb"}))))
  (testing "a retry is passed on"
    (is (= "retry: 3000\ndata: x\n\n" (SUT/frame {:retry 3000 :data "x"}))))
  (testing "nil is a keep-alive comment"
    (is (= ": keep-alive\n\n" (SUT/frame nil)))))

(deftest write-events-test
  (testing "each event is framed as JSON under the event name"
    (let [out (ByteArrayOutputStream.)]
      (SUT/write-events (fn [emit] (emit nil) (emit {:id "e.1" :n 1}))
                        {:event "note"}
                        out)
      (is (= (str ": keep-alive\n\n"
                  "event: note\nid: e.1\ndata: {\"id\":\"e.1\",\"n\":1}\n\n")
             (.toString out "UTF-8")))))
  (testing "a write to a stream the client closed answers :sse/closed"
    (let [answered (promise)
          out (proxy [OutputStream] []
                (write
                  ;; nosemgrep: no-raw-throw -- a closed socket throws
                  ([_] (throw (IOException. "closed")))
                  ;; nosemgrep: no-raw-throw -- a closed socket throws
                  ([_ _ _] (throw (IOException. "closed")))))]
      (SUT/write-events (fn [emit] (deliver answered (emit {:id 1}))) {} out)
      (is (= :sse/closed (error/kind @answered)))))
  (testing "the response is an event stream"
    (let [response (SUT/response (fn [_] nil) {})]
      (is (= 200 (:status response)))
      (is (= "text/event-stream; charset=utf-8"
             (get-in response [:headers "content-type"]))))))
