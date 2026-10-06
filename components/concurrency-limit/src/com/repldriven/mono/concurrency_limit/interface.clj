(ns com.repldriven.mono.concurrency-limit.interface
  "A limit on how much work is in flight at once, found by measuring. A
  limiter doubles its limit while it binds and throughput keeps rising,
  then holds it at the knee — the most throughput measured while the
  limit bound, times the lowest latency measured — with headroom,
  probing above the knee and draining below it one window in eight.
  An integer is a fixed limit that never moves."
  (:require
    [com.repldriven.mono.concurrency-limit.core :as core]
    [com.repldriven.mono.concurrency-limit.options :as options]))

(def options-schema
  "The Malli schema of a `max-in-flight` value: a positive integer, a
  fixed limit, or a map of `:initial`, `:min`, `:max`, `:headroom`,
  `:windows`, `:window-size` and `:window-ms`, each optional, a dynamic
  one. A config schema names it for its `max-in-flight` key."
  options/schema)

(defn limiter
  "A limiter for `opts`, a `max-in-flight` value, or a rejection of
  category `:concurrency-limit/invalid-options` whose `:message` names
  the option at fault.

  An integer `n` is a fixed limit of `n`. A map's options:
  - initial: the limit at start, and the least a search after a quiet
    spell starts from, default 8, or `max` where lower.
  - min: the lowest the limit falls, default 1.
  - max: the highest it rises, which bounds the search, default 1000.
  - headroom: the limit as a multiple of the knee, at least 1,
    default 1.5.
  - windows: how many windows the estimates are taken over, default 10.
  - window-size, window-ms: a window closes once it holds `window-size`
    samples (default 10) and has been open `window-ms` (default 1000)."
  [opts]
  (core/limiter opts))

(defn try-acquire
  "A permit from `limiter`, or nil where the limit is reached. Never
  waits. A refusal marks the window as one in which the limit bound."
  [limiter]
  (core/try-acquire limiter))

(defn acquire
  "A permit from `limiter`, waiting until one is free. Waiting marks
  the window as one in which the limit bound. A thread interrupted
  while it waits gets an anomaly of category `:concurrency-limit/acquire`
  instead, with its interrupt status set again."
  [limiter]
  (core/acquire limiter))

(defn release
  "Free `permit`, recording the time since it was taken as a latency
  sample, and step the limit where the window is full. With
  `:ignored`, as for a handler that threw, free it without recording
  a sample. Returns nil."
  ([permit] (core/release permit))
  ([permit outcome] (core/release permit outcome)))

(defn limit
  "How many permits `limiter` grants at once now."
  [limiter]
  (core/limit limiter))

(defn in-flight
  "How many of `limiter`'s permits are held now."
  [limiter]
  (core/in-flight limiter))

(defn capacity
  "`limiter`'s capacity estimate, in releases a second: the highest
  throughput of the recent windows in which the limit bound, or nil
  where none did."
  [limiter]
  (core/capacity limiter))
