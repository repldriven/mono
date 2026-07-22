(ns com.repldriven.mono.example-bookmark.store
  (:require
    [com.repldriven.mono.example_schemas.bookmarks :as bookmarks]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.utility.interface :as utility]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.example_schemas.bookmarks
     BookmarkProto$Bookmark)))

(defn- Bookmark->java
  [m]
  (BookmarkProto$Bookmark/parseFrom
   (proto/->pb (bookmarks/new-Bookmark m))))

(defn create
  [config data]
  (let [{:keys [url title tags]} data
        bookmark-id (str (utility/uuidv7))]
    (fdb/transact
     config
     (fn [txn]
       (let [store (fdb/open txn "bookmarks")]
         (let-nom>
           [_ (fdb/save-record
               store
               (Bookmark->java {:bookmark-id bookmark-id
                                :url url
                                :title title
                                :tags (vec tags)}))]
           {:bookmark-id bookmark-id
            :url url
            :title title
            :tags (vec tags)}))))))

(defn find-by-id
  [config bookmark-id]
  (fdb/transact
   config
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn "bookmarks") bookmark-id)
             bookmarks/pb->Bookmark))))

;; fdb queries an indexed field for equality; `tags` is a repeated field,
;; which the planner cannot match that way. The demo scans by primary key
;; and filters in memory instead, paging so the whole store is never held
;; at once.
(def ^:private scan-page-size 100)

(defn find-by-tag
  [config tag]
  (fdb/transact
   config
   (fn [txn]
     (let [store (fdb/open txn "bookmarks")]
       (loop [cursor nil
              found []]
         (let [opts (cond-> {:limit scan-page-size}
                            cursor
                            (assoc :after cursor))
               page (fdb/scan-records store opts)
               {:keys [records after]} page
               tagged (into found
                            (comp (map bookmarks/pb->Bookmark)
                                  (filter #(some #{tag} (:tags %))))
                            records)]
           (if after
             (recur after tagged)
             tagged)))))))
