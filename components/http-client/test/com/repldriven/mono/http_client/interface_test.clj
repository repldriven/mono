(ns com.repldriven.mono.http-client.interface-test
  (:require
    [com.repldriven.mono.error.interface :as err]
    [com.repldriven.mono.http-client.interface :as SUT]
    [org.httpkit.fake :refer [with-fake-http]]
    [clojure.test :as test :refer [deftest is testing]]))

(deftest res->body-string-body-no-content-type-test
  (testing "String body without content-type returns the string"
    (is (= "{\"a\":1,\"b\":2}" (SUT/res->body {:body "{\"a\":1,\"b\":2}"})))))

(deftest res->body-string-body-json-content-type-test
  (testing "String body with JSON content-type returns parsed JSON"
    (is (= {"a" 1 "b" 2}
           (SUT/res->body {:headers {:content-type "application/json"}
                           :body "{\"a\":1,\"b\":2}"})))))

(deftest res->body-byte-array-body-json-content-type-test
  (testing "Byte array body with JSON content-type returns parsed JSON"
    (is (= {"a" 1 "b" 2}
           (SUT/res->body {:headers {:content-type "application/json"}
                           :body (.getBytes "{\"a\":1,\"b\":2}")})))))

(deftest res->body-string-body-html-content-type-test
  (testing "String body with HTML content-type returns the string"
    (is (= "<html>...</html>"
           (SUT/res->body {:headers {:content-type "text/html"}
                           :body "<html>...</html>"})))))

(deftest res->body-nil-response-test
  (testing "Nil response returns nil" (is (nil? (SUT/res->body nil)))))

(deftest res->body-anomaly-passthrough-test
  (testing "Anomaly response is passed through unchanged"
    (let [anomaly (err/fail :test/error "Test error")]
      (is (= anomaly (SUT/res->body anomaly))))))

(deftest with-fake-http-test
  (testing "with-fake-http works with both sync and async requests"
    (with-fake-http
     [{:url "http://example.com/success" :method :get}
      {:status 200
       :headers {:content-type "application/json"}
       :body "{\"result\":\"success\"}"}
      {:url "http://example.com/exception" :method :get}
      (fn [_orig-fn _opts _callback]
        (throw (ex-info "Simulated error" {:test true})))
      {:url "http://example.com/request-failed" :method :get}
      {:error "Connection timeout"}]
     (testing "successful synchronous request"
       (let [res (SUT/request {:url "http://example.com/success" :method :get})]
         (is (= 200 (:status res)))
         (is (= {"result" "success"} (SUT/res->body res)))))
     (testing "synchronous request with exception returns anomaly"
       (let [res (SUT/request {:url "http://example.com/exception"
                               :method :get})]
         (is (err/anomaly? res))
         (is (= :http-client/request (err/kind res)))))
     (testing "synchronous request with http-kit error returns anomaly"
       (let [res (SUT/request {:url "http://example.com/request-failed"
                               :method :get})]
         (is (err/anomaly? res))
         (is (= :http-client/request (err/kind res)))))
     (testing "asynchronous request"
       (let [p (SUT/request-async {:url "http://example.com/success"
                                   :method :get})
             res @p]
         (is (= 200 (:status res)))
         (is (= {"result" "success"} (SUT/res->body res))))))))
