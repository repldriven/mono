(ns com.repldriven.mono.testcontainers.interface
  (:require
    com.repldriven.mono.testcontainers.system.core
    [com.repldriven.mono.testcontainers.container :as container]))

(defn reuse?
  "Whether a container component's config asks for its container to be
  reused across boots: `reuse` is a literal `true`, or the string
  `!env TESTCONTAINERS_REUSE_ENABLE` supplies, `true`, `1` or `yes`
  in any case. For a container component built outside this brick,
  so it reads the flag the same way.

  Args:
  - config: the component's config map, with `:reuse`."
  [config]
  (container/reuse? config))
