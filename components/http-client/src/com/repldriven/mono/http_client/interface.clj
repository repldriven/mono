(ns com.repldriven.mono.http-client.interface
  "HTTP client (http-kit) under mono's rules.

  Every call comes in two forms. The plain one is synchronous and
  returns a response map or an anomaly, like every other interface
  here. The `-async` one returns a promise that delivers exactly the
  same thing, so `let-nom>` and friends work either side of a deref:

    (http/get url)                  ; => {:status …} or anomaly
    @(http/get-async url)           ; => {:status …} or anomaly

  That is a deliberate break from http-kit, where every call returns
  a promise and `@(http/get url)` is the idiom. Mirroring it would
  make this the one brick in the workspace whose functions hand back
  promises; naming the async ones instead means the mistake fails
  loudly — deref on a map — with the right function next to it.

  Failures come back as anomalies: `:http-client/request` for a
  client-level failure (DNS, refused, timeout) or a thrown exception,
  `:http-client/body-parse` for an unreadable body. An HTTP error
  status is NOT a failure — a 500 is a response, and you get it.

  Options are http-kit's, passed through untouched: `:timeout`,
  `:headers`, `:body`, `:as`, `:follow-redirects`, `:proxy-url`,
  `:insecure?`, `:client` and the rest.

  The verb functions cover the HTTP methods; WebDAV is left to
  `request`, which takes any `:method`."
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.http-client.body :as body]
    [com.repldriven.mono.http-client.core :as core]))

;; ---------------------------------------------------------------------------
;; requests

(defn request
  "Make a request. Returns the response map (`{:status :headers
  :body …}`) or an `:http-client/request` anomaly.

  Args:
  - opts: http-kit request options, including `:url` and `:method`."
  [opts]
  (core/request opts))

(defn request-async
  "Like `request`, but returns a promise delivering the response map
  or the anomaly.

  Args:
  - opts: http-kit request options, including `:url` and `:method`."
  [opts]
  (core/request-async opts))

(defn get
  "GET `url`. Returns the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/get url nil))
  ([url opts] (core/get url opts)))

(defn get-async
  "GET `url`, returning a promise of the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/get-async url nil))
  ([url opts] (core/get-async url opts)))

(defn post
  "POST to `url`. Returns the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options, `:body` among them."
  ([url] (core/post url nil))
  ([url opts] (core/post url opts)))

(defn post-async
  "POST to `url`, returning a promise of the response map or an
  anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options, `:body` among them."
  ([url] (core/post-async url nil))
  ([url opts] (core/post-async url opts)))

(defn put
  "PUT to `url`. Returns the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options, `:body` among them."
  ([url] (core/put url nil))
  ([url opts] (core/put url opts)))

(defn put-async
  "PUT to `url`, returning a promise of the response map or an
  anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options, `:body` among them."
  ([url] (core/put-async url nil))
  ([url opts] (core/put-async url opts)))

(defn patch
  "PATCH `url`. Returns the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options, `:body` among them."
  ([url] (core/patch url nil))
  ([url opts] (core/patch url opts)))

(defn patch-async
  "PATCH `url`, returning a promise of the response map or an
  anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options, `:body` among them."
  ([url] (core/patch-async url nil))
  ([url opts] (core/patch-async url opts)))

(defn delete
  "DELETE `url`. Returns the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/delete url nil))
  ([url opts] (core/delete url opts)))

(defn delete-async
  "DELETE `url`, returning a promise of the response map or an
  anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/delete-async url nil))
  ([url opts] (core/delete-async url opts)))

(defn head
  "HEAD `url`. Returns the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/head url nil))
  ([url opts] (core/head url opts)))

(defn head-async
  "HEAD `url`, returning a promise of the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/head-async url nil))
  ([url opts] (core/head-async url opts)))

(defn options
  "OPTIONS `url`. Returns the response map or an anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/options url nil))
  ([url opts] (core/options url opts)))

(defn options-async
  "OPTIONS `url`, returning a promise of the response map or an
  anomaly.

  Args:
  - url: the request URL.
  - opts: http-kit request options."
  ([url] (core/options-async url nil))
  ([url opts] (core/options-async url opts)))

;; ---------------------------------------------------------------------------
;; reading responses

(defn res->body
  "Extract the response body as a string, or as parsed JSON when the
  response's content-type contains `json`. JSON keys are strings.
  Passes anomalies through; returns nil for a nil response.

  Args:
  - res: a response map, an anomaly, or nil."
  [res]
  (body/res->body res))

(defn res->edn
  "Like `res->body`, but parses JSON with keyword keys.

  Args:
  - res: a response map, an anomaly, or nil."
  [res]
  (body/res->edn res))

;; ---------------------------------------------------------------------------
;; client configuration

(defn make-client
  "Build a client to pass as `:client` on later requests, for
  connection pooling, SSL configuration or a bound address. Returns
  the client or an anomaly.

  Args:
  - opts: http-kit client options — `:max-connections`,
    `:ssl-configurer`, `:bind-address` and the rest."
  [opts]
  (core/make-client opts))

(defn url-encode
  "Form-encode `s` for use in a query string. Note this is form
  encoding, so a space becomes `+` rather than `%20` — it suits a
  query value, not a path segment.

  Args:
  - s: the string to encode."
  [s]
  (core/url-encode s))

(defn query-string
  "Render `m` as a query string.

  Args:
  - m: a map of parameters.
  - style: nested-parameter style, per http-kit."
  ([m] (core/query-string m))
  ([m style] (core/query-string m style)))
