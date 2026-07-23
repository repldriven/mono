(ns com.repldriven.mono.jdbc.sql
  "Every public function of `next.jdbc.sql` — the friendly SQL
  functions — wrapped so each returns an anomaly rather than
  throwing."
  (:require
    [com.repldriven.mono.jdbc.core :refer
     [opts+ plain default-opts
      sql-nom]]

    [next.jdbc.sql :as sql]))

(defn insert!
  ([connectable table key-map]
   (sql-nom :jdbc/insert
            "Failed to insert"
            (plain (sql/insert! connectable table key-map default-opts))))
  ([connectable table key-map opts]
   (sql-nom :jdbc/insert
            "Failed to insert"
            (plain (sql/insert! connectable table key-map (opts+ opts))))))

(defn insert-multi!
  ([connectable table hash-maps]
   (sql-nom :jdbc/insert-multi
            "Failed to insert"
            (plain (sql/insert-multi! connectable
                                      table
                                      hash-maps
                                      default-opts))))
  ([connectable table hash-maps opts]
   (sql-nom :jdbc/insert-multi
            "Failed to insert"
            (plain (sql/insert-multi! connectable
                                      table
                                      hash-maps
                                      (opts+ opts)))))
  ([connectable table cols rows opts]
   (sql-nom :jdbc/insert-multi
            "Failed to insert"
            (plain (sql/insert-multi! connectable
                                      table
                                      cols
                                      rows
                                      (opts+ opts))))))

(defn query
  ([connectable sql-params]
   (sql-nom :jdbc/query
            "Failed to query"
            (plain (sql/query connectable sql-params default-opts))))
  ([connectable sql-params opts]
   (sql-nom :jdbc/query
            "Failed to query"
            (plain (sql/query connectable sql-params (opts+ opts))))))

(defn find-by-keys
  ([connectable table key-map]
   (sql-nom :jdbc/find-by-keys
            "Failed to query"
            (plain (sql/find-by-keys connectable table key-map default-opts))))
  ([connectable table key-map opts]
   (sql-nom :jdbc/find-by-keys
            "Failed to query"
            (plain (sql/find-by-keys connectable
                                     table
                                     key-map
                                     (opts+
                                      opts))))))

(defn get-by-id
  ([connectable table pk]
   (sql-nom :jdbc/get-by-id
            "Failed to query"
            (plain (sql/get-by-id connectable table pk default-opts))))
  ([connectable table pk opts]
   (sql-nom :jdbc/get-by-id
            "Failed to query"
            (plain (sql/get-by-id connectable table pk (opts+ opts)))))
  ([connectable table pk pk-name opts]
   (sql-nom :jdbc/get-by-id
            "Failed to query"
            (plain (sql/get-by-id connectable
                                  table
                                  pk
                                  pk-name
                                  (opts+
                                   opts))))))

(defn update!
  ([connectable table key-map where-params]
   (sql-nom :jdbc/update
            "Failed to update"
            (plain (sql/update! connectable
                                table
                                key-map
                                where-params
                                default-opts))))
  ([connectable table key-map where-params opts]
   (sql-nom :jdbc/update
            "Failed to update"
            (plain (sql/update! connectable
                                table
                                key-map
                                where-params
                                (opts+ opts))))))

(defn delete!
  ([connectable table where-params]
   (sql-nom :jdbc/delete
            "Failed to delete"
            (plain (sql/delete! connectable table where-params default-opts))))
  ([connectable table where-params opts]
   (sql-nom :jdbc/delete
            "Failed to delete"
            (plain (sql/delete! connectable
                                table
                                where-params
                                (opts+
                                 opts))))))

(defn aggregate-by-keys
  ([connectable table aggregate key-map]
   (sql-nom :jdbc/aggregate-by-keys
            "Failed to query"
            (plain (sql/aggregate-by-keys connectable
                                          table
                                          aggregate
                                          key-map
                                          default-opts))))
  ([connectable table aggregate key-map opts]
   (sql-nom :jdbc/aggregate-by-keys
            "Failed to query"
            (plain (sql/aggregate-by-keys connectable
                                          table
                                          aggregate
                                          key-map
                                          (opts+ opts))))))
