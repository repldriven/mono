(ns com.repldriven.mono.migrator.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.migrator.interface :as SUT]
    [com.repldriven.mono.db.interface :as sql]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [clojure.test :refer [deftest is testing]]))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (sql/get-datasource datasource)))

(deftest migrate-test
  (testing "Applying a migration changelog should result in a paved db"
    (with-test-system
     [sys "classpath:migrator/application-test.yml"]
     (let [datasource (system/instance sys [:db :datasource])
           db-spec (db-spec sys)]
       (SUT/migrate db-spec "migrator/test-changelog.sql")
       (is (= [{:name "hello"} {:name "world"}]
              (sql/execute! datasource
                            ["select name from test order by id asc"]
                            {:builder-fn sql/as-unqualified-lower-maps})))))))
