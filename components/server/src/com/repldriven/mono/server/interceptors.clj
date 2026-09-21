(ns com.repldriven.mono.server.interceptors
  (:require
    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.identity-provider.interface :as idp]

    [sieppari.context :as sc]

    [clojure.set :as set]))

(def ^:private idempotency-key-re #"^[A-Za-z0-9_\-]{16,255}$")

(def require-idempotency-key
  {:name ::require-idempotency-key
   :enter (fn [ctx]
            (let [key (get-in ctx [:request :headers "idempotency-key"])]
              (cond (nil? key)
                    (sc/terminate ctx
                                  {:status 400
                                   :body {:title "REJECTED"
                                          :type "mono/missing-idempotency-key"
                                          :status 400
                                          :detail
                                          "Missing Idempotency-Key header"}})
                    (not (re-matches idempotency-key-re key))
                    (sc/terminate
                     ctx
                     {:status 400
                      :body
                      {:title "REJECTED"
                       :type "mono/invalid-idempotency-key"
                       :status 400
                       :detail
                       "Idempotency-Key must be 16-255 URL-safe ASCII chars"}})
                    :else
                    ctx)))})

(def credential
  {:name ::credential
   :enter
   (fn [ctx]
     (let [header (get-in ctx [:request :headers "authorization"])
           credential (auth/header->token header auth/default-schemes)]
       (if credential (assoc-in ctx [:request :credential] credential) ctx)))})

(defn- with-claims
  [ctx claims]
  (if (and claims (not (error/anomaly? claims)))
    (assoc-in ctx [:request :auth-claims] claims)
    ctx))

(def authenticate-with-signer
  {:name ::authenticate-with-signer
   :enter (fn [ctx]
            (let [{:keys [request]} ctx
                  {:keys [signer credential]} request]
              (with-claims ctx
                           (when (and signer credential)
                             (auth/verify-token signer credential)))))})

(def authenticate-with-provider
  {:name ::authenticate-with-provider
   :enter (fn [ctx]
            (let [{:keys [request]} ctx
                  {:keys [identity-provider expected-audiences credential]}
                  request]
              (with-claims ctx
                           (when (and identity-provider credential)
                             (idp/verify-token identity-provider
                                               credential
                                               {:expected-audiences
                                                expected-audiences})))))})

(def ^:private default-unauthorized-response
  {:status 401
   :body {:title "UNAUTHORIZED"
          :type "server/unauthorized"
          :status 401
          :detail "Authentication required"}})

(def require-auth
  {:name ::require-auth
   :compile (fn [data _]
              (let [{:keys [unauthorized]} data
                    response (or unauthorized default-unauthorized-response)]
                {:enter (fn [ctx]
                          (if (get-in ctx [:request :auth-claims])
                            ctx
                            (sc/terminate ctx response)))}))})

(defn- requirement-objects?
  "True when `security` is a sequence of requirement objects: maps of a
  scheme name to the scopes it requires."
  [security]
  (and (sequential? security)
       (every? (fn [requirement]
                 (and (map? requirement)
                      (every? (fn [[scheme scopes]]
                                (and (string? scheme)
                                     (sequential? scopes)
                                     (every? string? scopes)))
                              requirement)))
               security)))

(defn- security-faults
  "What `validate-security` refuses in `security`, against the vocabulary the
  route data declares, as a map of fault to what caused it — empty when
  nothing is wrong."
  [{:keys [scopes exclusive-scopes]} security]
  (if-not (requirement-objects? security)
    {:malformed security}
    (let [named (into #{} (comp (mapcat vals) cat) security)
          bare (into #{}
                     (for [requirement security
                           [scheme scopes] requirement
                           :when (empty? scopes)]
                       scheme))
          unknown (when scopes (set/difference named (set scopes)))
          stacked (into #{}
                        (mapcat (fn [group]
                                  (let [held (set/intersection named
                                                               (set group))]
                                    (when (< 1 (count held)) held))))
                        exclusive-scopes)]
      (cond-> {}
              (and scopes (seq bare))
              (assoc :no-scopes bare)
              (seq unknown)
              (assoc :unknown-scopes unknown)
              (seq stacked)
              (assoc :exclusive stacked)))))

(def validate-security
  {:name ::validate-security
   :compile (fn [data _]
              (let [security (get-in data [:openapi :security])]
                (when (seq security)
                  (let [faults (security-faults data security)]
                    (when (seq faults)
                      ;; nosemgrep: no-raw-throw
                      (throw (ex-info (str "Route table declares a security "
                                           "gate the chain cannot enforce")
                                      (assoc faults
                                             :security security
                                             :operation
                                             (or (get-in data
                                                         [:openapi
                                                          :operationId])
                                                 (:summary data))))))))
                nil))})
