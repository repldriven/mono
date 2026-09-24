(ns com.repldriven.mono.realworld-store.articles
  (:require
    [com.repldriven.mono.realworld-store.sql :as sql]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.jdbc.interface :as jdbc]))

;; One select for every article read.
;;
;; Everything an article body needs is here in a single statement: the
;; author, the tags, whether this caller favorited it, how many others did,
;; and whether this caller follows the author. Fetching those separately
;; would be an N+1 per listing, and splitting them across bricks would put
;; the joins somewhere they could not be written at all.
;;
;; `viewer-id` appears twice and may be nil. A null never matches, so an
;; anonymous caller gets `favorited` and `following` false without a
;; separate query.
(def ^:private select-article
  (str "select a.id, a.slug, a.title, a.description, a.body,"
       "       "
       (sql/at "a.created_at")
       " as created_at,"
       "       "
       (sql/at "a.updated_at")
       " as updated_at,"
       "       a.author_id," "       u.username as author_username,"
       "       u.bio as author_bio," "       u.image as author_image,"
       "       (fl.follower_id is not null) as author_following,"
       "       coalesce(t.names, '{}') as tag_list,"
       "       (fv.user_id is not null) as favorited,"
       "       coalesce(fc.n, 0) as favorites_count"))

(def ^:private from-article
  (str "  from articles a" "  join users u on u.id = a.author_id"
       "  left join lateral ("
       "         select array_agg(tg.name order by tg.name) as names"
       "           from article_tags ats"
       "           join tags tg on tg.id = ats.tag_id"
       "          where ats.article_id = a.id) t on true"
       "  left join lateral ("
       "         select count(*) as n from favorites f"
       "          where f.article_id = a.id) fc on true"
       "  left join follows fl" "    on fl.followed_id = a.author_id"
       "   and fl.follower_id = ?::bigint" "  left join favorites fv"
       "    on fv.article_id = a.id" "   and fv.user_id = ?::bigint"))

(defn- ->row
  [row]
  (when row (update row :tag-list sql/->vec)))

(defn by-slug
  [ds slug viewer-id]
  (let-nom>
    [row (jdbc/execute-one! ds
                            [(str select-article
                                  from-article
                                  " where a.slug = ?")
                             viewer-id viewer-id slug])]
    (->row row)))

(defn- by-id
  [ds id viewer-id]
  (let-nom>
    [row (jdbc/execute-one! ds
                            [(str select-article from-article " where a.id = ?")
                             viewer-id viewer-id id])]
    (->row row)))

;; `count(*) over ()` rides along on the same statement, so articlesCount is
;; the total number of matches rather than the size of this page — which is
;; what the API means by it — without a second round trip.
(def ^:private total "       , count(*) over () as total")

(def ^:private order-and-page
  " order by a.created_at desc, a.id desc limit ? offset ?")

(defn- page
  [rows]
  {:rows (mapv ->row rows)
   :total (or (:total (first rows)) 0)})

(defn find-articles
  "Filtered, paged listing. Every filter is optional and expressed in one
  statement, so there is a single prepared plan rather than a built string."
  [ds params viewer-id]
  (let [{:keys [tag author favorited limit offset]} params]
    (let-nom>
      [rows (jdbc/execute!
             ds
             [(str select-article
                   total
                   from-article
                   " where (?::text is null or exists ("
                   "          select 1 from article_tags xt"
                   "            join tags xg on xg.id = xt.tag_id"
                   "           where xt.article_id = a.id"
                   "             and xg.name = ?::text))"
                   "   and (?::text is null or u.username = ?::text)"
                   "   and (?::text is null or exists ("
                   "          select 1 from favorites xf"
                   "            join users xu on xu.id = xf.user_id"
                   "           where xf.article_id = a.id"
                   "             and xu.username = ?::text))"
                   order-and-page)
              viewer-id viewer-id tag tag author author favorited favorited
              (or limit 20) (or offset 0)])]
      (page rows))))

(defn feed
  "Articles by the people `viewer-id` follows."
  [ds viewer-id limit offset]
  (let-nom>
    [rows (jdbc/execute! ds
                         [(str select-article
                               total
                               from-article
                               " where a.author_id in ("
                               "         select followed_id from follows"
                               "          where follower_id = ?)"
                               order-and-page)
                          viewer-id viewer-id viewer-id (or limit 20)
                          (or offset 0)])]
    (page rows)))

