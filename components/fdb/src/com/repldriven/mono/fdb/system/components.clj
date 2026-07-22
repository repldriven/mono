(ns com.repldriven.mono.fdb.system.components
  (:refer-clojure :exclude [name])
  (:require
    [clojure.string :as str]
    [com.repldriven.mono.fdb.keyspace :as keyspace]
    [com.repldriven.mono.fdb.watcher :as watcher]
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (com.apple.foundationdb FDB)
    (com.apple.foundationdb.record RecordMetaData)
    (com.apple.foundationdb.record.metadata Index
                                            IndexOptions
                                            IndexTypes
                                            Key$Expressions
                                            MetaDataException)
    (com.apple.foundationdb.record.metadata.expressions GroupingKeyExpression
                                                        KeyExpression$FanType)
    (com.apple.foundationdb.record.provider.foundationdb APIVersion
                                                         FDBDatabaseFactory
                                                         FDBMetaDataStore
                                                         FDBRecordStore)
    (java.io File)
    (java.util.concurrent Executors TimeUnit)
    (java.util.function Function)))

;; ---
;; cluster-file-path
;; ---

;; Canonical: always a string. Production YAML supplies
;; `path: !env FDB_CLUSTER_FILE`; tests ref the
;; `fdb/container-cluster-file-path` adapter below into `:path`.
(def cluster-file-path
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [path (:path config)]
                         (log/info "FDB cluster file path:" path)
                         path)))
   :system/config {:path system/required-component}
   :system/config-schema [:map [:path string?]]
   :system/instance-schema string?})

;; Testcontainer adapter: derive the cluster file by writing
;; `fdb:fdb@<host>:<port>` into a temp file the FDB Java client
;; can open. Returns the absolute path so it can be wired into
;; `cluster-file-path.path` from a test YAML.
(def container-cluster-file-path
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [container (:container config)
                             host (.getHost container)
                             port (.getFirstMappedPort container)
                             contents (str "fdb:fdb@" host ":" port)
                             tmp (File/createTempFile "fdb" ".cluster")]
                         (.deleteOnExit tmp)
                         (spit tmp contents)
                         (let [path (.getAbsolutePath tmp)]
                           (log/info "FDB cluster file path:" path)
                           path))))
   :system/config {:container system/required-component}
   :system/config-schema [:map [:container some?]]
   :system/instance-schema string?})

;; ---
;; db
;; ---

