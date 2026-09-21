(ns com.repldriven.mono.server.interface
  (:require
    [com.repldriven.mono.server.system]

    [com.repldriven.mono.server.actuator :as actuator]
    [com.repldriven.mono.server.core :as core]
    [com.repldriven.mono.server.cors :as cors]
    [com.repldriven.mono.server.interceptors :as interceptors]))

(def require-idempotency-key
  "Interceptor that validates the `Idempotency-Key` header is present
  and syntactically well-formed (16-255 URL-safe ASCII chars)."
  interceptors/require-idempotency-key)

(def credential
  "Interceptor that puts the `Authorization` credential on the request under
  `:credential`, its scheme stripped, so what follows reads one key rather
  than parsing the header itself. Accepts the `Token` and `Bearer` schemes,
  case-insensitively.

  Sets nothing when the header is absent, malformed or of another scheme,
  and never terminates the request. A resolver turns the credential into
  `:auth-claims` after it — `authenticate-with-signer`,
  `authenticate-with-provider`, or one of your own — and `require-auth`
  refuses a request that has none."
  interceptors/credential)

(def authenticate-with-signer
  "Interceptor that verifies `:credential` as a JWT against the `auth/signer`
  under `:signer` on the request, and puts its claims under `:auth-claims`.
  The `server/interceptors` component is what puts the signer there.

  Sets nothing when there is no credential or no signer, or the token is
  not accepted, and never terminates the request: an endpoint where
  authentication is optional needs exactly that, and one that requires it
  says so with `require-auth`. Runs after `credential`."
  interceptors/authenticate-with-signer)

(def authenticate-with-provider
  "Interceptor that verifies `:credential` as a JWT through the identity
  provider under `:identity-provider` on the request, and puts its claims
  under `:auth-claims`. `:expected-audiences`, also from the request, is
  the set the token's `aud` must intersect; absent, any audience is
  accepted. The `server/interceptors` component is what puts both there.

  Sets nothing when there is no credential or no provider, or the token is
  not accepted, and never terminates the request. Runs after `credential`.

  A credential that is something else — an opaque token, or a session
  looked up in a store — takes a resolver of your own to the same contract:
  read `:credential`, set `:auth-claims`, never terminate."
  interceptors/authenticate-with-provider)

(def require-auth
  "Interceptor that terminates with a 401 unless a resolver has set
  `:auth-claims` on the request.

  The response is the route data's `:unauthorized`, read when the router
  is built, so an API with its own error contract sets it once at the root
  of its routes; without one it is a 401 with an RFC-9457 body."
  interceptors/require-auth)

(def standard-router-data core/standard-router-data)
(def standard-executor core/standard-executor)
(def default-exception-handlers core/default-exception-handlers)

(defn router-data
  ([] (core/router-data))
  ([exception-handlers] (core/router-data exception-handlers)))

(defn standard-default-handler [] (core/standard-default-handler))

(defn standard-openapi-handler [] (core/standard-openapi-handler))

(defn standard-openapi-ui-handler [] (core/standard-openapi-ui-handler))

(defn http-local-url
  "Get the local HTTP URL from a Jetty Server instance."
  [server]
  (core/http-local-url server))

(defn health-routes
  "Spring Boot Actuator-style health endpoints:
  - `/actuator/health/liveness` always returns `{\"status\":\"UP\"}` (200).
  - `/actuator/health/readiness` returns UP (200) or DOWN (503)
    based on `(:ready-fn ctx)`.
  - `/actuator/health` aggregates the two groups.
  `ready-fn` comes from the API ctx — the jetty-adapter system
  component threads it through, defaulting to `(constantly true)`
  for services without a startup dependency. Adapter services
  pass an atom-backed readiness component (jetty-adapter coerces
  any IDeref to a thunk) so readiness stays DOWN until webhook
  registration succeeds."
  [ctx]
  (actuator/health-routes ctx))

(defn wrap-cors
  "Wrap `handler` so browsers may call it from `:origins`.

  Middleware rather than an interceptor: a preflight arrives as OPTIONS on a
  path whose route declares no OPTIONS handler, which the router answers
  with a 404 before any interceptor runs.

  Returns `handler` unchanged when no origins are configured, so the default
  is to permit nothing.

  Args:
  - handler: the Ring handler to wrap.
  - opts: `{:origins [\"http://localhost:3000\"] :methods [...]
    :request-headers [...] :max-age 3600 :credentials? false}`. Only
    `:origins` is required."
  [handler opts]
  (cors/wrap-cors handler opts))
