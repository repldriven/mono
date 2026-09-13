# SMTP

> **Status: proposal.** Nothing of the brick exists. What the design
> reuses — the `system` brick's component kinds, `error`'s `try-nom`,
> the `testcontainers` brick's container helper and its generic
> extractors, `telemetry`'s `with-span`, `http-client` for reading the
> catcher — exists and is named as such in Background. Everything under
> Proposed Solution is the build list, and "The first slice" says what
> comes first.

## Objective

A workspace built on these bricks sends an email — an invitation, a
notice, a message an operator asked for — through the mail server its
installation names, and learns whether the server accepted it. This
TDD says which library does the sending and why, the shape of the
brick that wraps it, the message a caller hands it, the component that
holds the server's configuration, how a credential and the transport
security reach it, the catcher its tests run against, and where the
brick is registered so a consuming workspace receives it.

In scope: the `smtp` brick — its `interface.clj`, the message map,
`send` and `render`, the `smtp/client` component and its config schema,
and the anomalies it returns; the `mailpit` container group in the
`testcontainers` brick and the shared test YAML; registration in
`mono-lib`, `mono-test-lib`, `workspace.edn`, the root `deps.edn` and
the readme; and the tests.

Out of scope: the consumer in a workspace — the event processor that
composes a message from a domain event and calls `send`, its retry
schedule and any outbox — which follows under that workspace's own
design; templates and content; the Gmail API and any provider's HTTP
API; attachments; receiving mail.

## Background

What exists, and what the design copies.

- **The wrapper brick shape.** `scheduler` is a curated wrapper with a
  system component: `deps.edn` names one library, `interface.clj`
  bare-requires `com.repldriven.mono.scheduler.system` in the bracketed
  form and delegates to `core.clj`, which is the only file that touches
  the library and wraps every call in `try-nom` with a
  `:scheduler/<call-site>` category, and `system.clj` registers the
  component with `system/defcomponents`. `http-client` shows the
  conversion of a library's failure convention into `error/fail` with
  the response in the payload.
- **The Kind legend.** The readme's brick table gives every brick a
  Kind: a facade wraps a wide throwing API whole, an abstraction names
  operations rather than a library, and everything else is curated —
  mono's own API using the library inside, covering what mono needs.
- **A credential in config.** `keycloak`'s component defaults its
  secret to `nil` in `:system/config` — never `required-component`,
  never a shared default — and declares it `{:optional true}
  [:maybe string?]` in the Malli config schema, which a test validates
  directly; the YAML supplies it with `!env`. Every password in the
  workspace arrives that way.
- **The three-layer container pattern.** A container technology is a
  `defcomponents` group in the `testcontainers` brick: a container def
  that calls builder methods before `container/start!`, which snapshots
  the mapped ports into the instance, plus the generic
  `mapped-exposed-port` and `uri` extractors re-registered under the
  group's name, as `vault` does. The generic `container` kind takes an
  image name, exposed ports and a startup timeout, and no environment,
  so a container that needs one gets its own def. The shared YAML for a
  group lives in the `test-resources` brick under
  `testcontainers/<group>-test.yml`, and a brick's own
  `application-test.yml` includes it.
- **Test lifecycle.** `with-test-system` boots a system from a
  classpath YAML under a permit and asserts it started; `nom-test>`
  asserts a step returned no anomaly; a test may pass a function that
  patches the defs before start.
- **The guardrails.** `no-raw-throw` refuses a bare `throw`;
  `no-raw-time-id` makes `utility` the only caller of the JVM's clock
  and id primitives; `no-use-fixtures` refuses `use-fixtures`.
- **How a workspace consumes it.** A workspace sends from an event
  processor on at-least-once delivery, which leaves the event unacked
  when the handler returns an anomaly so the bus redelivers, and has no
  status to read back that says the person was already told. So the
  brick is stateless: it sends once and reports, and deduplication and
  retry are the consumer's.
- **Release visibility.** A brick added to `mono-lib` reaches a
  consumer only at a new tag, and `mono-test-lib` stays a superset of
  `mono-lib`, which the release workflow asserts.

## Proposed Solution

### The library

