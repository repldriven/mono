(ns com.repldriven.mono.realworld-domain.interface-test
  "The RealWorld contract, pinned.

  Every string and status here is asserted verbatim by the official Hurl
  suite, so these are not stylistic choices and tidying them breaks
  conformance. Nothing in this namespace touches a database, so it is cheap
  enough to run on every change."
  (:require
    [com.repldriven.mono.realworld-domain.interface :as SUT]

    [malli.core :as m]

    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]))

(defn- errors
  "The `errors` map a coercion failure would produce for `value`."
  [schema value]
  (get-in (SUT/coercion->response (m/explain schema value)) [:body :errors]))

(defn- status
  [schema value]
  (:status (SUT/coercion->response (m/explain schema value))))

(deftest coercion-blank-test
  (testing "absent, null and empty all report as can't be blank"
    ;; One condition as far as the caller is concerned, and the suite
    ;; asserts the same string for all three.
    (is (= {:username ["can't be blank"]
            :email ["can't be blank"]
            :password ["can't be blank"]}
           (errors SUT/Register {:user {}}))
        "absent keys")
    (is (= {:username ["can't be blank"]}
           (errors SUT/UpdateUser {:user {:username nil}}))
        "explicit null")
    (is (= {:username ["can't be blank"]}
           (errors SUT/UpdateUser {:user {:username ""}}))
        "empty string"))
  (testing "the status is 422" (is (= 422 (status SUT/Register {:user {}}))))
  (testing "the field is the last keyword on the path, not the wrapper"
    (is (= [:email]
           (keys (errors SUT/Login
                         {:user {:email "" :password "password123"}}))))))

(deftest coercion-password-test
  (testing "a blank password is blank, not too short"
    (is (= {:password ["can't be blank"]}
           (errors SUT/UpdateUser {:user {:password ""}})))
    (is (= {:password ["can't be blank"]}
           (errors SUT/UpdateUser {:user {:password nil}}))))
  (testing "a short password reports its own message"
    ;; The blank branch is checked first, so :error/message only ever
    ;; describes the non-blank failure. This is the case that would break
    ;; if the two were collapsed.
    (is (= {:password ["is too short (minimum is 8 characters)"]}
           (errors SUT/UpdateUser {:user {:password "short7c"}}))))
  (testing "8 and 64 characters are both accepted"
    (is (nil? (m/explain SUT/UpdateUser {:user {:password "bonjour1"}})))
    (is (nil? (m/explain SUT/UpdateUser
                         {:user {:password (str/join (repeat 64 "a"))}}))))
  (testing "login does not apply the length rule"
    ;; A too-short password at login is a wrong credential (401), not a
    ;; malformed request (422).
    (is (nil? (m/explain SUT/Login
                         {:user {:email "a@b.com" :password "short7c"}})))))

(deftest coercion-taglist-test
  (testing "absent means leave alone, and is not an error"
    (is (nil? (m/explain SUT/UpdateArticle {:article {:title "t"}}))))
  (testing "an empty vector means clear, and is not an error"
    (is (nil? (m/explain SUT/UpdateArticle {:article {:tagList []}}))))
  (testing "null is rejected"
    (is (= 422 (status SUT/UpdateArticle {:article {:tagList nil}})))
    (is (contains? (errors SUT/UpdateArticle {:article {:tagList nil}})
                   :tagList))))

(deftest coercion-multiple-errors-test
  (testing "every failing field is reported, not just the first"
    (is (= {:title ["can't be blank"] :description ["can't be blank"]}
           (errors SUT/CreateArticle
                   {:article {:title "" :description "" :body "b"}})))))

(deftest kind-response-test
  ;; Spelled out rather than table-driven: when one of these breaks, the
  ;; failure should name the case.
  (is (= {:status 409 :body {:errors {:username ["has already been taken"]}}}
         (SUT/kind->response :realworld/username-taken)))
  (is (= {:status 409 :body {:errors {:email ["has already been taken"]}}}
         (SUT/kind->response :realworld/email-taken)))
  (is (= {:status 401 :body {:errors {:credentials ["invalid"]}}}
         (SUT/kind->response :realworld/credentials-invalid)))
  (is (= {:status 401 :body {:errors {:token ["is missing"]}}}
         (SUT/kind->response :realworld/token-missing)))
  (is (= {:status 404 :body {:errors {:article ["not found"]}}}
         (SUT/kind->response :realworld/article-not-found)))
  (is (= {:status 404 :body {:errors {:comment ["not found"]}}}
         (SUT/kind->response :realworld/comment-not-found)))
  (is (= {:status 404 :body {:errors {:profile ["not found"]}}}
         (SUT/kind->response :realworld/profile-not-found)))
  (is (= {:status 403 :body {:errors {:article ["forbidden"]}}}
         (SUT/kind->response :realworld/article-forbidden)))
  (is (= {:status 403 :body {:errors {:comment ["forbidden"]}}}
         (SUT/kind->response :realworld/comment-forbidden)))
  (is (nil? (SUT/kind->response :realworld/not-a-real-kind))))

