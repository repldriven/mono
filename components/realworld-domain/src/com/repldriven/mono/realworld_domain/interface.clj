(ns com.repldriven.mono.realworld-domain.interface
  "The parts of the RealWorld API that do not need a database: request
  schemas, the error contract, wire views, and slugs.

  Separated from `realworld-store` on purpose. Conformance to RealWorld is
  mostly a matter of exact strings, exact status codes and exact key
  casing — details that are easy to get wrong, easy to break while tidying,
  and slow to check if every check needs postgres. Everything here is pure,
  so the whole contract is covered by tests that run in under a second."
  (:require
    [com.repldriven.mono.realworld-domain.errors :as errors]
    [com.repldriven.mono.realworld-domain.schema :as schema]
    [com.repldriven.mono.realworld-domain.slug :as slug]
    [com.repldriven.mono.realworld-domain.view :as view]))

;; --- schemas -------------------------------------------------------------
;; Wire-shaped, for reitit `:parameters` and `:responses`.

(def Register schema/Register)
(def Login schema/Login)
(def UpdateUser schema/UpdateUser)
(def CreateArticle schema/CreateArticle)
(def UpdateArticle schema/UpdateArticle)
(def AddComment schema/AddComment)

;; --- errors --------------------------------------------------------------

(defn coercion->response
  "A reitit coercion failure as a 422 with RealWorld's error body.

  Args:
  - data: the `ex-data` of a `:reitit.coercion/request-coercion` exception."
  [data]
  (errors/coercion->response data))

(defn kind->response
  "The response for an anomaly kind, or nil if it is not one of ours. Used
  on the read path, where the anomaly itself is in hand.

  Args:
  - kind: e.g. `:realworld/article-not-found`."
  [kind]
  (errors/kind->response kind))

(defn reason->response
  "The response for a REJECTED command envelope's `:reason`, or nil.

  Used on the write path, where the command envelope has reduced the
  anomaly to the string form of its kind.

  Args:
  - reason: the `:reason` string from the envelope."
  [reason]
  (errors/reason->response reason))

(def token-missing
  "The 401 for a request with no usable credential. Set as the route
  data's `:unauthorized`, which `server/require-auth` terminates with
  rather than deciding a response shape itself."
  errors/token-missing)

;; --- views ---------------------------------------------------------------

(defn user-body
  "The `{:user {...}}` authentication response, carrying `token`."
  [row token]
  (view/user-body row token))

(defn profile-body
  "The `{:profile {...}}` response."
  [row]
  (view/profile-body row))

(defn article-body
  "The `{:article {...}}` response, including `body`."
  [row]
  (view/article-body row))

(defn articles-body
  "The `{:articles [...] :articlesCount n}` response. Entries omit `body`,
  and `total` is the count of all matches rather than of this page."
  [rows total]
  (view/articles-body rows total))

(defn comment-body
  "The `{:comment {...}}` response."
  [row]
  (view/comment-body row))

(defn comments-body
  "The `{:comments [...]}` response."
  [rows]
  (view/comments-body rows))

(defn tags-body
  "The `{:tags [...]}` response."
  [names]
  (view/tags-body names))

;; --- slugs ---------------------------------------------------------------

(defn slug
  "A URL slug for `title`, different on every call so that two articles
  sharing a title do not collide."
  [title]
  (slug/slug title))
