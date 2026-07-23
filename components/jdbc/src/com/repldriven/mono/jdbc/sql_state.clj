(ns com.repldriven.mono.jdbc.sql-state
  "Surfacing and classifying database failures by their SQLSTATE.

  Deliberately not part of `core`: nothing here wraps next.jdbc. It
  reads the `java.sql.SQLException` behind an anomaly this brick
  produced, lifts its codes onto the payload, and answers questions
  about them.

  Lifting them matters more than the predicates do. SQLSTATE has
  hundreds of codes and no two vendors agree on the interesting ones,
  so rather than grow a taxonomy nobody asked for, every anomaly
  carries the raw code and an informed caller decides what it means.
  The two predicates below cover the cases that are unambiguous
  across drivers; everything else is left to the caller.

  SQLSTATE rather than exception classes, because a class test
  (`PSQLException`) would put a driver on the classpath and quietly
  make the brick single-vendor. Every driver's exception descends
  from `java.sql.SQLException`, and the codes are standardised: class
  `23` is integrity-constraint-violation, of which `23505` is a
  unique violation, across PostgreSQL, H2, HSQLDB and Derby alike."
  (:require
    [com.repldriven.mono.error.interface :as error]

    [clojure.string :as str])
  (:import
    (java.sql SQLException)))

(def unique-violation "23505")
(def integrity-constraint-violation-class "23")

(defn- exception
  [anomaly]
  (when (error/anomaly? anomaly)
    (let [ex (:exception (error/payload anomaly))]
      (when (instance? SQLException ex) ex))))

(defn enrich
  "Add `:sql-state` and `:vendor-code` to `anomaly` when a
  `java.sql.SQLException` caused it; return it untouched otherwise.

  `:vendor-code` earns its place where SQLSTATE is too coarse to act
  on: MySQL reports every integrity violation as 23000 and
  distinguishes them only by vendor code."
  [anomaly]
  (if-let [ex (exception anomaly)]
    (error/fail (error/kind anomaly)
                (assoc (error/payload anomaly)
                       :sql-state (.getSQLState ex)
                       :vendor-code (.getErrorCode ex)))
    anomaly))

(defn state
  "The SQLSTATE behind `anomaly`, or nil. Reads the payload key
  `enrich` left, falling back to the exception itself."
  [anomaly]
  (or (when (error/anomaly? anomaly) (:sql-state (error/payload anomaly)))
      (some-> (exception anomaly)
              .getSQLState)))

(defn unique-violation?
  [anomaly]
  (= unique-violation (state anomaly)))

(defn constraint-violation?
  [anomaly]
  (boolean (some-> (state anomaly)
                   (str/starts-with? integrity-constraint-violation-class))))
