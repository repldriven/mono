(ns ^:eftest/synchronized com.repldriven.mono.realworld-api.api-test
  "The wiring, over real HTTP.

  The store and domain bricks already cover behaviour and the error
  contract; what can only be checked here is that reitit, coercion, the
  auth interceptors and the command bus are hooked together the way the
  suite requires — exact statuses, the `Token` scheme, route ordering, and
  an absent key surviving coercion."
  (:require
    com.repldriven.mono.testcontainers.interface ;; extends
                                                 ;; `system/components`
    com.repldriven.mono.migrator.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.message-bus.interface

    [com.repldriven.mono.realworld-api.api :as SUT]

    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.test :refer [deftest is testing]]))

(defn- with-api
  [f]
  (with-test-system
   [sys
    ["classpath:realworld-api/application-test.yml"
     #(assoc-in % [:system/defs :server :handler] SUT/app)]]
   (f (server/http-local-url (system/instance sys [:server :jetty-adapter])))))

(defn- call
  ([method url path] (call method url path nil nil))
  ([method url path body] (call method url path body nil))
  ([method url path body token]
   (let [res (http/request
              (cond-> {:method method
                       :url (str url path)
                       :headers (cond-> {"content-type" "application/json"}
                                        token
                                        (assoc "authorization"
                                               (str "Token " token)))}
                      body
                      (assoc :body (json/write-str body))))]
     {:status (:status res)
      :body (when (seq (:body res)) (json/read-str (:body res)))})))

(defn- register!
  [url n]
  (let [u (util/random-suffix 8)
        res (call :post
                  url
                  "/api/users"
                  {:user {:username (str n u)
                          :email (str n u "@test.com")
                          :password "password123"}})]
    {:token (get-in res [:body "user" "token"])
     :username (get-in res [:body "user" "username"])
     :status (:status res)}))

(deftest auth-endpoints-test
  (with-api
   (fn [url]
     (testing "registering returns 201 and a token"
       (let [{:keys [status token]} (register! url "api")]
         (is (= 201 status) "the spec wants 201, not 200")
         (is (string? token))))
     (testing "a blank field is 422 with RealWorld's error body"
       (let [res (call :post url
                       "/api/users" {:user {:username ""
                                            :email "x@test.com"
                                            :password "password123"}})]
         (is (= 422 (:status res)))
         (is (= ["can't be blank"] (get-in res [:body "errors" "username"])))))
     (testing "a duplicate is 409, not 422"
       (let [{:keys [username]} (register! url "dup")
             res (call :post url
                       "/api/users" {:user {:username username
                                            :email "other@test.com"
                                            :password "password123"}})]
         (is (= 409 (:status res)))
         (is (= ["has already been taken"]
                (get-in res [:body "errors" "username"])))))
     (testing "wrong credentials are 401 with credentials invalid"
       (let [{:keys [username]} (register! url "login")
             res (call :post url
                       "/api/users/login" {:user {:email (str username
                                                              "@test.com")
                                                  :password "wrongpassword"}})]
         (is (= 401 (:status res)))
         (is (= ["invalid"] (get-in res [:body "errors" "credentials"]))))))))

(deftest token-scheme-test
  (with-api
   (fn [url]
     (let [{:keys [token]} (register! url "tok")]
       (testing "no credential is 401 token is missing"
         (let [res (call :get url "/api/user")]
           (is (= 401 (:status res)))
           (is (= ["is missing"] (get-in res [:body "errors" "token"])))))
       (testing "the Token scheme is accepted"
         ;; RealWorld sends `Token`, not `Bearer`; a brick that only knew
         ;; Bearer would fail every authenticated request.
         (is (= 200 (:status (call :get url "/api/user" nil token)))))
       (testing "an invalid credential is refused, not treated as anonymous"
         (is (= 401 (:status (call :get url "/api/user" nil "not.a.jwt")))))))))

(deftest route-ordering-test
  (with-api (fn [url]
              (let [{:keys [token]} (register! url "feed")]
                (testing
                  "/articles/feed is the feed, not an article with slug feed"
                  ;; Declaration order decides the match; conflict
                  ;; detection is separate, which is why the router sets
                  ;; :conflicts nil.
                  (let [res (call :get url "/api/articles/feed" nil token)]
                    (is (= 200 (:status res)))
                    (is (contains? (:body res) "articles"))
                    (is (= 0 (get-in res [:body "articlesCount"])))))))))

(deftest article-lifecycle-test
  (with-api
   (fn [url]
     (let [{:keys [token]} (register! url "art")
           created (call :post
                         url
                         "/api/articles"
                         {:article {:title "How to train your dragon"
                                    :description "Ever wonder how?"
                                    :body "It takes a Jacobian"
                                    :tagList ["dragons" "training"]}}
                         token)
           slug (get-in created [:body "article" "slug"])]
       (testing "creating returns 201 and a full article"
         (is (= 201 (:status created)))
         (is (= ["dragons" "training"]
                (get-in created [:body "article" "tagList"])))
         (is (= 0 (get-in created [:body "article" "favoritesCount"])))
         (is (= "It takes a Jacobian"
                (get-in created [:body "article" "body"]))))
       (testing "duplicate titles get distinct slugs"
         (let [again (call :post
                           url
                           "/api/articles"
                           {:article {:title "How to train your dragon"
                                      :description "d"
                                      :body "b"}}
                           token)]
           (is (not= slug (get-in again [:body "article" "slug"])))))
       (testing "a list entry omits body entirely"
         (let [res (call :get url "/api/articles?limit=1")]
           (is (= 200 (:status res)))
           (is (not (contains? (first (get-in res [:body "articles"]))
                               "body")))))
       (testing "an absent tagList preserves tags through coercion"
         ;; This is the one that depends on :strip-extra-keys leaving a
         ;; declared optional key absent rather than nulling it.
         (let [res (call :put
                         url
                         (str "/api/articles/" slug)
                         {:article {:title "Renamed"}}
                         token)]
           (is (= 200 (:status res)))
           (is (= ["dragons" "training"]
                  (get-in res [:body "article" "tagList"])))))
       (testing "an empty tagList clears, and null is rejected"
         (is (= []
                (get-in (call :put
                              url
                              (str "/api/articles/" slug)
                              {:article {:tagList []}}
                              token)
                        [:body "article" "tagList"])))
         (is (= 422
                (:status (call :put
                               url
                               (str "/api/articles/" slug)
                               {:article {:tagList nil}}
                               token)))))
       (testing "deleting is 204 with no body, and then 404"
         (let [res (call :delete url (str "/api/articles/" slug) nil token)]
           (is (= 204 (:status res)))
           (is (nil? (:body res))))
         (is (= 404 (:status (call :get url (str "/api/articles/" slug))))))))))

(deftest authorization-test
  (with-api
   (fn [url]
     (let [owner (register! url "own")
           thief (register! url "thief")
           created (call :post
                         url
                         "/api/articles"
                         {:article {:title "Mine" :description "d" :body "b"}}
                         (:token owner))
           slug (get-in created [:body "article" "slug"])]
       (testing "a non-owner is 403 forbidden"
         (let [res (call :delete
                         url
                         (str "/api/articles/" slug)
                         nil
                         (:token thief))]
           (is (= 403 (:status res)))
           (is (= ["forbidden"] (get-in res [:body "errors" "article"])))))
       (testing "an unknown slug is 404 not found"
         (let [res (call :put
                         url
                         "/api/articles/nope"
                         {:article {:body "x"}}
                         (:token owner))]
           (is (= 404 (:status res)))
           (is (= ["not found"] (get-in res [:body "errors" "article"])))))))))