Angus Mail, the Eclipse reference implementation of the Jakarta Mail
specification: `org.eclipse.angus/angus-mail`, which brings
`jakarta.mail/jakarta.mail-api` and the activation implementation with
it, all in the `jakarta.mail` package namespace. It is declared in
`components/smtp/deps.edn` and nowhere else, as ADR-0011 requires, and
Renovate owns its version.

The brick's Kind is curated. Jakarta Mail's API is wide and throws, so
a facade would wrap sessions, stores, folders and transports that
nothing here reads. The brick's own API is a message map and two
functions, and a caller wanting more requires Angus directly and
declares it in its own `deps.edn`.

### The brick

`components/smtp/`, flat:

```
deps.edn
src/com/repldriven/mono/smtp/interface.clj
src/com/repldriven/mono/smtp/core.clj
src/com/repldriven/mono/smtp/system.clj
test/com/repldriven/mono/smtp/interface_test.clj
test/com/repldriven/mono/smtp/system_test.clj
test-resources/smtp/application-test.yml
```

`interface.clj` carries the ns docstring — what the brick wraps, what
it returns as an anomaly, which component owns the session — and two
functions:

- `send [client message]` — hands the message to the mail server the
  client is configured for. Returns `{:message-id id :recipients
  [...]}` or an anomaly.
- `render [message]` — the message as RFC 5322 text, for a test that
  asserts on structure and for a caller that archives what it sent.

The message map, kebab-case keys as ADR-0006 requires:

- `:from` — a string address, or `{:address "..." :name "..."}`. Falls
  back to the client's configured from.
- `:to`, `:cc`, `:bcc` — vectors of the same shape. `:to` is required.
- `:reply-to` — one address, optional.
- `:subject` — required.
- `:text` — required. `:html` — optional.
- `:headers` — a map of header name to value, optional.

`core.clj` is the only namespace that imports `jakarta.mail.*`. It
parses every address strictly with `InternetAddress`, builds the
`MimeMessage`, and sends through a `Transport` opened for the call and
closed after it. The send runs inside `telemetry/with-span`, and logs
one line at info with the message id, the outcome and the recipient
domains — never the subject, the body or an address.

The anomalies, each carrying `:message`:

- `error/reject :smtp/invalid-address` — an address that does not
  parse, with `:address`. Named for the problem, since the caller can
  fix it.
- `error/fail :smtp/authenticate` — the server rejected the credential,
  from `AuthenticationFailedException` through `try-nom-ex`. Named for
  the problem, since the operator can act.
- `error/fail :smtp/send` — anything else the transport raised, with
  the exception; on a `SendFailedException` also `:invalid-addresses`,
  `:valid-unsent` and `:valid-sent`, so a partial acceptance is
  reported as one.

### The client component

`system.clj` registers `(system/defcomponents :smtp {:client client})`.
Config, with defaults:

- `:host` — `required-component`.
- `:port` — 587.
- `:security` — `:starttls`; or `:tls`, or `:none`.
- `:username` — `nil`. `:password` — `nil`, and `{:optional true}
  [:maybe string?]` in the schema, as `keycloak` declares its secret.
  `mail.smtp.auth` is set when a username is present.
- `:from` — `nil`; a string or `{:address :name}`, the default sender.
- `:connection-timeout-ms` and `:timeout-ms` — 10000 each.
- `:properties` — `{}`, merged last into the `mail.smtp.*` properties,
  for anything the keys above do not name.

Start builds a `jakarta.mail.Session` from those properties and makes
no connection: a session is a bag of properties, and the server is
first contacted on the first `send`. The instance is a map of the
session, the credential and the default from, and the instance schema
checks for the session. Stop does nothing. The Malli config schema is
in `:system/config-schema`, and `system_test.clj` validates it with
`metosin/malli` as a `:test`-only extra-dep, as `keycloak` does.

A production YAML:

```yaml
smtp:
  client: !system/component
    system/component-kind: smtp/client
    host: !env SMTP_HOST
    username: !env SMTP_USERNAME
    password: !env SMTP_PASSWORD
    from:
      address: !env SMTP_FROM_ADDRESS
      name: !env SMTP_FROM_NAME
```

### Security

The `:security` value sets the transport properties:

- `:starttls` — `mail.smtp.starttls.enable` and
  `mail.smtp.starttls.required` true. The connection opens in clear on
  the submission port and is refused unless the server upgrades it.
