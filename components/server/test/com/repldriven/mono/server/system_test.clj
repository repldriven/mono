(ns com.repldriven.mono.server.system-test
  (:require
    [com.repldriven.mono.http-client.interface :as http-client]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [reitit.http :as http]
    [reitit.ring :as ring]
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(deftest server-test
  (testing "Server component system configuration and lifecycle"
    (let [handler (fn [_] {:status 200 :body "ok"})]
      (with-test-system [_
                         ["classpath:server/application-test.yml"
                          #(assoc-in %
                            [:system/defs :server :handler]
                            (constantly handler))]]))))

(deftest interceptors-test
  (testing "Ring interceptors MUST be inserted"
    (let [data {:got "me" :this "time"}
          handler (fn [req] {:status 200 :body (select-keys req (keys data))})
          routes (fn [ctx] ["/api" {:interceptors (:interceptors ctx)}
                            ["/interceptors" {:get {:handler handler}}]])
          app (fn [ctx]
                (http/ring-handler (http/router (routes ctx)
                                                server/standard-router-data)
                                   (ring/create-default-handler)
                                   server/standard-executor))]
      (with-test-system [sys
                         ["classpath:server/application-test.yml"
                          #(assoc-in % [:system/defs :server :handler] app)]]
                        (let [jetty (system/instance sys
                                                     [:server :jetty-adapter])
                              base-url (server/http-local-url jetty)
                              url (str base-url "/api/interceptors")
                              res (http-client/request {:url url :method :get})
                              body (http-client/res->body res)]
                          (is (= body {"got" "me" "this" "time"})))))))

(deftest coercion-error-test
  (testing "Coercion errors return structured responses"
    (let [routes (fn [_ctx]
                   ["/api"
                    ["/validate"
                     {:post {:parameters {:body [:map [:name string?]]}
                             :responses {200 {:body [:map [:greeting string?]]}}
                             :handler
                             (fn [_] {:status 200 :body {:greeting "hello"}})}}]
                    ["/bad-response"
                     {:post {:parameters {:body [:map [:name string?]]}
                             :responses {200 {:body [:map [:greeting string?]]}}
                             :handler (fn [_]
                                        {:status 200 :body {:wrong "key"}})}}]])
          app (fn [ctx]
                (http/ring-handler (http/router (routes ctx)
                                                server/standard-router-data)
                                   (ring/create-default-handler)
                                   server/standard-executor))]
      (with-test-system
       [sys
        ["classpath:server/application-test.yml"
         #(assoc-in % [:system/defs :server :handler] app)]]
       (let [jetty (system/instance sys [:server :jetty-adapter])
             base-url (server/http-local-url jetty)
             post! (fn [path body]
                     (http-client/request {:method :post
                                           :url (str base-url path)
                                           :headers {"Content-Type"
                                                     "application/json"}
                                           :body (json/write-str body)}))]
         (testing "Valid request returns 200"
           (let [res (post! "/api/validate" {"name" "Alice"})]
             (is (= 200 (:status res)))))
         (testing "Invalid request body returns 400 with error type"
           (let [res (post! "/api/validate" {})
                 body (http-client/res->body res)]
             (is (= 400 (:status res)))
             (is (= "REJECTED" (get body "title")))
             (is (= "mono/bad-request" (get body "type")))
             (is (contains? body "detail"))))
         (testing "Invalid response body returns 500 with error type"
           (let [res (post! "/api/bad-response" {"name" "Alice"})
                 body (http-client/res->body res)]
             (is (= 500 (:status res)))
             (is (= "FAILED" (get body "title")))
             (is (= "mono/bad-response" (get body "type")))
             (is (contains? body "detail")))))))))
