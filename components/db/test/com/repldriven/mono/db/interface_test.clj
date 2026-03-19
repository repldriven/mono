(ns com.repldriven.mono.db.interface-test
  (:require
    com.repldriven.mono.db.interface
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [clojure.test :refer [deftest is testing]]))

(deftest db-component-test
  (testing "DB component should provide valid connections"
    (with-test-system
     [sys "classpath:db/application-test.yml"]
     (let [datasource (system/instance sys [:db :datasource])]
       (is (= [{:?column? 1}]
              (jdbc/execute! datasource
                             ["select 1"]
                             {:builder-fn rs/as-unqualified-lower-maps})))))))
