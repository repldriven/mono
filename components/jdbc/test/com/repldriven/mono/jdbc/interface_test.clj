(ns ^:eftest/synchronized com.repldriven.mono.jdbc.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface ;; extends
                                                 ;; `system/components`

    [com.repldriven.mono.jdbc.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(def ^:private create-table
  ["create table pet (
      id serial primary key,
      pet_name text not null,
      age_months int not null,
      created_at timestamptz not null default now())"])

(deftest jdbc-test
  (testing "next.jdbc, wrapped"
    (with-test-system
     [sys "classpath:jdbc/application-test.yml"]
     (let [ds (system/instance sys [:jdbc :datasource])]
       (testing "a statement with no result set reports a plain update count"
         (nom-test> [created (SUT/execute-one! ds create-table)
                     _ (is (= {:jdbc/update-count 0} created))
                     _ (is (not (contains? created :next.jdbc/update-count)))]))
       (testing "snake_case columns arrive as unqualified kebab-case keys"
         (nom-test> [row
                     (SUT/insert! ds :pet {:pet-name "Whiskers" :age-months 24})
                     _ (is (= "Whiskers" (:pet-name row)))
                     _ (is (= 24 (:age-months row)))
                     _ (is (some? (:created-at row)))
                     _ (is (= #{:id :pet-name :age-months :created-at}
                              (set (keys row))))]))
       (testing "the friendly SQL functions round-trip"
         (nom-test> [_ (SUT/insert-multi! ds
                                          :pet
                                          [{:pet-name "Rex" :age-months 12}
                                           {:pet-name "Kit" :age-months 6}])
                     found (SUT/find-by-keys ds :pet {:pet-name "Rex"})
                     _ (is (= 1 (count found)))
                     _ (is (= 12 (:age-months (first found))))
                     by-id (SUT/get-by-id ds :pet (:id (first found)))
                     _ (is (= "Rex" (:pet-name by-id)))
                     updated
                     (SUT/update! ds :pet {:age-months 13} {:pet-name "Rex"})
                     _ (is (= 1 (SUT/update-count updated)))
                     deleted (SUT/delete! ds :pet {:pet-name "Kit"})
                     _ (is (= 1 (:jdbc/update-count deleted)))
                     remaining (SUT/query ds ["select count(*) as n from pet"])
                     _ (is (= 2 (:n (first remaining))))]))
       (testing "a transaction commits, and its body sees itself in one"
         (nom-test> [in-tx
                     (SUT/with-transaction
                      [tx ds nil]
                      (SUT/insert! tx :pet {:pet-name "Ana" :age-months 3})
                      (SUT/active-tx? tx))
                     _ (is (true? in-tx))
                     found (SUT/find-by-keys ds :pet {:pet-name "Ana"})
                     _ (is (= 1 (count found)))]))
       (testing "a throwing transaction body rolls back, as an anomaly"
         (let [result (SUT/with-transaction
                       [tx ds nil]
                       (SUT/insert! tx :pet {:pet-name "Gone" :age-months 1})
                       ;; nosemgrep: no-raw-throw
                       (throw (ex-info "rollback" {})))]
           (is (error/anomaly? result))
           (nom-test> [found (SUT/find-by-keys ds :pet {:pet-name "Gone"})
                       _ (is (empty? found))])))
       (testing "a unique violation is recognised by SQLSTATE, not by driver"
         (nom-test>
           [_
            (SUT/execute-one!
             ds
             ["alter table pet add constraint pet_name_unique
                          unique (pet_name)"])])
         (let [dup (SUT/insert! ds :pet {:pet-name "Rex" :age-months 1})]
           (is (SUT/unique-violation? dup))
           (is (SUT/constraint-violation? dup))
           (is (= "23505" (SUT/sql-state dup)))
           (is (= "23505" (:sql-state (error/payload dup))))
           (is (int? (:vendor-code (error/payload dup))))))
       (testing "a failing statement is an anomaly, not a throw"
         (let [result (SUT/execute! ds ["select * from no_such_table"])]
           (is (error/anomaly? result))
           (is (= :jdbc/execute (error/kind result)))
           (is (not (SUT/unique-violation? result)))))
       (testing "a connection body reuses the connectable"
         (nom-test> [n (SUT/on-connection [conn ds]
                                          (SUT/execute-one!
                                           conn
                                           ["select count(*) as n from pet"]))
                     _ (is (= 3 (:n n)))]))
       (testing "plan is reducible and streams rows"
         (is (= 3
                (transduce (map (constantly 1))
                           +
                           (SUT/plan ds ["select id from pet"])))))))))
