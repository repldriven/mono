(ns com.repldriven.mono.realworld-api.api
  "The 19 RealWorld endpoints.

  Reads call the store directly; writes go through the command bus. Both
  render through `realworld-domain`, which owns every string and status the
  conformance suite asserts — nothing here invents an error body."
  (:require
    [com.repldriven.mono.realworld-api.command :as cmd]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.realworld-domain.interface :as domain]
    [com.repldriven.mono.realworld-store.interface :as store]
    [com.repldriven.mono.server.interface :as server]

    [reitit.http :as http]
    [reitit.ring :as ring]))

;; --- request helpers -----------------------------------------------------

(defn- viewer
  "The caller's user id, or nil when unauthenticated.

  Claims carry the id as a string because JSON has no integers of the size
  we want, so it is parsed back here rather than at every call site."
  [request]
  (some-> (get-in request [:auth-claims :sub])
          (parse-long)))

(defn- not-found
  [kind]
  (domain/kind->response kind))

;; --- users ---------------------------------------------------------------

(defn- register
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:keys [user]} body]
    (cmd/response (cmd/send request "register" user)
                  201
                  #(domain/user-body % (store/token (:store request) %)))))

(defn- login
  "Not a command: it mutates nothing. It is a lookup plus a token mint, and
  routing it through the write bus would be uniformity at the cost of
  saying something untrue about it."
  [request]
  (let [{:keys [store parameters]} request
        {:keys [body]} parameters
        {:keys [user]} body
        {:keys [email password]} user
        result (store/authenticate store email password)]
    (if (error/anomaly? result)
      (not-found (error/kind result))
      {:status 200
       :body (domain/user-body result (store/token store result))})))

(defn- current-user
  [request]
  (let [{:keys [store]} request
        row (store/user store (viewer request))]
    {:status 200 :body (domain/user-body row (store/token store row))}))

(defn- update-user
  [request]
  (let [{:keys [store parameters]} request
        {:keys [body]} parameters
        {:keys [user]} body]
    (cmd/response (cmd/send request
                            "update-user"
                            {:actor-id (viewer request) :patch user})
                  200
                  #(domain/user-body % (store/token store %)))))

;; --- profiles ------------------------------------------------------------

(defn- profile
  [request]
  (let [{:keys [store parameters]} request
        {:keys [path]} parameters
        {:keys [username]} path
        row (store/profile store username (viewer request))]
    (if row
      {:status 200 :body (domain/profile-body row)}
      (not-found :realworld/profile-not-found))))

(defn- follow
  [request]
  (let [{:keys [parameters]} request
        {:keys [path]} parameters
        {:keys [username]} path]
    (cmd/response (cmd/send request
                            "follow"
                            {:actor-id (viewer request) :username username})
                  200
                  domain/profile-body)))

(defn- unfollow
  [request]
  (let [{:keys [parameters]} request
        {:keys [path]} parameters
        {:keys [username]} path]
    (cmd/response (cmd/send request
                            "unfollow"
                            {:actor-id (viewer request) :username username})
                  200
                  domain/profile-body)))

;; --- articles ------------------------------------------------------------

(defn- list-articles
  [request]
  (let [{:keys [store parameters]} request
        {:keys [query]} parameters
        {:keys [rows total]} (store/articles store query (viewer request))]
    {:status 200 :body (domain/articles-body rows total)}))

(defn- feed
  [request]
  (let [{:keys [store parameters]} request
        {:keys [query]} parameters
        {:keys [limit offset]} query
        {:keys [rows total]} (store/feed store (viewer request) limit offset)]
    {:status 200 :body (domain/articles-body rows total)}))

(defn- get-article
  [request]
  (let [{:keys [store parameters]} request
        {:keys [path]} parameters
        {:keys [slug]} path
        row (store/article store slug (viewer request))]
    (if row
      {:status 200 :body (domain/article-body row)}
      (not-found :realworld/article-not-found))))

(defn- create-article
  [request]
  (let [{:keys [parameters]} request
        {:keys [body]} parameters
        {:keys [article]} body]
    (cmd/response (cmd/send request
                            "create-article"
                            (assoc article
                                   :actor-id (viewer request)
                                   :author-id (viewer request)
                                   :slug (domain/slug (:title article))))
                  201
                  domain/article-body)))

(defn- update-article
  [request]
  (let [{:keys [parameters]} request
        {:keys [body path]} parameters
        {:keys [article]} body
        {:keys [slug]} path]
    (cmd/response (cmd/send request
                            "update-article"
                            {:actor-id (viewer request)
                             :slug slug
                             :patch article})
                  200
                  domain/article-body)))

(defn- delete-article
  [request]
  (let [{:keys [parameters]} request
        {:keys [path]} parameters
        {:keys [slug]} path]
    (cmd/response (cmd/send request
                            "delete-article"
                            {:actor-id (viewer request) :slug slug})
                  204)))

(defn- favorite
  [request]
  (let [{:keys [parameters]} request
        {:keys [path]} parameters
        {:keys [slug]} path]
    (cmd/response (cmd/send request
                            "favorite"
                            {:actor-id (viewer request) :slug slug})
                  200
                  domain/article-body)))

(defn- unfavorite
  [request]
  (let [{:keys [parameters]} request
        {:keys [path]} parameters
        {:keys [slug]} path]
    (cmd/response (cmd/send request
                            "unfavorite"
                            {:actor-id (viewer request) :slug slug})
                  200
                  domain/article-body)))

;; --- comments and tags ---------------------------------------------------

(defn- list-comments
  [request]
  (let [{:keys [store parameters]} request
        {:keys [path]} parameters
        {:keys [slug]} path
        rows (store/comments store slug (viewer request))]
    (if (error/anomaly? rows)
      (not-found (error/kind rows))
      {:status 200 :body (domain/comments-body rows)})))

(defn- create-comment
  [request]
  (let [{:keys [parameters]} request
        {:keys [body path]} parameters
        {:keys [comment]} body
        {:keys [slug]} path]
    (cmd/response (cmd/send request
                            "create-comment"
                            {:actor-id (viewer request)
                             :slug slug
                             :body (:body comment)})
                  201
                  domain/comment-body)))

(defn- delete-comment
  [request]
  (let [{:keys [parameters]} request
        {:keys [path]} parameters
        {:keys [slug id]} path]
    (cmd/response (cmd/send request
                            "delete-comment"
                            {:actor-id (viewer request)
                             :slug slug
                             :comment-id id})
                  204)))

(defn- tags
  [request]
  (let [{:keys [store]} request]
    {:status 200 :body (domain/tags-body (store/tags store))}))

;; --- routes --------------------------------------------------------------

(def ^:private paging
  [:map
   [:limit {:optional true} [:int {:min 1}]]
   [:offset {:optional true} [:int {:min 0}]]])

(defn- routes
  [ctx]
  (let [required [server/require-auth]]
    [["/api"
      {:interceptors (conj (vec (:interceptors ctx))
                           server/credential
                           server/authenticate-with-signer)
       :unauthorized domain/token-missing}
      ["/users" {:post {:parameters {:body domain/Register} :handler register}}]
      ["/users/login"
       {:post {:parameters {:body domain/Login}
               :handler login}}]
      ["/user"
       {:interceptors required
        :get {:handler current-user}
        :put {:parameters {:body domain/UpdateUser}
              :handler update-user}}]
      ["/profiles/{username}"
       {:get {:parameters {:path [:map [:username :string]]} :handler profile}}]
      ["/profiles/{username}/follow"
       {:interceptors required
        :parameters {:path [:map [:username :string]]}
        :post {:handler follow}
        :delete {:handler unfollow}}]
      ["/tags" {:get {:handler tags}}]
      ;; Declared ahead of /articles/{slug} so `feed` is not read as a
      ;; slug. Conflict detection is separate from match priority in
      ;; reitit, hence :conflicts nil below.
      ["/articles/feed"
       {:interceptors required
        :get {:parameters {:query paging} :handler feed}}]
      ["/articles"
       {:get {:parameters {:query (into paging
                                        [[:tag {:optional true} :string]
                                         [:author {:optional true} :string]
                                         [:favorited {:optional true}
                                          :string]])}
              :handler list-articles}
        :post {:interceptors required
               :parameters {:body domain/CreateArticle}
               :handler create-article}}]
      ["/articles/{slug}"
       {:parameters {:path [:map [:slug :string]]}
        :get {:handler get-article}
        :put {:interceptors required
              :parameters {:body domain/UpdateArticle}
              :handler update-article}
        :delete {:interceptors required :handler delete-article}}]
      ["/articles/{slug}/comments"
       {:parameters {:path [:map [:slug :string]]}
        :get {:handler list-comments}
        :post {:interceptors required
               :parameters {:body domain/AddComment}
               :handler create-comment}}]
      ["/articles/{slug}/comments/{id}"
       {:interceptors required
        :parameters {:path [:map [:slug :string] [:id :int]]}
        :delete {:handler delete-comment}}]
      ["/articles/{slug}/favorite"
       {:interceptors required
        :parameters {:path [:map [:slug :string]]}
        :post {:handler favorite}
        :delete {:handler unfavorite}}]]]))

(def ^:private exception-handlers
  "RealWorld's error body for the two failures reitit raises itself.

  `server/router-data` merges these over its defaults, so mono's RFC-9457
  shape is untouched for every other service — nothing in the published
  server brick had to change to serve an API with its own error contract."
  {:reitit.coercion/request-coercion (fn [ex _req]
                                       (domain/coercion->response (ex-data ex)))
   :muuntaja/decode (fn [_ex _req]
                      {:status 422 :body {:errors {:body ["is invalid"]}}})})

(defn app
  [ctx]
  ;; The RealWorld frontends run on a different origin to the API, so a
  ;; browser will not call it at all without the CORS wrapper.
  (-> (http/ring-handler (http/router (routes ctx)
                                      (assoc (server/router-data
                                              exception-handlers)
                                             :conflicts
                                             nil))
                         (ring/routes (ring/create-default-handler))
                         server/standard-executor)
      (server/wrap-cors (:cors ctx))))
