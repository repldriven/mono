(ns com.repldriven.mono.keycloak.core
  "Keycloak Admin REST + token endpoint glue. The `KeycloakIdentity
  Provider` defrecord (in `identity_provider`) wraps these helpers
  and implements the `identity-provider` brick's protocol — the
  same way `pulsar/message-bus.clj` wraps the raw Pulsar SDK to
  satisfy `message-bus`'s Producer/Consumer protocols."
  (:require
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.utility.interface :as util]))

(def jwks-ttl-ms (* 10 60 1000))

;; Internal accessor protocol — `KeycloakIdentityProvider` extends it
;; so these helpers can pull config / cached-token atoms off the
;; record without depending on its field shape.
(defprotocol Client
  (-config [_])
  (-admin-token-atom [_])
  (-jwks-atom [_]))

(defn- realm-url
  [{:keys [base-url realm]} & path-parts]
  (apply str base-url "/realms/" realm path-parts))

(defn- admin-url
  [{:keys [base-url realm]} & path-parts]
  (apply str base-url "/admin/realms/" realm path-parts))

(defn parse-token-response
  "Pull `{:access-token :expires-in}` out of a Keycloak token
  response. Returns nil on malformed input."
  [body]
  (when (and (map? body) (:access_token body))
    {:access-token (:access_token body)
     :expires-in (or (:expires_in body) 60)}))

(defn parse-jwks
  "Pass-through that exists so callers can route through one place
  if Keycloak's JWKS response shape ever needs translation."
  [body]
  (when (and (map? body) (sequential? (:keys body)))
    body))

(defn admin-token-expired?
  "Return true if `cached` is nil or older than 80 % of its lifetime."
  [cached now-ms]
  (or (nil? cached)
      (let [{:keys [expires-in fetched-at]} cached
            age-ms (- now-ms fetched-at)
            threshold-ms (* 0.8 1000 (or expires-in 60))]
        (>= age-ms threshold-ms))))

(defn jwks-stale?
  "Return true if cached JWKS is older than the configured TTL."
  [cached now-ms ttl-ms]
  (or (nil? cached)
      (>= (- now-ms (:fetched-at cached)) ttl-ms)))

(defn new-client-representation
  "Build the Keycloak ClientRepresentation JSON body for a per-tenant
  service-account client. `audience` is the realm-level client-scope
  name to attach, so every JWT this client mints carries the right
  `aud` claim. Returns plain Clojure data ready for JSON encoding."
  [{:keys [bank-id name audience]}]
  {:clientId bank-id
   :name (or name bank-id)
   :enabled true
   :protocol "openid-connect"
   :publicClient false
   :serviceAccountsEnabled true
   :standardFlowEnabled false
   :directAccessGrantsEnabled false
   :implicitFlowEnabled false
   :attributes {"access.token.lifespan" "3600"}
   :defaultClientScopes (cond-> ["service-accounts"]
                                audience
                                (conj audience))
   :optionalClientScopes []
   :description audience})

(defn- exchange-client-credentials*
  "Config-only variant: hit the token endpoint without needing a built
  client. Used both by the public exchange flow and by the admin-token
  refresh path."
  [config {:keys [client-id client-secret scope]}]
  (let-nom>
    [res (http/request
          {:method :post
           :url (realm-url config "/protocol/openid-connect/token")
           :headers {"content-type" "application/x-www-form-urlencoded"}
           :body (cond-> (str "grant_type=client_credentials"
                              "&client_id=" client-id
                              "&client_secret=" client-secret)
                         scope
                         (str "&scope=" scope))})
     body (http/res->edn res)]
    body))

(defn exchange-client-credentials
  "POST `client_credentials` to the realm token endpoint. Returns the
  raw Keycloak response body (snake-case keys preserved) or an
  anomaly."
  [client creds]
  (exchange-client-credentials* (-config client) creds))

(defn- fetch-admin-token
  [config]
  (let-nom>
    [body (exchange-client-credentials*
           config
           {:client-id (:admin-client-id config)
            :client-secret (:admin-client-secret config)})]
    (or (some-> (parse-token-response body)
                (assoc :fetched-at (util/now)))
        (error/fail :keycloak/admin-token-malformed
                    {:message
                     "Keycloak admin token response missing access_token"}))))

(defn- admin-token!
  "Return a valid admin access token, refreshing if expired."
  [client]
  (let [config (-config client)
        a (-admin-token-atom client)
        cached @a]
    (if-not (admin-token-expired? cached (util/now))
      (:access-token cached)
      (let [fresh (fetch-admin-token config)]
        (if (error/anomaly? fresh)
          fresh
          (do (reset! a fresh) (:access-token fresh)))))))

(defn- admin-headers
  [token]
  {"authorization" (str "Bearer " token)
   "content-type" "application/json"})

(defn create-client
  "Create a Keycloak client for `bank-id`. Returns
  `{:client-id …}` (the secret is fetched separately via
  `client-secret`) or an anomaly."
  [client {:keys [bank-id name audience]}]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       _ (http/request
          {:method :post
           :url (admin-url config "/clients")
           :headers (admin-headers token)
           :body (json/write-str
                  (new-client-representation
                   {:bank-id bank-id
                    :name name
                    :audience audience}))})]
      {:client-id bank-id})))

(defn client-secret
  "Fetch the current client_secret for a given Keycloak clientId. The
  Admin API requires the Keycloak UUID, not the clientId — we look it
  up first."
  [client client-id]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       list-res (http/request
                 {:method :get
                  :url (admin-url config "/clients?clientId=" client-id)
                  :headers (admin-headers token)})
       clients (http/res->edn list-res)
       uuid (some-> clients
                    first
                    :id)
       _ (when-not uuid
           (error/reject :keycloak/client-not-found
                         {:message "No Keycloak client matches client-id"
                          :client-id client-id}))
       sec-res (http/request
                {:method :get
                 :url (admin-url config "/clients/" uuid "/client-secret")
                 :headers (admin-headers token)})
       sec (http/res->edn sec-res)]
      {:client-id client-id :client-secret (:value sec)})))

(defn delete-client
  "Delete the Keycloak client matching `client-id`. Idempotent: a
  404 is treated as success (already gone)."
  [client client-id]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       list-res (http/request
                 {:method :get
                  :url (admin-url config "/clients?clientId=" client-id)
                  :headers (admin-headers token)})
       clients (http/res->edn list-res)
       uuid (some-> clients
                    first
                    :id)]
      (if uuid
        (let-nom>
          [_ (http/request
              {:method :delete
               :url (admin-url config "/clients/" uuid)
               :headers (admin-headers token)})]
          {:client-id client-id})
        {:client-id client-id}))))

(defn regenerate-secret
  "Rotate the client_secret for a given Keycloak clientId."
  [client client-id]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       list-res (http/request
                 {:method :get
                  :url (admin-url config "/clients?clientId=" client-id)
                  :headers (admin-headers token)})
       clients (http/res->edn list-res)
       uuid (some-> clients
                    first
                    :id)
       _ (when-not uuid
           (error/reject :keycloak/client-not-found
                         {:message "No Keycloak client matches client-id"
                          :client-id client-id}))
       res (http/request
            {:method :post
             :url (admin-url config "/clients/" uuid "/client-secret")
             :headers (admin-headers token)})
       sec (http/res->edn res)]
      {:client-id client-id :client-secret (:value sec)})))

(defn update-client-audience
  "Point the Keycloak client matching `client-id` at `audience`: replace
  its `defaultClientScopes` with `[\"service-accounts\" audience]` — the
  same shape `new-client-representation` builds at creation. Fetches
  the current `ClientRepresentation` (needed to preserve every other
  field on the PUT) and swaps just that one list, so this is
  target-state idempotent — a redelivered call converges on the same
  result."
  [client client-id audience]
  (let [config (-config client)]
    (let-nom>
      [token (admin-token! client)
       list-res (http/request
                 {:method :get
                  :url (admin-url config "/clients?clientId=" client-id)
                  :headers (admin-headers token)})
       clients (http/res->edn list-res)
       representation (first clients)
       _ (when-not representation
           (error/reject :keycloak/client-not-found
                         {:message "No Keycloak client matches client-id"
                          :client-id client-id}))
       _ (http/request
          {:method :put
           :url (admin-url config "/clients/" (:id representation))
           :headers (admin-headers token)
           :body (json/write-str
                  (assoc
                   representation
                   :defaultClientScopes
                   (cond-> ["service-accounts"] audience (conj audience))))})]
      {:client-id client-id})))

(defn- fetch-jwks
  [config]
  (let-nom>
    [res (http/request {:method :get
                        :url (realm-url config
                                        "/protocol/openid-connect/certs")})
     body (http/res->edn res)]
    (or (some-> (parse-jwks body)
                (assoc :fetched-at (util/now)))
        (error/fail :keycloak/jwks-malformed
                    {:message "Keycloak JWKS response missing :keys"}))))

(defn jwks!
  "Return cached JWKS, refreshing if stale. If `force-refresh?` is
  truthy, bypass the cache (used when a `kid` is unknown)."
  ([client] (jwks! client false))
  ([client force-refresh?]
   (let [config (-config client)
         a (-jwks-atom client)
         cached @a]
     (if (and (not force-refresh?)
              (not (jwks-stale? cached (util/now) jwks-ttl-ms)))
       cached
       (let [fresh (fetch-jwks config)]
         (if (error/anomaly? fresh)
           fresh
           (do (reset! a fresh) fresh)))))))

(defn issuer
  "Configured issuer URL for the realm."
  [client]
  (realm-url (-config client)))