(def db
  {:system/start (fn [{:system/keys [config instance]}]
                   (let [{:keys [cluster-file-path api-version]} config
                         api-version (or api-version 710)]
                     (log/info "FDB database start called, instance:" instance
                               "config:" config)
                     (or instance
                         (try-nom :fdb/create-db
                                  {:message "Failed to create FDB database"
                                   :cluster-file-path cluster-file-path}
                                  (let [fdb (FDB/selectAPIVersion api-version)
                                        db (.open fdb cluster-file-path)]
                                    (log/info
                                     "Opened FDB database with cluster file:"
                                     cluster-file-path)
                                    db)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component
                   :api-version 710}
   :system/config-schema [:map
                          [:cluster-file-path string?]]
   :system/instance-schema some?})

;; ---
;; record-db
;; ---

(def record-db
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (try-nom
          :fdb/create-record-db
          {:message "Failed to create FDB Record Layer database"}
          (let [{:keys [cluster-file-path async-to-sync-timeout-ms]} config
                ;; Record Layer's default `getWithDeadline` is 5s,
                ;; which is too tight for a single-process dev FDB
                ;; under concurrent first-access from many services
                ;; (each `FDBMetaDataStore.<init>` performs a
                ;; directory-layer resolution that serialises on
                ;; the cluster). Bump to 30s by default.
                timeout-ms (or async-to-sync-timeout-ms 30000)
                db (.getDatabase
                    (doto (FDBDatabaseFactory/instance)
                      (.setAPIVersion APIVersion/API_VERSION_7_1)
                      (.setScheduledExecutor
                       (Executors/newSingleThreadScheduledExecutor)))
                    cluster-file-path)]
            (log/info
             "Opening FDB Record Layer database with async->sync timeout (ms):"
             timeout-ms)
            (.setAsyncToSyncTimeout db timeout-ms TimeUnit/MILLISECONDS)
            db))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB Record Layer database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component}
   :system/config-schema [:map
                          [:cluster-file-path string?]
                          [:async-to-sync-timeout-ms {:optional true}
                           [:maybe pos-int?]]]
   :system/instance-schema some?})

;; ---
;; store
;; ---

(defn- set-primary-key
  [b record-type primary-key]
  (when primary-key
    (let [expr (if (= 1 (count primary-key))
                 (Key$Expressions/field (first primary-key))
                 (Key$Expressions/concatenateFields ^java.util.List
                                                    primary-key))]
      (.setPrimaryKey (.getRecordType b record-type) expr))))

(defn- set-primary-keys
  [b record-types]
  (doseq [{:strs [record-type primary-key]} (vals record-types)]
    (set-primary-key b record-type primary-key)))

(defn- key-expression
  [{:strs [field fields fan-out nest]}]
  (cond
   fields
   (Key$Expressions/concatenateFields ^java.util.List fields)

   nest
   (.nest (Key$Expressions/field field
                                 (if fan-out
                                   KeyExpression$FanType/FanOut
                                   KeyExpression$FanType/None))
          (key-expression nest))

   :else
   (Key$Expressions/field field
                          (if fan-out
                            KeyExpression$FanType/FanOut
                            KeyExpression$FanType/None))))

(def ^:private index-type->str {"count" IndexTypes/COUNT "sum" IndexTypes/SUM})

(defn- add-indexes
  [builder record-type indexes]
  (doseq [{:strs [name unique type] :as idx-cfg} indexes]
    (let [expr (key-expression idx-cfg)
          idx-type (get index-type->str type "value")
          ;; COUNT groups by every field (0 grouped columns, count entries
          ;; per group). SUM groups by all but the last field and sums that
          ;; trailing value column (1 grouped column).
          grouped-expr (condp = idx-type
                         IndexTypes/COUNT (GroupingKeyExpression. expr 0)
                         IndexTypes/SUM (GroupingKeyExpression. expr 1)
                         expr)
          opts (if unique
                 IndexOptions/UNIQUE_OPTIONS
                 IndexOptions/EMPTY_OPTIONS)]
      (.addIndex builder
                 record-type
                 (Index. name
                         grouped-expr
                         idx-type
                         opts)))))

(defn- resolve-descriptor
  [class-name]
  (let [clazz (Class/forName class-name)
        method (.getMethod clazz "getDescriptor" (into-array Class []))]
    (.invoke method nil (into-array Object []))))

(defn- build-meta-data
  [descriptor record-types]
  (let [file-desc (resolve-descriptor descriptor)
        builder (-> (RecordMetaData/newBuilder)
                    (.setRecords file-desc))]
    (set-primary-keys builder record-types)
    (doseq [[_store-name {:strs [record-type indexes]}] record-types]
      (add-indexes builder record-type indexes))
    (.build builder)))

(def store
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [descriptor record-types]} config
                             meta (build-meta-data descriptor record-types)
                             store-names (set (keys record-types))]
                         (fn [ctx store-name]
                           (when-not (store-names store-name)
                             ;; nosemgrep: no-raw-throw
                             (throw (ex-info "Unknown record store"
                                             {:store store-name})))
                           (-> (FDBRecordStore/newBuilder)
                               (.setMetaDataProvider meta)
                               (.setContext ctx)
                               (.setKeySpacePath (keyspace/path store-name))
                               .createOrOpen)))))
   :system/config {:descriptor system/required-component
                   :record-types system/required-component}
   :system/config-schema [:map
                          [:descriptor string?]
                          [:record-types map?]]
   :system/instance-schema fn?})

