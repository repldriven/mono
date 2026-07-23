(ns ^:eftest/synchronized com.repldriven.mono.example-api.api-test
  (:refer-clojure :exclude [get])
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.example-bookmark.interface

    [com.repldriven.mono.example-api.api :as SUT]

    [com.repldriven.mono.http-client.interface :as http-client]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface
     :refer [with-test-system]]

    [com.repldriven.mono.json.interface :as json]
    [clojure.test :refer [deftest is testing]]))

(defn- post
  [base-url path body]
  (http-client/request {:method :post
                        :url (str base-url path)
                        :headers {"Content-Type"
                                  "application/json"}
                        :body (json/write-str body)}))

(defn- get
  [base-url path]
  (http-client/request {:url (str base-url path)
                        :method :get
                        :headers {"Accept" "application/json"}}))

(deftest api-test
  (testing "Bookmark API endpoints"
    (with-test-system
     [sys
      ["classpath:example-api/application-test.yml"
       #(assoc-in % [:system/defs :server :handler] SUT/app)]]
     (let [jetty (system/instance sys [:server :jetty-adapter])
           base-url (server/http-local-url jetty)]
       (testing "POST /api/bookmarks creates a bookmark"
         (let [res (post base-url
                         "/api/bookmarks"
                         {"url" "https://clojure.org"
                          "title" "Clojure"
                          "tags" ["lang" "jvm"]})
               body (http-client/res->body res)]
           (is (= 201 (:status res)))
           (is (some? (get body "bookmark-id")))
           (is (= "https://clojure.org" (clojure.core/get body "url")))
           (testing "GET /api/bookmarks/:id finds it"
             (let [id (clojure.core/get body "bookmark-id")
                   res (get base-url (str "/api/bookmarks/" id))
                   found (http-client/res->body res)]
               (is (= 200 (:status res)))
               (is (= "Clojure" (clojure.core/get found "title")))))
           (testing "GET /api/bookmarks?tag= filters"
             (let [_ (post base-url
                           "/api/bookmarks"
                           {"url" "https://rust-lang.org"
                            "title" "Rust"
                            "tags" ["lang" "systems"]})
                   res (get base-url "/api/bookmarks?tag=jvm")
                   results (http-client/res->body res)]
               (is (= 200 (:status res)))
               (is (= 1 (count results)))
               (is (= "Clojure" (clojure.core/get (first results) "title")))))))
       (testing "POST /api/bookmarks with invalid body"
         (let [res (post base-url "/api/bookmarks" {})]
           (is (= 400 (:status res)))))))))
