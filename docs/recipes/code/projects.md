# Projects

<!-- tessl-plugin: framework -->

## Problem

You want to assemble a Polylith project — pick the components and bases
that go into a deployable, or into a library another workspace depends
on.

## Solution

A project under `projects/` is a deployable assembly: a `deps.edn`
listing components and bases as `:local/root` deps. It MAY also have a
`resources/` folder for deployment-scoped resources. No source code; no
`-main` (bases handle that).

### File layout

```
projects/<project-name>-service/
  deps.edn
  resources/                  ; deployment-scoped
    application.yml           ; the system definition the base loads
    logback.xml               ; production log config
    logback-test.xml          ; test log config
```

For a deployable service, the system YAML the base loads at startup is
on the classpath — under the project's `resources/`, or under the base's
own `resources/<base>/` as `realworld-api` keeps it — and the runtime
names it with `--config-file` and `--profile`, as `just realworld-hurl`
does. See [system-configurations.md](system-configurations.md).

### deps.edn pattern

A project's `deps.edn` has three sections:

- **`:deps`** — components and bases as `:local/root` paths, plus any
  project-level pins (the Clojure version, a driver the bricks stay
  agnostic of).
- **`:aliases :build`** — pulls in the `build` base and sets exec-args
  for `tools.build` to assemble the deployable.
- **`:aliases :test`** — the bricks only tests need (`test-resources`,
  `test-system`, `testcontainers`), plus the test runner,
  `external-test-runner`.

Adapted from `projects/realworld-service/deps.edn`:

```clojure
{:paths ["resources"]
 :deps {components/auth {:local/root "../../components/auth"}
        components/jdbc {:local/root "../../components/jdbc"}
        ;; ... (every component the project ships)
        components/realworld-store
        {:local/root "../../components/realworld-store"}
        bases/realworld-api {:local/root "../../bases/realworld-api"}

        ;; Project-level pins
        org.postgresql/postgresql {:mvn/version "42.7.13"}
        org.clojure/clojure {:mvn/version "1.12.5"}}

 :aliases
 {:test {:extra-deps
         {components/test-resources
          {:local/root "../../components/test-resources"}
          components/test-system
          {:local/root "../../components/test-system"}
          components/testcontainers
          {:local/root "../../components/testcontainers"}
          bases/external-test-runner
          {:local/root "../../bases/external-test-runner"}}}
  :build {:deps {io.github.clojure/tools.build {:mvn/version "0.10.12"}}
          :extra-deps {bases/build {:local/root "../../bases/build"}}
          :ns-default com.repldriven.mono.build.build
          :exec-args {:lib com.repldriven.mono/realworld-service
                      :main com.repldriven.mono.realworld-api.main}}}}
```

### Library projects

`mono-lib` and `mono-test-lib` are projects with no base: a `deps.edn`
another workspace names by `:deps/root` in a git dependency, so that
tools.deps pulls the components transitively. They carry no `:build`
alias, an empty `:paths`, and dep keys qualified with the workspace top
namespace so a consumer's own `components/cache` key cannot clash. Both
ship under one lib symbol, and `mono-test-lib` is a superset of
`mono-lib`; the comments in the two files say why, and the release
workflow asserts it.

### Library pinning

A version that several bricks or projects must agree on is declared
once. A project-level pin in `:deps` holds it for that deployable; a
shim directory under `deps/`, referenced by `:local/root` under a
`pin/` key, holds it for every project that references the shim — the
same shape a downstream workspace's `ext/mono` shims take for this
repository's coordinate.

A pin only controls a version if the coordinate reaches the resolver at
the depth it needs. Pinning *up* (holding a library above what something
else asks for) works unaided, because the resolver's tie-break at equal
depth takes the newer version. Pinning *down* does not: the pin's copy
sits one level below a direct dependency and loses, so the competing
copy has to be excluded at the point it enters.

`org.clojure/clojure` is the exception that cannot be shimmed at all.
The CLI merges its own root `deps.edn` into every project's `:deps`, so
Clojure is always a direct dependency and a coordinate one level down
never competes. A project that drops the pin does not inherit the
workspace version — it silently takes whatever Clojure the caller's CLI
ships. Every project therefore repeats `org.clojure/clojure`.

## Rules

**MUST:**

- Projects live in `projects/`.
- A project contains a `deps.edn`.
- Projects use `:local/root` paths for components and bases.
- A project that produces a deployable artefact has a `:build` alias
  pointing at `bases/build`.
- A project's `:test` alias carries the bricks only tests need —
  `test-resources`, `test-system`, `testcontainers` — and the test
  runner, `external-test-runner`.
- A library project carries no `:build` alias, an empty `:paths`, and
  dep keys qualified with the workspace top namespace, so a consumer's
  own key cannot clash.
- `mono-test-lib` is a superset of `mono-lib`; the release workflow
  asserts it.
- A library project keeps `logback-test.xml` off `:paths` and on the
  `:test` alias's `:extra-paths`: a library's `:paths` join every
  consumer's classpath, and logback prefers a `logback-test.xml` found
  anywhere on it.
- A version several bricks or projects must agree on is declared
  once: a project-level pin in `:deps` holds it for one deployable,
  and a shim under `deps/`, referenced by `:local/root` under a `pin/`
  key, holds it for every project that references the shim.
- Every project repeats the `org.clojure/clojure` pin: the CLI makes
  Clojure a direct dependency, so nothing one level down can hold it.
- Profiles (`:dev`, `:test`, `:prod`) are encoded inside the system
  YAML with `aero` `!profile` tags, not as separate files.

**MUST NOT:**

- Projects contain Clojure source code.
- Projects define a `-main` (bases do).
- Projects depend on other projects.
- Pin a library *down* without excluding the newer copy where it
  enters; a pin one level below a direct dependency loses.

**MAY:**

- Projects have a `resources/` folder for deployment-scoped resources:
  `application.yml` (the system definition), `logback.xml` and
  `logback-test.xml`.
- A deployable's system YAML sit under the base's own
  `resources/<base>/` rather than the project's `resources/`; the
  runtime names it with `--config-file` and `--profile`.
- A project have no base, when it is a library another workspace
  consumes by `:deps/root`.

## Discussion

Projects exist so the same components can be assembled into different
deployables, and so a set of components can be published as one
dependency. Keeping projects code-free has two benefits. First, a
project review reads like a deployment manifest, not a programming
exercise. Second, library version pins live in one obvious place per
deployable — a component never has to know which project it's running
in.

The project's `resources/` folder is on the classpath at runtime. For a
deployable service it holds the system definition the base loads at
startup, `logback.xml` for production log config, and
`logback-test.xml` for tests. Profiles (`:dev`, `:test`, `:prod`) are
encoded inside the YAML via `aero` `!profile` tags, not through separate
files. A library project keeps `logback-test.xml` off `:paths` and on
the `:test` alias's `:extra-paths`, because a library's `:paths` become
part of every consumer's classpath and logback prefers a
`logback-test.xml` found anywhere on it.

## References

- [ADR-0007](../../adr/0007-system-as-data.md) — System as data
- [bases.md](bases.md)
- [components.md](components.md)
- [system-components.md](system-components.md)
- [system-configurations.md](system-configurations.md)
- [Polylith documentation](https://polylith.gitbook.io/polylith)
