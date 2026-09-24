(ns com.repldriven.mono.server.interceptors
  (:require
    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.identity-provider.interface :as idp]
    [com.repldriven.mono.log.interface :as log]

    [sieppari.context :as sc]

    [clojure.set :as set]
    [clojure.string :as str]))

(def ^:private idempotency-key-re #"^[A-Za-z0-9_\-]{16,255}$")

(def require-idempotency-key
  {:name ::require-idempotency-key
   :enter (fn [ctx]
            (let [key (get-in ctx [:request :headers "idempotency-key"])]
              (cond (nil? key)
                    (sc/terminate ctx
                                  {:status 400
                                   :body {:title "REJECTED"
                                          :type "server/missing-idempotency-key"
                                          :status 400
                                          :detail
                                          "Missing Idempotency-Key header"}})
                    (not (re-matches idempotency-key-re key))
                    (sc/terminate
                     ctx
                     {:status 400
                      :body
                      {:title "REJECTED"
                       :type "server/invalid-idempotency-key"
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

(defn- provider-for
  "The provider among `providers` whose issuer the unverified `iss` of
  `credential` names, or nil when none does."
  [providers credential]
  (let [iss (:iss (auth/unverified-claims credential))]
    (some (fn [provider] (when (= iss (idp/get-issuer provider)) provider))
          providers)))

(def authenticate-with-provider
  {:name ::authenticate-with-provider
   :enter
   (fn [ctx]
     (let [{:keys [request]} ctx
           {:keys [identity-provider identity-providers expected-audiences
                   credential]}
           request
           provider (when credential
                      (if (seq identity-providers)
                        (provider-for identity-providers credential)
                        identity-provider))
           claims (when provider
                    (idp/verify-token provider
                                      credential
                                      {:expected-audiences
                                       (when (seq expected-audiences)
                                         (set expected-audiences))}))]
       (cond
        (nil? credential)
        ctx

        (nil? provider)
        (do (log/warn "Credential rejected: no provider for issuer"
                      (pr-str (:iss (auth/unverified-claims credential))))
            ctx)

        (error/anomaly? claims)
        (do (log/warn "Credential rejected:" (:message (error/payload claims)))
            ctx)

        :else
        (assoc-in ctx [:request :auth-claims] claims))))})

(defn- claim-scopes
  "The scopes a claims map grants through the standard claims: the
  space-separated `scope`, and Keycloak's realm roles."
  [claims]
  (let [{:keys [scope]} claims]
    (into #{}
          (filter string?)
          (concat (when (string? scope)
                    (remove str/blank? (str/split scope #"\s+")))
                  (get-in claims [:realm_access :roles])))))

(def claims->scopes
  {:name ::claims->scopes
   :enter (fn [ctx]
            (if-some [claims (get-in ctx [:request :auth-claims])]
              (update-in ctx
                         [:request :auth-scopes]
                         (fnil into #{})
                         (claim-scopes claims))
              ctx))})

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

(defn- refuse-faults
  "Throws when `security` has a fault against the vocabulary `data`
  declares: a route table the chain cannot enforce is a programming error,
  and the service must not start."
  [data security]
  (let [faults (security-faults data security)]
    (when (seq faults)
      ;; nosemgrep: no-raw-throw
      (throw (ex-info (str "Route table declares a security gate the chain "
                           "cannot enforce")
                      (assoc faults
                             :security security
                             :operation (or (get-in data
                                                    [:openapi :operationId])
                                            (:summary data))))))))

(def validate-security
  {:name ::validate-security
   :compile (fn [data _]
              (let [security (get-in data [:openapi :security])]
                (when (seq security) (refuse-faults data security))
                nil))})

(def ^:private default-forbidden-response
  {:status 403
   :body {:title "FORBIDDEN"
          :type "server/forbidden"
          :status 403
          :detail "Insufficient privileges"}})

(defn- satisfied?
  "True when `granted` meets `security` as OpenAPI reads it: some
  requirement object every one of whose schemes has all its scopes granted."
  [security granted]
  (boolean (some (fn [requirement]
                   (every? (fn [[_ scopes]] (every? granted scopes))
                           requirement))
                 security)))

(def require-scopes
  {:name ::require-scopes
   :compile
   (fn [data _]
     (let [security (get-in data [:openapi :security])]
       (when (seq security)
         (refuse-faults data security)
         (let [unauthorized (or (:unauthorized data)
                                default-unauthorized-response)
               forbidden (or (:forbidden data) default-forbidden-response)]
           {:enter (fn [ctx]
                     (let [{:keys [auth-claims auth-scopes]} (:request ctx)]
                       (cond (nil? auth-claims)
                             (sc/terminate ctx unauthorized)
                             (satisfied? security (or auth-scopes #{}))
                             ctx
                             :else
                             (sc/terminate ctx forbidden))))}))))})
