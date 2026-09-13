(ns ^:eftest/synchronized com.repldriven.mono.realworld-store.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface ;; extends
                                                 ;; `system/components`
    com.repldriven.mono.migrator.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.message-bus.interface

    [com.repldriven.mono.realworld-store.interface :as SUT]

    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.realworld-domain.interface :as domain]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.jdbc.interface :as jdbc]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.set :as set]
    [clojure.test :refer [deftest is testing]]))

(def ^:private tables
  #{"users" "follows" "articles" "tags" "article_tags" "favorites" "comments"})

(deftest schema-test
  (testing "the store starts with a migrated schema behind it"
    ;; The store refs migrator.migrations rather than jdbc.datasource, and
    ;; that reference is the only thing ordering schema creation before the
    ;; first query. If it were pointed at jdbc directly this would pass or
    ;; fail depending on start order.
    (with-test-system
     [sys "classpath:realworld-store/application-test.yml"]
     (let [store (system/instance sys [:realworld :store])
           {:keys [datasource]} store]
       (is (some? datasource))
       (is (some? (:signer store)))
       (testing "every table in the changelog exists"
         (let [present (->> (jdbc/execute! datasource
                                           [(str
                                             "select table_name from"
                                             " information_schema.tables"
                                             " where table_schema = 'public'")])
                            (map :table-name)
                            (set))]
           (is (empty? (remove present tables))
               (str "missing: " (remove present tables)))))
       (testing "the composite keys that make follow and favorite idempotent"
         ;; Inserting the same pair twice must be a no-op rather than a
         ;; duplicate row, which is what lets the write path use
         ;; `on conflict do nothing` instead of read-then-write.
         (let [pk (fn [table]
                    (->> (jdbc/execute! datasource
                                        [(str "select a.attname as col"
                                              " from pg_index i"
                                              " join pg_attribute a"
                                              "   on a.attrelid = i.indrelid"
                                              "  and a.attnum = any(i.indkey)"
                                              " where i.indrelid = ?::regclass"
                                              "   and i.indisprimary") table])
                         (map :col)
                         (set)))]
           (is (= #{"follower_id" "followed_id"} (pk "follows")))
           (is (= #{"user_id" "article_id"} (pk "favorites")))
           (is (= #{"article_id" "tag_id"} (pk "article_tags")))))))))

(defn- unique
  "A suffix so a test's fixtures cannot collide with another's, since the
  container is shared across the namespace."
  []
  (util/random-suffix 12))

(defn- with-store
  [f]
  (with-test-system
   [sys "classpath:realworld-store/application-test.yml"]
   (f (system/instance sys [:realworld :store]))))

(deftest register-test
  (with-store
   (fn [store]
     (let [u (unique)]
       (testing "a registered user comes back with a hashed password"
         (let [row (SUT/register store
                                 {:username (str "jake" u)
                                  :email (str "jake" u "@test.com")
                                  :password "password123"})]
           (is (int? (:id row)))
           (is (= (str "jake" u) (:username row)))
           (is (not= "password123" (:password-hash row))
               "the plaintext must never be stored")
           (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z"
                           (:created-at row))
               "timestamps are pre-formatted for the wire")))
       (testing "a duplicate username names username, not email"
         ;; Which field clashed has to be named, and a 23505
         ;; anomaly cannot say — hence the explicit check before
         ;; insert.
         (let [dup (SUT/register store
                                 {:username (str "jake" u)
                                  :email (str "other" u "@test.com")
                                  :password "password123"})]
           (is (error/rejection? dup))
           (is (= :realworld/username-taken (error/kind dup)))))
       (testing "a duplicate email names email"
         (let [dup (SUT/register store
                                 {:username (str "other" u)
                                  :email (str "jake" u "@test.com")
                                  :password "password123"})]
           (is (= :realworld/email-taken (error/kind dup)))))))))

(deftest authenticate-test
  (with-store
   (fn [store]
     (let [u (unique)
           email (str "auth" u "@test.com")
           row (SUT/register store
                             {:username (str "auth" u)
                              :email email
                              :password "password123"})]
       (testing "the right password authenticates"
         (is (= (:id row) (:id (SUT/authenticate store email "password123")))))
       (testing "an unknown email and a wrong password fail identically"
         ;; Distinguishing them would tell an attacker which emails exist.
         (let [wrong (SUT/authenticate store email "wrongpassword")
               unknown (SUT/authenticate store
                                         (str "nobody" u "@test.com")
                                         "password123")]
           (is (= :realworld/credentials-invalid (error/kind wrong)))
           (is (= :realworld/credentials-invalid (error/kind unknown)))))
       (testing "a token identifies the user by id, so a rename cannot break it"
         (let [t (SUT/token store row)]
           (is (string? t))
           (is (= (str (:id row))
                  (:sub (auth/verify-token (:signer store) t))))))))))

(deftest update-user-test
  (with-store
   (fn [store]
     (let [u (unique)
           row (SUT/register store
                             {:username (str "upd" u)
                              :email (str "upd" u "@test.com")
                              :password "password123"})]
       (testing "only the keys present are changed"
         (let [updated
               (SUT/update-user store (:id row) {:bio "I work at statefarm"})]
           (is (= "I work at statefarm" (:bio updated)))
           (is (= (:username row) (:username updated))
               "an absent key must be left alone, not nulled")))
       (testing "bio can be set back to null explicitly"
         (let [updated (SUT/update-user store (:id row) {:bio nil})]
           (is (nil? (:bio updated)))))
       (testing "a password change re-hashes and the old one stops working"
         (let [_ (SUT/update-user store (:id row) {:password "newpassword1"})
               email (:email row)]
           (is (error/rejection? (SUT/authenticate store email "password123")))
           (is (some? (:id (SUT/authenticate store email "newpassword1"))))))
       (testing
         "taking another user's username is rejected, but keeping your own is not"
         (let [other (SUT/register store
                                   {:username (str "other" u)
                                    :email (str "other" u "@test.com")
                                    :password "password123"})
               clash
               (SUT/update-user store (:id row) {:username (:username other)})
               same
               (SUT/update-user store (:id row) {:username (:username row)})]
           (is (= :realworld/username-taken (error/kind clash)))
           (is (= (:username row) (:username same))
               "excluding self is what makes a no-op rename succeed")))))))

(deftest profile-and-follow-test
  (with-store
   (fn [store]
     (let [u (unique)
           me (SUT/register store
                            {:username (str "me" u)
                             :email (str "me" u "@test.com")
                             :password "password123"})
           them (SUT/register store
                              {:username (str "them" u)
                               :email (str "them" u "@test.com")
                               :password "password123"})]
       (testing "an anonymous viewer sees following false, not an error"
         (let [p (SUT/profile store (:username them) nil)]
           (is (= (:username them) (:username p)))
           (is (false? (:following p)))))
       (testing "following is reflected and is idempotent"
         (is (true? (:following (SUT/follow store (:id me) (:username them)))))
         (is (true? (:following (SUT/follow store (:id me) (:username them))))
             "following twice is a no-op, not a duplicate-key error")
         (is (true? (:following
                     (SUT/profile store (:username them) (:id me))))))
       (testing "unfollowing is also idempotent"
         (is (false? (:following
                      (SUT/unfollow store (:id me) (:username them)))))
         (is (false? (:following
                      (SUT/unfollow store (:id me) (:username them))))))
       (testing "an unknown username is a not-found rejection"
         (is (= :realworld/profile-not-found
                (error/kind (SUT/follow store (:id me) (str "ghost" u)))))
         (is (nil? (SUT/profile store (str "ghost" u) nil))))))))
(defn- author!
  [store u n]
  (SUT/register store
                {:username (str n u)
                 :email (str n u "@test.com")
                 :password "password123"}))

(defn- article!
  [store author title tags]
  (SUT/create-article store
                      {:author-id (:id author)
                       :slug (str "slug-" (util/random-suffix 12))
                       :title title
                       :description "d"
                       :body "b"
                       :tagList tags}))

(deftest article-read-test
  (with-store
   (fn [store]
     (let [u (unique)
           jake (author! store u "jake")
           a (article! store
                       jake
                       "How to train your dragon"
                       ["dragons" "training"])]
       (testing "tagList comes back as strings, not a java.sql.Array"
         ;; pgjdbc hands array_agg back as a PgArray; unconverted it would
         ;; serialise into the JSON body as an opaque object.
         (is (vector? (:tag-list a)))
         (is (every? string? (:tag-list a)))
         (is (= ["dragons" "training"] (:tag-list a))
             "alphabetical, which is what the suite's index assertions expect"))
       (testing "an article with no tags is an empty vector, not nil"
         (let [none (article! store jake "Untagged" [])]
           (is (= [] (:tag-list none)))))
       (testing "an anonymous viewer sees false rather than an error"
         (let [seen (SUT/article store (:slug a) nil)]
           (is (false? (:favorited seen)))
           (is (false? (:author-following seen)))
           (is (= 0 (:favorites-count seen)))))
       (testing "an unknown slug is nil"
         (is (nil? (SUT/article store "no-such-slug" nil))))))))

(deftest article-favorite-and-count-test
  (with-store
   (fn [store]
     (let [u (unique)
           jake (author! store u "fav")
           fan (author! store u "fan")
           a (article! store jake "Favoured" ["x"])]
       (testing "favoriting is reflected for the actor and counted for all"
         (let [mine (SUT/favorite store (:slug a) (:id fan))]
           (is (true? (:favorited mine)))
           (is (= 1 (:favorites-count mine))))
         (is (false? (:favorited (SUT/article store (:slug a) (:id jake))))
             "someone else's favorite is counted but not attributed")
         (is (= 1 (:favorites-count (SUT/article store (:slug a) (:id jake))))))
       (testing "favoriting twice does not double count"
         (let [again (SUT/favorite store (:slug a) (:id fan))]
           (is (= 1 (:favorites-count again)))))
       (testing "unfavoriting is idempotent"
         (is (= 0
                (:favorites-count (SUT/unfavorite store (:slug a) (:id fan)))))
         (is (= 0
                (:favorites-count
                 (SUT/unfavorite store (:slug a) (:id fan))))))))))

(deftest article-update-test
  (with-store
   (fn [store]
     (let [u (unique)
           jake (author! store u "upd-a")
           other (author! store u "other-a")
           a (article! store jake "Original" ["keep" "these"])]
       (testing "an absent tagList preserves tags; an empty one clears them"
         (let [kept (SUT/update-article store
                                        (:slug a)
                                        (:id jake)
                                        {:title "Changed"})]
           (is (= "Changed" (:title kept)))
           (is (= ["keep" "these"] (:tag-list kept))))
         (let [cleared
               (SUT/update-article store (:slug a) (:id jake) {:tagList []})]
           (is (= [] (:tag-list cleared)))))
       (testing "the slug does not change and updatedAt moves past createdAt"
         (let [updated
               (SUT/update-article store (:slug a) (:id jake) {:body "new"})]
           (is (= (:slug a) (:slug updated)))
           (is (not= (:created-at updated) (:updated-at updated))
               "microsecond precision is what makes this reliable")))
       (testing "a non-owner is forbidden, an unknown slug is not found"
         (is (= :realworld/article-forbidden
                (error/kind
                 (SUT/update-article store (:slug a) (:id other) {:body "x"}))))
         (is (= :realworld/article-forbidden
                (error/kind (SUT/delete-article store (:slug a) (:id other)))))
         (is
          (= :realworld/article-not-found
             (error/kind
              (SUT/update-article store "nope" (:id jake) {:body "x"})))))))))

(deftest article-list-and-feed-test
  (with-store
   (fn [store]
     (let [u (unique)
           jake (author! store u "list-a")
           bob (author! store u "list-b")
           _ (article! store jake "One" ["shared" (str "t" u)])
           _ (article! store jake "Two" [(str "t" u)])
           _ (article! store bob "Three" [(str "t" u)])]
       (testing "articlesCount is the total, while rows are the page"
         (let [{:keys [rows total]}
               (SUT/articles store {:tag (str "t" u) :limit 1} nil)]
           (is (= 1 (count rows)))
           (is (= 3 total) "count(*) over () counts matches, not the page")))
       (testing "filtering by author narrows both rows and total"
         (let [{:keys [rows total]} (SUT/articles store
                                                  {:tag (str "t" u)
                                                   :author (:username jake)}
                                                  nil)]
           (is (= 2 (count rows)))
           (is (= 2 total))))
       (testing "offset pages through without repeating"
         (let [p1 (:rows (SUT/articles store
                                       {:tag (str "t" u) :limit 2 :offset 0}
                                       nil))
               p2 (:rows (SUT/articles store
                                       {:tag (str "t" u) :limit 2 :offset 2}
                                       nil))]
           (is (= 2 (count p1)))
           (is (= 1 (count p2)))
           (is (empty? (set/intersection (set (map :slug p1))
                                         (set (map :slug p2)))))))
       (testing "a feed is empty until you follow someone, then it is theirs"
         (is (= 0 (:total (SUT/feed store (:id bob) nil nil))))
         (SUT/follow store (:id bob) (:username jake))
         (let [{:keys [rows total]} (SUT/feed store (:id bob) nil nil)]
           (is (= 2 total))
           (is (every? #(= (:username jake) (:author-username %)) rows))))))))

(deftest comments-and-tags-test
  (with-store
   (fn [store]
     (let [u (unique)
           jake (author! store u "cm-a")
           other (author! store u "cm-b")
           a (article! store jake "Commented" [(str "tag" u)])
           c (SUT/create-comment store
                                 (:slug a)
                                 (:id jake)
                                 "It takes a Jacobian")]
       (testing "a comment has an integer id and its author"
         (is (int? (:id c)))
         (is (= (:username jake) (:author-username c)))
         (is (= "It takes a Jacobian" (:body c))))
       (testing "comments list oldest first"
         (SUT/create-comment store (:slug a) (:id other) "second")
         (let [rows (SUT/comments store (:slug a) nil)]
           (is (= ["It takes a Jacobian" "second"] (mapv :body rows)))))
       (testing "only the author may delete, and not-found beats forbidden"
         (is (= :realworld/comment-forbidden
                (error/kind
                 (SUT/delete-comment store (:slug a) (:id c) (:id other)))))
         (is (= :realworld/comment-not-found
                (error/kind
                 (SUT/delete-comment store (:slug a) 999999 (:id jake)))))
         (is (= :realworld/article-not-found
                (error/kind (SUT/comments store "no-such-slug" nil))))
         (is (nil? (SUT/delete-comment store (:slug a) (:id c) (:id jake)))))
       (testing "tags in use are listed"
         (is (some #{(str "tag" u)} (SUT/tags store))))
       (testing "deleting an article cascades to its comments"
         (let [slug (:slug a)]
           (is (nil? (SUT/delete-article store slug (:id jake))))
           (is (nil? (SUT/article store slug nil)))
           (is (= :realworld/article-not-found
                  (error/kind (SUT/comments store slug nil))))))))))

(defn- with-system
  "The whole write path: store, processor, command-processor and a
  dispatcher over a local bus."
  [f]
  (with-test-system
   [sys "classpath:realworld-store/application-test.yml"]
   (f (system/instance sys [:realworld :store])
      (system/instance sys [:realworld :dispatcher]))))

(defn- send!
  [dispatcher command payload]
  (command/send dispatcher
                {:command command
                 :id (str (util/uuidv7))
                 :correlation-id (str (util/uuidv7))
                 :payload payload}))

(deftest command-path-test
  (with-system
    (fn [store dispatcher]
      (let [u (unique)]
        (testing "a write arrives through the bus and comes back ACCEPTED"
          (let [reply (send! dispatcher
                             "register"
                             {:username (str "cmd" u)
                              :email (str "cmd" u "@test.com")
                              :password "password123"})]
            (is (= "ACCEPTED" (:status reply)))
            (is (int? (get-in reply [:payload :id])))))
        (testing "a rejection comes back REJECTED, carrying its kind as reason"
          ;; The envelope keeps only the kind, which is why the error table
          ;; in realworld-domain is keyed by its string form.
          (let [reply (send! dispatcher
                             "register"
                             {:username (str "cmd" u)
                              :email (str "other" u "@test.com")
                              :password "password123"})]
            (is (= "REJECTED" (:status reply)))
            (is (= ":realworld/username-taken" (:reason reply)))
            (is (some? (domain/reason->response (:reason reply)))
                "the reason must resolve in the response table")))
        (testing "an ownership failure is REJECTED, not silently ACCEPTED"
          ;; command/command-response tests rejection? then error? and
          ;; treats anything else as success, so building this with
          ;; error/unauthorized would report a forbidden write as ACCEPTED
          ;; with a null payload. This asserts we did not.
          (let [jake (author! store u "cmd-own")
                thief (author! store u "cmd-thief")
                a (article! store jake "Owned" [])
                reply (send! dispatcher
                             "delete-article"
                             {:slug (:slug a) :actor-id (:id thief)})]
            (is (= "REJECTED" (:status reply)))
            (is (= ":realworld/article-forbidden" (:reason reply)))
            (is (some? (SUT/article store (:slug a) nil))
                "and the article is still there")))
        (testing "an unknown command is rejected rather than ignored"
          (let [reply (send! dispatcher "not-a-command" {})]
            (is (= "REJECTED" (:status reply)))))))))
