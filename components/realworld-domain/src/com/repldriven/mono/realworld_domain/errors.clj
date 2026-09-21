(ns com.repldriven.mono.realworld-domain.errors
  "RealWorld's error contract: `{\"errors\": {\"<field>\": [\"<message>\"]}}`.

  Two sources feed it. Malli coercion failures become 422s, translated
  explicitly rather than through `malli.error/humanize` — an absent key
  humanizes as `missing required key`, and the API wants `can't be blank`
  for absent, null and empty alike, so the mapping is written out where it
  can be read and tested.

  Everything else is an anomaly kind. The write path only ever gets a kind
  back, because `command/command-response` reduces a rejection to its
  `:reason` — but every rejection here is a fixed field-and-message pair, so
  the kind is all that is needed."
  (:require
    [malli.core :as m]))

(defn- field
  "The field an error belongs to: the last keyword on its `:in` path, so
  `[:user :email]` reports as `email`. Falls back to `body`, which is what
  the suite expects for a whole-body failure."
  [error]
  (or (last (filter keyword? (:in error))) :body))

(defn- message
  "The message for one coercion error.

  Absent, null and empty string are one condition — the caller did not
  supply a value — and collapse to `can't be blank` regardless of which
  schema rejected them. Anything else defers to the schema's own
  `:error/message`, which is how `password` reports being too short rather
  than being blank."
  [error]
  (let [{:keys [value type schema]} error]
    (cond (= :malli.core/missing-key type)
          "can't be blank"
          (nil? value)
          "can't be blank"
          (= "" value)
          "can't be blank"
          :else
          (or (:error/message (m/properties schema)) "is invalid"))))

(defn coercion->response
  "A reitit coercion `ex-data` map as a 422 response.

  Args:
  - data: the `ex-data` of a `:reitit.coercion/request-coercion` exception."
  [data]
  {:status 422
   :body {:errors (reduce (fn [acc error]
                            (update acc
                                    (field error)
                                    (fnil conj [])
                                    (message
                                     error)))
                          {}
                          (:errors data))}})

(def ^:private by-kind
  "Every non-coercion failure the API can report, as anomaly kind to
  response. One table so the read path and the write path cannot disagree."
  {:realworld/username-taken [409
                              {:errors {:username ["has already been taken"]}}]
   :realworld/email-taken [409 {:errors {:email ["has already been taken"]}}]
   :realworld/credentials-invalid [401 {:errors {:credentials ["invalid"]}}]
   :realworld/token-missing [401 {:errors {:token ["is missing"]}}]
   :realworld/article-not-found [404 {:errors {:article ["not found"]}}]
   :realworld/comment-not-found [404 {:errors {:comment ["not found"]}}]
   :realworld/profile-not-found [404 {:errors {:profile ["not found"]}}]
   :realworld/article-forbidden [403 {:errors {:article ["forbidden"]}}]
   :realworld/comment-forbidden [403 {:errors {:comment ["forbidden"]}}]})

(def ^:private by-reason
  "The same table keyed by the string form of each kind.

  `command/command-response` sets `:reason` to `(str (error/kind anomaly))`,
  which keeps the leading colon, so the write path matches on `\":realworld/
  article-not-found\"` rather than on the keyword."
  (update-keys by-kind str))

(defn- ->response
  [entry]
  (when entry
    (let [[status body] entry]
      {:status status :body body})))

(defn kind->response
  "The response for an anomaly kind, or nil if it is not one of ours.

  Args:
  - kind: the anomaly kind keyword, e.g. `:realworld/article-not-found`."
  [kind]
  (->response (get by-kind kind)))

(defn reason->response
  "The response for a command envelope's `:reason`, or nil if unrecognised.

  Args:
  - reason: the `:reason` string from a REJECTED command response."
  [reason]
  (->response (get by-reason reason)))

(def token-missing
  "The 401 an unauthenticated request gets: the route data's
  `:unauthorized`, which `server/require-auth` terminates with."
  (kind->response :realworld/token-missing))
