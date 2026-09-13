(ns com.repldriven.mono.smtp.interface
  "Sends email over SMTP submission with Angus Mail, the Jakarta Mail
  implementation, and renders a message as the RFC 5322 text a mail
  server receives. A message is a map of kebab-case keys, an address a
  string or `{:address :name}`. Nothing here throws: a request the
  brick declines is `error/reject` with `:smtp/invalid-address` (with
  `:address` when one failed to parse) or `:smtp/invalid-message` (with
  `:missing`); a failure at the server is `error/fail` with
  `:smtp/authenticate` for a rejected credential or `:smtp/send` for
  anything else the transport raised, a partial acceptance carrying
  `:invalid-addresses`, `:valid-unsent` and `:valid-sent`. The
  `smtp/client` system component owns the mail session, the credential
  and the default from, and opens no connection until a `send`."
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.smtp.system]

    [com.repldriven.mono.smtp.core :as core]))

(defn send
  "Hand `message` to the mail server `client` is configured for, over a
  connection opened for the call and closed after it. Returns
  `{:message-id id :recipients [...]}`, `id` being the Message-ID header
  sent (`<uuidv7@from-domain>`) and the recipients the bare to, cc and
  bcc addresses in that order, or an anomaly: `:smtp/invalid-message`
  or `:smtp/invalid-address` before connecting, `:smtp/authenticate` or
  `:smtp/send` from the server. Logs one info line per call and records
  a span, neither carrying the subject, a body or an address.

  Args:
  - client: an `smtp/client` instance.
  - message: `:to` (required), `:from` (falls back to the client's),
    `:cc`, `:bcc`, `:reply-to`, `:subject` and `:text` (both required),
    `:html` and `:headers` (a map of header name to value)."
  [client message]
  (core/send client message))

(defn render
  "The message as RFC 5322 text, without the Bcc header, as `send` would
  build it: `text/plain` for `:text` alone, `multipart/alternative` with
  the text part first when `:html` is given, subject and display names
  encoded as UTF-8. Returns the text, or the same `:smtp/invalid-message`
  and `:smtp/invalid-address` anomalies as `send`.

  Args:
  - client: optional; its `:from` is used when the message has none.
  - message: the message map `send` takes."
  ([message] (core/render message))
  ([client message] (core/render client message)))