;; ---
;; meta-store
;; ---

(defn- open-meta-store
  [ctx ks-path file-desc store-name]
  (let [ms (doto (FDBMetaDataStore. ctx ks-path)
             (.setLocalFileDescriptor file-desc))]
    (-> (FDBRecordStore/newBuilder)
        (.setMetaDataStore ms)
        (.setContext ctx)
        (.setKeySpacePath (keyspace/path store-name))
        .createOrOpen)))

(defn- truthy-flag?
  "Coerce a `migrate`-style flag to boolean. Accepts the literal
  Clojure boolean (when set inline in test YAML) or the string
  shape `!env FDB_MIGRATE` produces (\"true\" / \"1\" / \"yes\"
  case-insensitive). Anything else — including nil from an unset
  env var — is treated as false."
  [v]
  (cond (boolean? v)
        v
        (string? v)
        (contains? #{"true" "1" "yes"}
                   (some-> v
                           str/lower-case))
        :else
        false))

;; Read-only by default. When `migrate: true` (or the env-resolved
;; string equivalent), persists the record meta-data into FDB at
;; start before returning the open-fn — exactly the previous
;; `meta-store-write` behaviour, just gated on a single flag.
;; Read-mode is lazy (no directory-cache warming at start) so the
;; same component-kind can be declared in a system that also
;; persists schema in the same JVM (monolith, tests) without
;; ordering races: callers pay path-resolution cost on first
;; store access. The schema-save is idempotent — FDB Record
;; Layer's "meta-data version must increase" rejection is treated
;; as a no-op so every helm upgrade Job can restart cleanly. Real
;; schema upgrades must explicitly bump the meta-data version on
;; the builder.
(def meta-store
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or
      instance
      (let [{:keys [record-db path descriptor record-types migrate]} config
            ks-path (keyspace/path path)
            file-desc (resolve-descriptor descriptor)]
        (when (truthy-flag? migrate)
          (log/info "FDB meta-store migrating metadata to:" path)
          (try
            (.run record-db
                  ^Function
                  (fn [ctx]
                    (let [ms (FDBMetaDataStore. ctx ks-path)
                          meta-data (build-meta-data descriptor record-types)]
                      (.saveRecordMetaData ms meta-data))
                    nil))
            (catch Exception e
              (let [root (loop [t e]
                           (if-let [c (.getCause t)]
                             (recur c)
                             t))]
                (if (and (instance? MetaDataException root)
                         (re-find #"meta-data version must increase"
                                  (or (.getMessage ^Throwable root) "")))
                  (log/info
                   "FDB meta-data already persisted at >= current version; skipping save")
                  ;; nosemgrep: no-raw-throw
                  (throw e))))))
        (fn [ctx store-name]
          (open-meta-store ctx ks-path file-desc store-name)))))
   :system/config {:record-db system/required-component
                   :path system/required-component
                   :descriptor system/required-component}
   :system/config-schema [:map
                          [:record-db some?]
                          [:path string?]
                          [:descriptor string?]
                          [:record-types {:optional true} [:maybe map?]]
                          [:migrate {:optional true}
                           [:maybe [:or boolean? string?]]]]
   :system/instance-schema fn?})

;; ---
;; watcher
;; ---

(def watcher-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (watcher/start config)))
   :system/stop
   (fn [{:system/keys [instance]}] (when instance ((:stop instance))) nil)
   :system/config {:record-db system/required-component
                   :consumer-id system/required-component
                   :store-name system/required-component
                   :handler system/required-component}
   :system/instance-schema some?})

;; ---
;; watchers
;; ---

(def watchers-component
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance (into {} (map (fn [[k v]] [k (watcher/start v)]) config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Stopping watcher:"
                                            (clojure.core/name k))
                                  ((:stop v)))
                                instance))))
   :system/config system/required-component
   :system/instance-schema map?})