(deftest reason-response-test
  (testing "the write path matches on the string form, colon included"
    ;; command/command-response sets :reason to (str (error/kind anomaly)),
    ;; which keeps the leading colon. Getting this wrong makes every write
    ;; rejection fall through to a generic 422.
    (is (= (SUT/kind->response :realworld/article-not-found)
           (SUT/reason->response ":realworld/article-not-found")))
    (is (= (SUT/kind->response :realworld/article-forbidden)
           (SUT/reason->response (str :realworld/article-forbidden))))
    (is (nil? (SUT/reason->response "realworld/article-not-found"))
        "without the colon it is not a kind we produced")
    (is (nil? (SUT/reason->response nil))))
  (testing "token-missing is the 401 require-auth terminates with"
    (is (= {:status 401 :body {:errors {:token ["is missing"]}}}
           SUT/token-missing))))

(def ^:private article-row
  {:slug "how-to-train-your-dragon-abc12345"
   :title "How to train your dragon"
   :description "Ever wonder how?"
   :body "It takes a Jacobian"
   :tag-list ["dragons" "training"]
   :created-at "2016-02-18T03:22:56.637000Z"
   :updated-at "2016-02-18T03:48:35.824000Z"
   :favorited true
   :favorites-count 3
   :author-username "jake"
   :author-bio "I work at statefarm"
   :author-image nil
   :author-following true})

(deftest article-view-test
  (testing "a single article carries body and camelCase keys"
    (let [{:keys [article]} (SUT/article-body article-row)]
      (is (= "It takes a Jacobian" (:body article)))
      (is (= ["dragons" "training"] (:tagList article)))
      (is (= 3 (:favoritesCount article)))
      (is (true? (:favorited article)))
      (is (= "2016-02-18T03:22:56.637000Z" (:createdAt article)))
      (is
       (=
        {:username "jake" :bio "I work at statefarm" :image nil :following true}
        (:author article)))))
  (testing "a list entry omits body entirely"
    ;; Absent, not null — the suite asserts the key does not exist.
    (let [{:keys [articles]} (SUT/articles-body [article-row] 1)]
      (is (not (contains? (first articles) :body)))
      (is (= ["dragons" "training"] (:tagList (first articles))))))
  (testing "articlesCount is the total, not the page size"
    (let [body (SUT/articles-body [article-row] 2)]
      (is (= 1 (count (:articles body))))
      (is (= 2 (:articlesCount body)))))
  (testing "an empty page is still a list and a count"
    (is (= {:articles [] :articlesCount 0} (SUT/articles-body [] 0)))))

(deftest profile-and-user-view-test
  (testing "empty bio and image normalise to null"
    ;; The suite writes "" and then asserts null comes back.
    (let [{:keys [profile]}
          (SUT/profile-body
           {:username "jake" :bio "" :image "" :following false})]
      (is (nil? (:bio profile)))
      (is (nil? (:image profile)))
      (is (false? (:following profile)))))
  (testing "following is a boolean even when the row says nothing"
    (is (false? (get-in (SUT/profile-body {:username "jake"})
                        [:profile :following]))))
  (testing "the user body carries the token and never the hash"
    (let [{:keys [user]} (SUT/user-body {:username "jake"
                                         :email "jake@jake.jake"
                                         :password-hash "secret"
                                         :bio nil
                                         :image nil}
                                        "jwt.token.here")]
      (is (= "jwt.token.here" (:token user)))
      (is (= "jake@jake.jake" (:email user)))
      (is (= #{:email :token :username :bio :image} (set (keys user)))))))

(deftest comment-view-test
  (testing "id stays an integer and the author is a profile"
    (let [{:keys [comment]} (SUT/comment-body
                             {:id 1
                              :body "It takes a Jacobian"
                              :created-at "2016-02-18T03:22:56.637000Z"
                              :updated-at "2016-02-18T03:22:56.637000Z"
                              :author-username "jake"})]
      (is (= 1 (:id comment)))
      (is (int? (:id comment)))
      (is (= "jake" (get-in comment [:author :username])))
      (is (false? (get-in comment [:author :following])))))
  (testing "a comments body has no count, unlike articles"
    (is (= {:comments []} (SUT/comments-body [])))))

(deftest tags-view-test
  (is (= {:tags ["dragons" "training"]} (SUT/tags-body ["dragons" "training"])))
  (is (= {:tags []} (SUT/tags-body nil))))

(deftest slug-test
  (testing "the same title yields different slugs, even back to back"
    ;; Duplicate titles are allowed and must not collide. Taking the suffix
    ;; from the head of a uuidv7 passes a lazy version of this test and
    ;; fails here: the head is a millisecond clock, so successive calls
    ;; within the same minute agree.
    (is (apply distinct?
               (repeatedly 50 #(SUT/slug "How to train your dragon")))))
  (testing "a slug is lower-case, hyphenated and starts with the title"
    (let [s (SUT/slug "How to Train Your Dragon!")]
      (is (str/starts-with? s "how-to-train-your-dragon-"))
      (is (re-matches #"[a-z0-9-]+" s))))
  (testing "punctuation does not leave doubled or trailing separators"
    (is (re-matches #"[a-z0-9]+(-[a-z0-9]+)*"
                    (SUT/slug "  Hello,   World!!  "))))
  (testing "a title with nothing slugifiable still yields a usable slug"
    (let [s (SUT/slug "!!!")]
      (is (seq s))
      (is (re-matches #"[a-z0-9-]+" s)))))