- `:tls` — `mail.smtp.ssl.enable` true. Encrypted from the first byte,
  on the port the provider reserves for it.
- `:none` — neither. For a catcher, and only where the YAML says so.

`mail.smtp.ssl.checkserveridentity` is true whatever the value.
XOAUTH2 is a property away — `mail.smtp.auth.mechanisms` in
`:properties`, with the token as the password — and is not wired.

### Message identity and encoding

The Message-ID is `<uuidv7@domain>`, the id from `util/uuidv7` and the
domain from the from address, set by a `proxy` of `MimeMessage` that
overrides `updateMessageID`, since `saveChanges` otherwise replaces
the header with Jakarta's own. `send` returns that id, so the caller
holds an identifier it can record and that matches the copy the mail
server and the recipient hold. The Date header is Jakarta's. Subject
and display names are encoded as UTF-8. A message with `:text` alone
is `text/plain`; with `:html` too it is `multipart/alternative`, text
part first, so a client that renders HTML picks the HTML and every
other shows the text.

### The catcher for tests

Mailpit: one image, an SMTP listener and a REST API that returns what
it received. It is a group in the `testcontainers` brick, a
`mailpit.clj` beside `mqtt.clj` and `vault.clj` under
`system/components/`: a `container` def over `GenericContainer` for
`axllent/mailpit`, with
`1025` and `8025` exposed and the environment `MP_SMTP_AUTH`
(`test:test`) and `MP_SMTP_AUTH_ALLOW_INSECURE` set before start, so
the credential path is exercised over cleartext. `system/core.clj`
registers it as:

```clojure
(system/defcomponents :mailpit
                      {:container mailpit/container
                       :container-smtp-port testcontainers/mapped-exposed-port
                       :container-api-port testcontainers/mapped-exposed-port
                       :container-api-url testcontainers/uri})
```

The shared YAML,
`components/test-resources/test-resources/testcontainers/mailpit-test.yml`,
wires the three layers:

```yaml
container: !system/component
  system/component-kind: mailpit/container

container-smtp-port: !system/component
  system/component-kind: mailpit/container-smtp-port
  container: !system/local-ref container
  exposed-port: 1025

container-api-port: !system/component
  system/component-kind: mailpit/container-api-port
  container: !system/local-ref container
  exposed-port: 8025

container-api-url: !system/component
  system/component-kind: mailpit/container-api-url
  port: !system/local-ref container-api-port

client: !system/component
  system/component-kind: smtp/client
  host: localhost
  port: !system/local-ref container-smtp-port
  security: !keyword none
  username: test
  password: test
  from:
    address: noreply@example.test
    name: Example
```

The brick's `test-resources/smtp/application-test.yml` is the two
lines that include it under `smtp`. The interface test reads
`/api/v1/message/latest` from the API URL with `http-client/res->edn`;
`DELETE /api/v1/messages` clears the inbox between assertions.

### Registration and release

- `projects/mono-lib/deps.edn` and `projects/mono-test-lib/deps.edn`:
  `com.repldriven.mono.components/smtp {:local/root
  "../../components/smtp"}`, alphabetical, in both.
- `workspace.edn`: `"smtp"` in `:necessary` for `mono-lib` and
  `mono-test-lib`.
- The root `deps.edn`: `components/smtp` under `:dev`, and
  `components/smtp/test` and `components/smtp/test-resources` under
  `:test`.
- `readme.md`: a `### Mail` section in the brick table with the row
  `smtp` — sending email over SMTP submission, text and HTML —
  `angus-mail` — Curated.
- A tag, since a consumer pins a sha.

### The first slice

1. The brick: `deps.edn`, the three source namespaces, `render` and
   its unit tests, the config schema and its test.
2. The catcher: the `mailpit` group, the shared YAML, the brick's
   `application-test.yml`, and the interface test against it.
3. Registration in both library projects, `workspace.edn`, the root
   `deps.edn` and the readme, with `just test` green.
4. The tag.

A workspace's consumer follows under its own design, against the
pinned tag.

### Tests