(defn- set-tags!
  "Replace an article's tags with `names`.

  Tag rows are shared, so they are upserted and never deleted — an unused
  tag is harmless and `GET /api/tags` only reports tags actually in use."
  [tx article-id names]
  (let-nom>
    [_ (jdbc/execute-one! tx
                          ["delete from article_tags where article_id = ?"
                           article-id])
     _ (if (seq names)
         (jdbc/execute-one! tx
                            [(str "insert into tags (name) select unnest(?)"
                                  " on conflict (name) do nothing")
                             (into-array String names)])
         nil)
     _ (if (seq names)
         (jdbc/execute-one! tx
                            [(str "insert into article_tags (article_id,"
                                  " tag_id) select ?, id from tags"
                                  " where name = any(?)")
                             article-id (into-array String names)])
         nil)]
    nil))

(defn create
  [ds data]
  (let [{:keys [author-id slug title description body tagList]} data]
    (jdbc/with-transaction
     [tx ds nil]
     (let-nom>
       [row (jdbc/execute-one!
             tx
             [(str "insert into articles"
                   " (slug, title, description, body, author_id)"
                   " values (?, ?, ?, ?, ?) returning id")
              slug title description body author-id])
        _ (set-tags! tx (:id row) tagList)
        article (by-id tx (:id row) author-id)]
       article))))

(defn- owned
  "The article, or a rejection: not-found before forbidden, so a caller
  cannot use the difference to discover which slugs exist."
  [tx slug author-id]
  (let-nom>
    [row (jdbc/execute-one! tx
                            ["select id, author_id from articles where slug = ?"
                             slug])]
    (cond (nil? row)
          (error/reject :realworld/article-not-found "not found")

          (not= author-id (:author-id row))
          (error/reject :realworld/article-forbidden "forbidden")

          :else
          row)))

(defn update-article
  "Patch the fields present in `data`. `tagList` absent leaves tags alone;
  present replaces them, including with an empty list."
  [ds slug author-id data]
  (let [{:keys [title description body tagList]} data
        set-tags (contains? data :tagList)]
    (jdbc/with-transaction
     [tx ds nil]
     (let-nom>
       [row (owned tx slug author-id)
        _ (jdbc/execute-one!
           tx
           [(str "update articles set"
                 "   title = coalesce(?::text, title),"
                 "   description = coalesce(?::text, description),"
                 "   body = coalesce(?::text, body),"
                 "   updated_at = now()"
                 " where id = ?")
            title description body (:id row)])
        _ (if set-tags (set-tags! tx (:id row) tagList) nil)
        article (by-id tx (:id row) author-id)]
       article))))

(defn delete-article
  [ds slug author-id]
  (jdbc/with-transaction
   [tx ds nil]
   (let-nom>
     [row (owned tx slug author-id)
      ;; Tags, favorites and comments go with it, by cascade.
      _ (jdbc/execute-one! tx ["delete from articles where id = ?" (:id row)])]
     nil)))

(defn- with-article
  [tx slug f]
  (let-nom>
    [row (jdbc/execute-one! tx ["select id from articles where slug = ?" slug])]
    (if-not row
      (error/reject :realworld/article-not-found "not found")
      (f (:id row)))))

(defn favorite
  [ds slug user-id]
  (jdbc/with-transaction
   [tx ds nil]
   (with-article tx
                 slug
                 (fn [article-id]
                   (let-nom>
                     [_ (jdbc/execute-one!
                         tx
                         [(str "insert into favorites (user_id, article_id)"
                               " values (?, ?) on conflict do nothing")
                          user-id article-id])]
                     (by-id tx article-id user-id))))))

(defn unfavorite
  [ds slug user-id]
  (jdbc/with-transaction
   [tx ds nil]
   (with-article tx
                 slug
                 (fn [article-id]
                   (let-nom>
                     [_ (jdbc/execute-one!
                         tx
                         [(str "delete from favorites"
                               " where user_id = ? and article_id = ?")
                          user-id article-id])]
                     (by-id tx article-id user-id))))))

(defn tags
  "Tag names actually in use, alphabetically.

  In use rather than all rows: `set-tags!` never deletes tag rows, so an
  orphan is expected and should not be reported."
  [ds]
  (let-nom>
    [rows (jdbc/execute! ds
                         [(str "select distinct t.name from tags t"
                               "  join article_tags ats on ats.tag_id = t.id"
                               "  order by t.name")])]
    (mapv :name rows)))
