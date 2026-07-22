(ns ^:eftest/synchronized com.repldriven.mono.example-bookmark.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.example-bookmark.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface
     :refer [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(deftest create-and-find-test
  (testing "Create a bookmark and find it by id"
    (with-test-system
     [sys "classpath:example-bookmark/application-test.yml"]
     (let [store (system/instance sys [:example-bookmark :store])]
       (nom-test> [bookmark (SUT/create store
                                        {:url "https://clojure.org"
                                         :title "Clojure"
                                         :tags ["lang" "jvm"]})
                   _ (is (some? (:bookmark-id bookmark)))
                   _ (is (= "https://clojure.org" (:url bookmark)))
                   found (SUT/find-by-id store (:bookmark-id bookmark))
                   _ (is (= (:bookmark-id bookmark) (:bookmark-id found)))
                   _ (is (= "Clojure" (:title found)))
                   _ (is (= ["lang" "jvm"] (:tags found)))])))))

(deftest find-by-tag-test
  (testing "Find bookmarks by tag"
    (with-test-system
     [sys "classpath:example-bookmark/application-test.yml"]
     (let [store (system/instance sys [:example-bookmark :store])]
       (nom-test> [_ (SUT/create store
                                 {:url "https://clojure.org"
                                  :title "Clojure"
                                  :tags ["lang" "jvm"]})
                   _ (SUT/create store
                                 {:url "https://www.scala-lang.org"
                                  :title "Scala"
                                  :tags ["lang" "jvm"]})
                   _ (SUT/create store
                                 {:url "https://rust-lang.org"
                                  :title "Rust"
                                  :tags ["lang" "systems"]})
                   jvm-results (SUT/find-by-tag store "jvm")
                   _ (is (= 2 (count jvm-results)))
                   _ (is (= #{"Clojure" "Scala"}
                            (set (map :title jvm-results))))
                   systems-results (SUT/find-by-tag store "systems")
                   _ (is (= 1 (count systems-results)))
                   _ (is (= "Rust" (:title (first systems-results))))])))))
