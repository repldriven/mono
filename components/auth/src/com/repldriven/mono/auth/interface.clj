(ns com.repldriven.mono.auth.interface
  "Password hashing, JWT signing and verification, and the parsing of an
  `Authorization` header into the credential it carries.

  The Authorization scheme is a set rather than a constant, because `Token`
  and `Bearer` are both in common use and RealWorld uses the former.

  Everything fails as an anomaly. Verification failures are
  `:unauthorized/anomaly`, so they can be told apart from a hashing fault
  without inspecting a message: a caller presenting a bad token is an
  ordinary condition, a signer with no secret is not."
  (:require
    [com.repldriven.mono.auth.system]

    [com.repldriven.mono.auth.password :as password]
    [com.repldriven.mono.auth.token :as token]))

(def default-ttl-seconds
  "Token lifetime used when a signer does not set `:ttl-seconds`."
  token/default-ttl-seconds)

(def default-schemes
  "Authorization schemes accepted by default, lower-cased."
  token/default-schemes)

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
  (token/header->token header schemes))
