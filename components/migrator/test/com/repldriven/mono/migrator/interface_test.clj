(ns com.repldriven.mono.migrator.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.migrator.interface :as SUT]
    [com.repldriven.mono.jdbc.interface :as jdbc]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [clojure.test :refer [deftest is testing]]))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:jdbc :datasource])]
    (jdbc/get-datasource datasource)))

(deftest migrate-test
  (testing "Applying a migration changelog should result in a paved db"
    (with-test-system [sys "classpath:migrator/application-test.yml"]
                      (let [datasource (system/instance sys [:jdbc :datasource])
                            db-spec (db-spec sys)]
                        (SUT/migrate db-spec "migrator/test-changelog.sql")
                        (is (= [{:name "hello"} {:name "world"}]
                               (jdbc/execute!
                                datasource
                                ["select name from test order by id asc"])))))))
