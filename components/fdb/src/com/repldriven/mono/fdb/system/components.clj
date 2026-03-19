(ns com.repldriven.mono.fdb.system.components
  (:require
    [com.repldriven.mono.fdb.keyspace :as keyspace]
    [com.repldriven.mono.fdb.watcher :as watcher]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (com.apple.foundationdb FDB)
    (com.apple.foundationdb.record RecordMetaData)
    (com.apple.foundationdb.record.metadata Index IndexOptions Key$Expressions)
    (com.apple.foundationdb.record.provider.foundationdb APIVersion
                                                         FDBDatabaseFactory
                                                         FDBMetaDataStore
                                                         FDBRecordStore)
    (java.io File)
    (java.util.concurrent Executors)
    (java.util.function Function)))

;; ---
;; cluster-file-path
;; ---

(def cluster-file-path
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
                         (error/try-nom
                          :fdb/create-db
                          {:message "Failed to create FDB database"
                           :cluster-file-path cluster-file-path}
                          (let [fdb (FDB/selectAPIVersion api-version)
                                db (.open fdb cluster-file-path)]
                            (log/info "Opened FDB database with cluster file:"
                                      cluster-file-path)
                            db)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component
                   :api-version 710}
   :system/config-schema [:map [:cluster-file-path string?]]
   :system/instance-schema some?})

;; ---
;; record-db
;; ---

(def record-db
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (error/try-nom
                        :fdb/create-record-db
                        {:message "Failed to create FDB Record Layer database"}
                        (let [{:keys [cluster-file-path]} config]
                          (log/info "Opening FDB Record Layer database")
                          (.getDatabase
                           (doto (FDBDatabaseFactory/instance)
                             (.setAPIVersion APIVersion/API_VERSION_7_1)
                             (.setScheduledExecutor
                              (Executors/newSingleThreadScheduledExecutor)))
                           cluster-file-path)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB Record Layer database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component}
   :system/config-schema [:map [:cluster-file-path string?]]
   :system/instance-schema some?})

;; ---
;; store
;; ---

(defn- resolve-descriptor
  [class-name]
  (let [clazz (Class/forName class-name)
        method (.getMethod clazz "getDescriptor" (into-array Class []))]
    (.invoke method nil (into-array Object []))))

(defn- build-index-expr
  [{:strs [field fields]}]
  (if fields
    (Key$Expressions/concatenateFields ^java.util.List fields)
    (Key$Expressions/field field)))

(defn- set-primary-key
  [b record-type primary-key]
  (when primary-key
    (.setPrimaryKey (.getRecordType b record-type)
                    (Key$Expressions/concatenateFields ^java.util.List
                                                       primary-key))))

(defn- add-indexes
  [b record-type indexes]
  (doseq [{:strs [name unique] :as idx-cfg} indexes]
    (let [expr (build-index-expr idx-cfg)
          opts
          (if unique IndexOptions/UNIQUE_OPTIONS IndexOptions/EMPTY_OPTIONS)]
      (.addIndex b record-type (Index. name expr "value" opts)))))

(defn- apply-all-primary-keys
  [b record-types]
  (doseq [{:strs [record-type primary-key]} (vals record-types)]
    (set-primary-key b record-type primary-key)))

(defn- build-meta-data
  [descriptor record-types]
  (let [file-desc (resolve-descriptor descriptor)
        b (-> (RecordMetaData/newBuilder)
              (.setRecords file-desc))]
    (apply-all-primary-keys b record-types)
    (doseq [[_store-name {:strs [record-type indexes]}] record-types]
      (add-indexes b record-type indexes))
    (.build b)))

(def store
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [descriptor record-types]} config
                             meta (build-meta-data descriptor record-types)
                             store-names (set (keys record-types))]
                         (fn [ctx store-name]
                           (when-not (store-names store-name)
                             (throw (ex-info "Unknown record store"
                                             {:store store-name})))
                           (-> (FDBRecordStore/newBuilder)
                               (.setMetaDataProvider meta)
                               (.setContext ctx)
                               (.setKeySpacePath (keyspace/path store-name))
                               .createOrOpen)))))
   :system/config {:descriptor system/required-component
                   :record-types system/required-component}
   :system/config-schema [:map [:descriptor string?] [:record-types map?]]
   :system/instance-schema fn?})

;; ---
;; meta-store
;; ---

(def meta-store
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [record-db path descriptor record-types]} config
               ks-path (keyspace/path path)
               file-desc (resolve-descriptor descriptor)
               meta-data (build-meta-data descriptor record-types)]
           (log/info "FDB meta-store saving metadata to:" path)
           (.run record-db
                 ^Function
                 (fn [ctx]
                   (let [ms (FDBMetaDataStore. ctx ks-path)]
                     (.saveRecordMetaData ms meta-data))
                   nil))
           (fn [ctx store-name]
             (let [ms (doto (FDBMetaDataStore. ctx ks-path)
                        (.setLocalFileDescriptor file-desc))]
               (-> (FDBRecordStore/newBuilder)
                   (.setMetaDataStore ms)
                   (.setContext ctx)
                   (.setKeySpacePath (keyspace/path store-name))
                   .createOrOpen))))))
   :system/config {:record-db system/required-component
                   :path system/required-component
                   :descriptor system/required-component
                   :record-types system/required-component}
   :system/config-schema [:map [:record-db some?] [:path string?]
                          [:descriptor string?] [:record-types map?]]
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
