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
  (let [{:keys [record-db record-store]} config
        {:keys [url title tags]} data
        bookmark-id (str (utility/uuidv7))]
    (fdb/transact
     record-db
     record-store
     "bookmarks"
     (fn [store]
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
          :tags (vec tags)})))))

(defn find-by-id
  [config bookmark-id]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact
     record-db
     record-store
     "bookmarks"
     (fn [store]
       (some-> (fdb/load-record store bookmark-id)
               bookmarks/pb->Bookmark)))))

(defn find-by-tag
  [config tag]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact
     record-db
     record-store
     "bookmarks"
     (fn [store]
       (mapv bookmarks/pb->Bookmark
             (fdb/query-repeated-records
              store
              "Bookmark"
              "tags"
              tag))))))
