(ns com.repldriven.mono.server.interface
  (:require
    [com.repldriven.mono.server.system]

    [com.repldriven.mono.server.actuator :as actuator]
    [com.repldriven.mono.server.core :as core]
    [com.repldriven.mono.server.cors :as cors]
    [com.repldriven.mono.server.interceptors :as interceptors]
    [com.repldriven.mono.server.streaming :as streaming]))

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
  "Interceptor that verifies `:credential` as a JWT through an identity
  provider on the request, and puts its claims under `:auth-claims`.

  One provider or several: among `:identity-providers`, when the request
  carries them, the one whose issuer the token's unverified `iss` names is
  asked, the signature and issuer being checked by that provider all the
  same; otherwise `:identity-provider` is asked directly, so an API that
  keeps one provider on the request for its own calls still verifies
  against the realm that issued the token. `:expected-audiences`, also
  from the request, is
  the collection the token's `aud` must intersect; absent, any audience is
  accepted. The `server/interceptors` component is what puts them there.

  Sets nothing when there is no credential, when no provider answers to
  the issuer, or when the token is not accepted — logging a warning for
  the last two — and never terminates the request. Runs after
  `credential`.

  A credential that is something else — an opaque token, or a session
  looked up in a store — takes a resolver of your own to the same contract:
  read `:credential`, set `:auth-claims`, never terminate."
  interceptors/authenticate-with-provider)

(def claims->scopes
  "Interceptor that puts the scopes `:auth-claims` grants under
  `:auth-scopes`, a set of strings, from the standard claims: the
  space-separated `scope` (RFC 9068) and Keycloak's `realm_access.roles`.
  Adds to any scopes already there, so a resolver of your own — one that
  reads a membership, say — puts its scopes under the same key before or
  after it. Sets nothing without claims, and never terminates. Runs after
  a resolver has set `:auth-claims`."
  interceptors/claims->scopes)

(def require-scopes
  "Interceptor that enforces an operation's OpenAPI security against
  `:auth-scopes`, terminating with a 401 when no resolver has set
  `:auth-claims` and a 403 when the scopes granted do not meet the gate.

  The gate is read when the router is built, from the operation's merged
  `:openapi :security`, as OpenAPI reads it: the requirement objects are
  alternatives, and within one every scheme's scopes are all required, so
  `[{\"bearerAuth\" [\"admin\"]} {\"bearerAuth\" [\"org:viewer\"]}]`
  admits either while `[{\"bearerAuth\" [\"admin\" \"org:viewer\"]}]`
  demands both. A scheme with no scopes is met by any authenticated caller.
  An operation with no security, or an empty one, is public and gets no
  interceptor at all. A gate this interceptor could not enforce is refused
  while the router is built, as `validate-security` refuses it, and against
  the same `:scopes` and `:exclusive-scopes`.

  The responses are the route data's `:unauthorized` and `:forbidden`, read
  when the router is built, so an API with its own error contract sets them
  once at the root of its routes; without them each is an RFC-9457 body."
  interceptors/require-scopes)

(def require-auth
  "Interceptor that terminates with a 401 unless a resolver has set
  `:auth-claims` on the request.

  The response is the route data's `:unauthorized`, read when the router
  is built, so an API with its own error contract sets it once at the root
  of its routes; without one it is a 401 with an RFC-9457 body."
  interceptors/require-auth)

(def validate-security
  "Interceptor that refuses, while the router is built, an operation whose
  OpenAPI security the chain cannot enforce. It compiles to nothing, so no
  request sees it: reitit runs an interceptor's `:compile` once per
  operation with that operation's merged route data, which is where a gate
  is readable and where a fault in the route table belongs.

  An operation with no `:openapi :security`, or an empty one, is public and
  passes. Otherwise the security must be requirement objects — maps of a
  scheme name to the scopes it requires — and is checked against what the
  route data declares, usually once at the root of the routes:

  - `:scopes`, the set of scope names a principal can carry. Declared,
    every scheme an operation names must name at least one scope, and
    every scope named must be in the set. Left out, a scheme with no
    scopes is the ordinary gate of an API that enforces presence alone.
  - `:exclusive-scopes`, sets of scopes of which an operation may name at
    most one. Reitit concatenates a method's `:security` onto its route's
    unless the method's vector is marked `^:replace`, so a scope declared
    under a method stacks on the route's and the operation admits both.

  Refuses by throwing `ex-info`, since a route table the service cannot
  enforce is a programming error and the service must not start. The
  ex-data carries each fault — `:malformed`, `:no-scopes`,
  `:unknown-scopes`, `:exclusive` — with what caused it, the `:security`
  as merged, and the `:operation`, its `operationId` or its summary."
  interceptors/validate-security)

(def standard-router-data core/standard-router-data)
(def standard-executor core/standard-executor)
(def default-exception-handlers core/default-exception-handlers)

(defn router-data
  ([] (core/router-data))
  ([exception-handlers] (core/router-data exception-handlers)))

(defn standard-default-handler [] (core/standard-default-handler))

(defn standard-openapi-handler
  "Ring handler serving the router's OpenAPI document. A response's own
  `:openapi` data — its `headers`, a `Location` on a 201, and its
  `links` — is merged into its Response Object, which reitit builds from
  `:description` and `:content` alone and would otherwise leave out."
  []
  (core/standard-openapi-handler))

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

(defn streaming-body
  "A Ring response body that holds the response open for as long as
  `write` runs, handing it the response's output stream, and closes the
  stream once `write` returns. For a response written as it happens
  rather than all at once, such as a server-sent event stream.

  Args:
  - write: a function of one argument, the `java.io.OutputStream`. What
    it returns is not read."
  [write]
  (streaming/body write))
