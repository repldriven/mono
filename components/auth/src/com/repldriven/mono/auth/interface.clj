(ns com.repldriven.mono.auth.interface
  "Password hashing, JWT signing, and the HTTP interceptors that turn a
  credential into claims on the request.

  Two things are deliberately not decided here. The Authorization scheme is
  a set rather than a constant, because `Token` and `Bearer` are both in
  common use and RealWorld uses the former. And the response for a missing
  credential is a parameter, because its body belongs to the API being
  served rather than to authentication — see `require-auth`.

  Everything fails as an anomaly. Verification failures are
  `:unauthorized/anomaly`, so they can be told apart from a hashing fault
  without inspecting a message: a caller presenting a bad token is an
  ordinary condition, a signer with no secret is not."
  (:require
    com.repldriven.mono.auth.system

    [com.repldriven.mono.auth.interceptors :as interceptors]
    [com.repldriven.mono.auth.password :as password]
    [com.repldriven.mono.auth.token :as token]))

(def default-ttl-seconds
  "Token lifetime used when a signer does not set `:ttl-seconds`."
  token/default-ttl-seconds)

(def default-schemes
  "Authorization schemes accepted by default, lower-cased."
  interceptors/default-schemes)

(def default-credential-key
  "Request key `credential-interceptor` sets when `opts` names none."
  interceptors/default-credential-key)

(defn hash-password
  "Hash a plaintext password for storage, or return an anomaly.

  The result carries its own salt and parameters, so it is the only value
  that needs storing.

  Args:
  - plain: the plaintext password. Must be a non-empty string."
  [plain]
  (password/derive-hash plain))

(defn verify-password
  "True if `plain` matches `hashed`.

  Returns false rather than an anomaly for every failure, including a
  malformed stored hash: the question asked is whether these match, and
  nothing that is not a match should read as one.

  Args:
  - plain: the plaintext password offered.
  - hashed: the stored hash, as produced by `hash-password`."
  [plain hashed]
  (password/verify plain hashed))

(defn sign-token
  "Sign `claims` into a JWT (HS256), or return an anomaly.

  `iat` and `exp` are set from the signer's `:ttl-seconds`; any values for
  them in `claims` are overwritten.

  Args:
  - signer: an `auth/signer` instance — `{:secret ... :ttl-seconds ...}`.
  - claims: a map of claims. Prefer a stable user id as `:sub` over a
    username, which can change and would invalidate a live token."
  [signer claims]
  (token/sign signer claims))

(defn verify-token
  "Verify a JWT and return its claims, or an `:unauthorized/anomaly`.

  A bad signature, a malformed token and an expired one are all the same
  answer — not accepted — and are not distinguished in the anomaly.

  Args:
  - signer: an `auth/signer` instance.
  - jwt-string: the encoded token, without any scheme prefix."
  [signer jwt-string]
  (token/verify signer jwt-string))

(defn header->token
  "The credential out of an `Authorization` header value, or nil when the
  header is absent, malformed, or uses a scheme not in `schemes`.

  Args:
  - header: the raw header value, e.g. `\"Token abc.def.ghi\"`.
  - schemes: a set of accepted lower-cased scheme names."
  [header schemes]
  (interceptors/header->token header schemes))

(defn credential-interceptor
  "Interceptor that puts the `Authorization` credential on the request under
  `:credential`, its scheme stripped, so every interceptor and handler after
  it reads one key rather than parsing the header itself.

  Does not reject when the header is absent, malformed or of a scheme not in
  `:schemes` — it sets nothing. `token-interceptor` is this plus
  verification, for a credential that is a JWT; an API whose credential is
  something else, a session looked up in a store say, follows this with an
  interceptor of its own that resolves the key.

  Args:
  - opts: `{:schemes #{\"bearer\"} :credential-key :credential}`, both
    optional. Schemes default to `default-schemes`."
  ([] (interceptors/credential-interceptor))
  ([opts] (interceptors/credential-interceptor opts)))

(defn token-interceptor
  "Interceptor that verifies an `Authorization` credential and assocs its
  claims onto the request under `:auth-claims`.

  Does not reject when the credential is absent or invalid — it simply
  sets nothing, which is what endpoints with optional authentication need.
  Pair it with `require-auth` where a credential is mandatory.

  Args:
  - signer: an `auth/signer` instance, or a function of the request that
    returns one — a keyword works, so `:signer` reads it off the request.
    Interceptors are built before a started component can reach them, so
    the indirection is what lets a route be wired without threading the
    component through routing.
  - opts: `{:schemes #{\"token\"} :claims-key :auth-claims}`, both optional."
  ([signer] (interceptors/token-interceptor signer))
  ([signer opts] (interceptors/token-interceptor signer opts)))

(defn require-auth
  "Interceptor that terminates with `response` unless `token-interceptor`
  has set claims.

  Args:
  - response: the Ring response to terminate with. Defaults to a 401 with
    an RFC-9457 body; pass your own when the API has its own error shape.
  - opts: `{:claims-key :auth-claims}`, optional."
  ([] (interceptors/require-auth))
  ([response] (interceptors/require-auth response))
  ([response opts] (interceptors/require-auth response opts)))
