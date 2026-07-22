(ns com.repldriven.mono.cli.interface
  "Standard CLI scaffolding for service `-main` entry points: a
  shared `--config-file` / `--profile` / `--help` option set, an
  arg validator that returns either an exit-message map or a
  parsed-options map, and an exit helper that anomaly-formats
  errors before terminating the JVM."
  (:require
    [com.repldriven.mono.cli.core :as core]))

(defn validate-args
  "Parse `args` against the standard CLI option set. Returns
  either a map with `:exit-message` (and optional `:ok?`) when the
  caller should print and exit, or `{:options …}` with the parsed
  values.

  Args:
  - program-name: name shown in `--help` usage.
  - args: the raw arg seq from `-main`."
  [program-name args]
  (core/validate-args program-name args))

(defn exit
  "Log `msg` and terminate the JVM with exit code 0 (when `ok?`)
  or 1 (otherwise). Anomaly `msg`s are rendered via
  `error/format-anomaly` so kind, payload message, exception, and
  captured stack reach the log.

  Args:
  - ok?: true for success exit, false for failure.
  - msg: a string or an anomaly."
  [ok? msg]
  (core/exit ok? msg))
