(ns com.repldriven.mono.scheduler.interface
  "Generic cron scheduler — a thin wrapper over io.factorhouse/cronut
  (Quartz, in-memory). Domain-free: callers register named jobs that fire
  a 0-arg handler on a Quartz cron expression, and start/stop the
  scheduler. The `:scheduler/scheduler` system component owns the
  scheduler's lifecycle; consumers `!system/ref scheduler.scheduler` and
  register triggers against the instance.

  In-memory only (no persistence/clustering) — persist your own job and
  run state, and run a single scheduler instance."
  (:require
    com.repldriven.mono.scheduler.system
    [com.repldriven.mono.scheduler.core :as core]))

(defn start
  "Create and start a scheduler. Returns it; pass to the other fns.
  Normally driven by the `:scheduler/scheduler` system component — call
  directly only outside a system (e.g. tests)."
  []
  (core/start))

(defn stop
  "Shut a scheduler down."
  [scheduler]
  (core/stop scheduler))

(defn schedule!
  "Register (or replace) the job `id` to fire `handler-fn` (0-arg) on the
  Quartz cron expression `cron-expr` (6/7 fields: sec min hour dom month
  dow [year]). Returns `id`.

  Args:
  - scheduler: a started scheduler.
  - id: unique job name (string).
  - cron-expr: Quartz cron expression string.
  - handler-fn: 0-arg fn run on each fire."
  [scheduler id cron-expr handler-fn]
  (core/schedule! scheduler id cron-expr handler-fn))

(defn unschedule!
  "Remove the job `id` (and its trigger). Safe if absent.

  Args:
  - scheduler: a started scheduler.
  - id: the job name to remove."
  [scheduler id]
  (core/unschedule! scheduler id))

(defn next-fire-at
  "Epoch millis of the next time `cron-expr` fires strictly after
  `after-millis` (UTC), or nil if it never fires again. Pure — does not
  need a scheduler, so callers can compute a job's next run without a
  live trigger.

  Args:
  - cron-expr: Quartz cron expression string.
  - after-millis: epoch millis to search after."
  [cron-expr after-millis]
  (core/next-fire-at cron-expr after-millis))