- **`render`**, no container: the From, To, Cc, Reply-To and Subject
  headers as given; a UTF-8 subject encoded; an extra header present;
  `text/plain` for text alone and `multipart/alternative` with the
  text part first for text and HTML; `:from` falling back to the
  client's; `:smtp/invalid-address` for an address that does not
  parse, and for a missing `:to`.
- **`send`**, against Mailpit: a text-and-HTML message read back with
  from, to, subject, text and HTML matching, and the returned message
  id equal to the stored Message-ID header; cc and bcc recipients
  delivered; a wrong password, patched in before start, answered with
  `:smtp/authenticate`.
- **The config schema**: the defaults validate; a missing host and an
  unknown `:security` are refused.

## Alternatives Considered

- **postal.** Rejected: its last release is on the `javax.mail` package
  namespace, and a second mail library on the classpath in the
  `jakarta.mail` namespace would sit beside it.
- **tarayo.** Rejected: current and on Angus underneath, but it pulls
  `tika-core`, `camel-snake-kebab` and `nano-id` for a thin layer over
  the same calls, and `nano-id` is a second id source beside
  `utility`.
- **Simple Java Mail.** Rejected: a builder API with DKIM, S/MIME,
  connection pools and clusters the design does not need. The mail
  server signs.
- **The Gmail API.** Rejected: a Google-only brick, a service-account
  key to guard, no local catcher, and a MIME message still to build.
- **A provider's HTTP API.** Rejected for the same reasons, one brick
  per provider.
- **A `mail` abstraction with pluggable senders.** Rejected: it designs
  the seam before a second sender exists. The message map is the seam
  if one is ever needed.
- **Connecting at start.** Rejected: a mail server that is down would
  fail the whole system's boot, and a send that fails is already
  reported.
- **A pooled connection.** Rejected: one connection per send until an
  installation's volume says otherwise.
- **Naming the brick `mail`.** Rejected: it speaks SMTP and nothing
  else, and `mail` is what an abstraction over senders would be called.

## Known Limitations

- **One connection per send.** No pool, no reuse.
- **The encrypted paths are exercised only against a real server.**
  Mailpit runs in clear in the test profile, so `:starttls` and `:tls`
  are proved at an installation, not in `just test`.
- **No retry, queue or outbox.** A refused send is an anomaly to the
  caller.
- **No bounce handling.** A delivery that fails after the server
  accepted the message bounces to the sending mailbox, which nothing
  reads.
- **Provider limits are not enforced.** The operator stays within
  them.
- **No attachments.** Text and HTML only.
- **XOAUTH2 is not wired.** A property, and a token the caller would
  have to obtain.
- **A misconfigured server is found on the first send**, not at start.

## References

- [mail](../prd/mail.md) — Mail, the product requirements this design
  serves.
- [ADR-0005](../adr/0005-error-handling-with-anomalies.md) — Error
  handling with anomalies at interface boundaries.
- [ADR-0006](../adr/0006-kebab-case-keyword-keys.md) — Kebab-case
  keyword keys end-to-end.
- [ADR-0007](../adr/0007-system-as-data.md) — System-as-data via
  donut.system and YAML.
- [ADR-0011](../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library.
- [ADR-0015](../adr/0015-comments-and-docstrings.md) — Comments and
  docstrings.
- [components](../recipes/code/components.md) — the brick shape and
  the `interface.clj` discipline.
- [system-components](../recipes/code/system-components.md) —
  `system/defcomponents` and the component map.
- [system-configurations](../recipes/code/system-configurations.md) —
  the YAML tags the production and test files use.
- [error-handling](../recipes/code/error-handling.md) — the anomaly
  kinds and how a category is named.
- [code-style](../recipes/code/code-style.md) — requires, ids and
  timestamps.
- [testcontainers](../recipes/test/testcontainers.md) — the three-layer
  pattern the catcher follows.
- [test-system](../recipes/test/test-system.md) — `with-test-system`
  and `nom-test>`.
- [Angus Mail](https://eclipse-ee4j.github.io/angus-mail/) — the
  library.
- [Mailpit](https://mailpit.axllent.org/) — the catcher.
- [RFC 5322](https://www.rfc-editor.org/rfc/rfc5322) — Internet Message
  Format.
- [RFC 6409](https://www.rfc-editor.org/rfc/rfc6409) — Message
  Submission for Mail.
