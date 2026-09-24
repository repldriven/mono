(ns com.repldriven.mono.realworld-store.comments
  (:require
    [com.repldriven.mono.realworld-store.sql :as sql]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.jdbc.interface :as jdbc]))

(def ^:private select-comment
  (str "select c.id, c.body, c.author_id,"
       "       "
       (sql/at "c.created_at")
       " as created_at,"
       "       " (sql/at "c.updated_at")
       " as updated_at," "       u.username as author_username,"
       "       u.bio as author_bio," "       u.image as author_image,"
       "       (fl.follower_id is not null) as author_following"
       "  from comments c"
       "  join users u on u.id = c.author_id" "  left join follows fl"
       "    on fl.followed_id = c.author_id"
       "   and fl.follower_id = ?::bigint"))

(defn- article-id
  [tx slug]
  (let-nom>
    [row (jdbc/execute-one! tx ["select id from articles where slug = ?" slug])]
    (if row
      (:id row)
      (error/reject :realworld/article-not-found "not found"))))

(defn for-article
  [ds slug viewer-id]
  (let-nom>
    [id (article-id ds slug)]
    (jdbc/execute! ds
                   [(str select-comment
                         " where c.article_id = ?"
                         " order by c.created_at asc, c.id asc")
                    viewer-id id])))

(defn create
  [ds slug author-id body]
  (jdbc/with-transaction
   [tx ds nil]
   (let-nom>
     [id (article-id tx slug)
      row (jdbc/execute-one!
           tx
           [(str "insert into comments (article_id, author_id, body)"
                 " values (?, ?, ?) returning id")
            id author-id body])
      comment (jdbc/execute-one! tx
                                 [(str select-comment " where c.id = ?")
                                  author-id (:id row)])]
     comment)))

(defn delete-comment
  "Delete a comment of `author-id`'s on `slug`.

  The article is checked first so that a comment id belonging to a different
  article reports the article as missing rather than the comment, and
  not-found precedes forbidden so ownership cannot be probed."
  [ds slug comment-id author-id]
  (jdbc/with-transaction
   [tx ds nil]
   (let-nom>
     [id (article-id tx slug)
      row (jdbc/execute-one! tx
                             [(str "select id, author_id from comments"
                                   " where id = ? and article_id = ?")
                              comment-id id])
      _ (cond (nil? row)
              (error/reject :realworld/comment-not-found "not found")

              (not= author-id (:author-id row))
              (error/reject :realworld/comment-forbidden "forbidden")

              :else
              (jdbc/execute-one! tx
                                 ["delete from comments where id = ?"
                                  (:id row)]))]
     nil)))
