# Bases

<!-- tessl-plugin: framework -->

## Problem

You want to add or modify a Polylith base.

## Solution

A base under `bases/` is an application entry point. It owns the
`-main` function, parses CLI args, builds the system definition, injects
any required handlers, and starts the system. Each runnable application
has exactly one base.

### File layout

```
bases/<base-name>/
  src/com/repldriven/mono/<base-name>/
    main.clj    ; -main entry point and bootstrap
    ...         ; (often) interceptors, handlers, route definitions
  resources/<base-name>/application.yml   ; (a deployable) the system
  test/...
  deps.edn
```

### main.clj

The base's `main.clj` does three things:

1. Bare-requires every brick whose system multimethods need to be
   extended at startup.
2. Defines `start` — builds the system definition from a YAML config,
   injects any `!system/required-component` slots, and calls
   `system/start`.
3. Defines `-main` to parse CLI args and call `start`.

Adapted from `bases/realworld-api/src/.../main.clj`:

```clojure
(ns com.repldriven.mono.realworld-api.main
  (:require
    [com.repldriven.mono.auth.interface]
    [com.repldriven.mono.command.interface]
    [com.repldriven.mono.command-processor.interface]
    [com.repldriven.mono.jdbc.interface]
    [com.repldriven.mono.message-bus.interface]
    [com.repldriven.mono.migrator.interface]
    [com.repldriven.mono.realworld-store.interface]
    [com.repldriven.mono.server.interface]

    [com.repldriven.mono.realworld-api.api :as api]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom->]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(defn start
  [config-file profile]
  (nom-> (env/config config-file profile)
         system/defs
         (assoc-in [:system/defs :server :handler] api/app)
         system/start))

(defn stop [system] (system/stop system))

(defn -main
  [& args]
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "realworld-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            sys (start config-file (keyword profile))]
        (if (error/anomaly? sys)
          (cli/exit false
                    (str "Failed to start [" (error/kind sys) "]: "
                         (or (:message (error/payload sys))
                             "Unknown error")))
          (do (log/info "realworld-api started")
              @(promise)))))))
```

`(:gen-class)` exposes `-main` as a Java entry point. `@(promise)`
blocks the main thread so the JVM stays alive while the background
components run. A base with no HTTP surface — `service`, the generic
command-handler entry point — has the same shape minus the `assoc-in`.

### Accessing components

Bases reach components through `interface.clj`, never internal
namespaces — the same rule as for components themselves:

```clojure
;; OK
[com.repldriven.mono.server.interface :as server]
```

Bases never depend on other bases. If two bases need to share code,
that code belongs in a component.

### What a base does not own

A base owns no persistence. `component → base` is not a dependency
Polylith allows, so a store parked behind an entry point is unreachable
by every component, and the day one needs it the store has to move
first. Registering a storage brick's component kinds by bare-requiring
its interface from the base is registration, not ownership.

## Rules

**MUST:**

- Bases live in `bases/`.
- A base has a `-main` function in its entry namespace and uses
  `(:gen-class)`.
- Bases access components via `interface.clj`.
- Bases bare-require every brick whose system multimethods need to
  extend at runtime.

**MUST NOT:**

- Bases depend on other bases.
- Bases share code with each other except through components.
- A base own a store. Persistence belongs in a component.

## Discussion

Bases are the runnable parts of the system. The split between "base
provides `-main` and bootstrap" and "project picks the components" lets
the same code run in different deployments. Here that is
`realworld-api`, the HTTP entry point of the example, and `service`,
which hosts command processors and nothing else; `build` and
`external-test-runner` are bases in Polylith's classification too,
entered from the command line rather than a deployment. See
[projects.md](projects.md) for how a base becomes a deployable.

The no-base-depends-on-base rule keeps the dep graph clean. If two
bases need shared logic, hoisting it into a component is the right
move; the alternative is a lattice of base-on-base deps that loses the
one-entry-point-per-artefact property. A workspace that composes
several bases into one process — for local development, or an
end-to-end test rig — does it through one designated aggregator that
reaches each composed base by a declared surface, and writes that
convention down as its own; nothing here composes bases.

The bare-require list in `main.clj` looks ugly but is load-bearing:
each entry extends the donut.system multimethods the system definition
needs at startup. Forgetting one means the system fails to start with a
"no method found" error. Tests, by contrast, can consolidate these into
a single `test/.../system.clj` — see
[system-components.md](system-components.md).

## References

- [ADR-0007](../../adr/0007-system-as-data.md) — System as data
- [components.md](components.md)
- [projects.md](projects.md)
- [system-components.md](system-components.md)
- [Polylith documentation](https://polylith.gitbook.io/polylith)
