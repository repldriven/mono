# System configurations

<!-- tessl-plugin: design -->

## Problem

You want to write or modify a system configuration that assembles and
runs a system.

## Solution

We use a YAML (or EDN) file that declares which registered components
from each brick are wired together to form a running system. A base
loads the file via `env/config`, the result is fed into donut.system,
and `system/start` brings everything up in dependency order.

### File layout

System configurations live alongside the brick or base they correspond
to. Conventionally:

```
bases/<base>/resources/<base>/application.yml              ; runtime
bases/<base>/test-resources/<base>/application-test.yml
components/<brick>/test-resources/<brick>/application-test.yml
```

Loaded by classpath URL — e.g. `classpath:realworld-api/application.yml`.

### Top-level shape

A configuration has a top-level `system:` key whose entries are groups,
each matching the keyword group passed to `defcomponents` — see
[system-components.md](system-components.md). Each group's entries name
components within it.

Adapted from `bases/realworld-api/resources/realworld-api/application.yml`:

```yaml
system:
  jdbc:
    datasource: !system/component
      system/component-kind: jdbc/datasource
      dbtype: postgres
      host: !env REALWORLD_DB_HOST
      port: !env REALWORLD_DB_PORT
      database: !env REALWORLD_DB_NAME
      user: !env REALWORLD_DB_USER
      password: !env REALWORLD_DB_PASSWORD

  migrator:
    migrations: !system/component
      system/component-kind: migrator/migrations
      datasource: !system/ref jdbc.datasource
      changelogs:
        - realworld-store/changelog.sql

  realworld:
    store: !system/component
      system/component-kind: realworld-store/store
      datasource: !system/ref migrator.migrations
      signer: !system/ref auth.jwt

  server:
    handler: !system/required-component
    interceptors: !system/component
      system/component-kind: server/interceptors
      store: !system/ref realworld.store
    jetty-adapter: !system/component
      system/component-kind: server/jetty-adapter
      handler: !system/local-ref handler
      interceptors: !system/local-ref interceptors
      options:
        port: !long [!or [!env REALWORLD_PORT, 8091]]
```

### Tag literals

The reader resolves these tag literals into donut.system data
structures at load time.

- **`!system/component`** — declares a component instance. Requires a
  `system/component-kind` key whose value names a registered kind (e.g.
  `server/interceptors`).
- **`!system/ref <group>.<component>`** — cross-group reference.
  Resolves to the started instance of that component.
- **`!system/local-ref <component>`** — reference to a component in the
  same group.
- **`!system/required-component`** — declares a slot that must be
  injected by the bootstrap (typically an HTTP `handler`). See
  "Required-component injection" below.
- **`!profile`** — selects a value (or whole subtree) by the active
  profile, via aero.
- **`!include <path>`** — inlines another YAML file at this point.
  Useful for splitting large configurations.
- **`!env <NAME>`** — reads an environment variable.
- **`!or`, `!long`, `!keyword`, `!str`, `!join`, `!concat`** — aero's
  and this reader's value tags: a default for a missing variable, a
  string coerced to a number or keyword, strings joined. `!long` and
  `!keyword` coerce another tag's value when it is their one-item
  sequence, since YAML allows no tag on a tagged scalar:
  `!keyword [!or [!env SMTP_SECURITY, starttls]]`.
- **`!strs`** — forces string keys on the subtree below. Used when a
  config map's keys must be strings.

### Required-component injection

Some components — most often HTTP handlers — can't be declared in YAML
because they are function values produced by code. The YAML declares
the slot:

```yaml
server:
  handler: !system/required-component
```

The base's bootstrap injects the value before starting:

```clojure
(nom-> (env/config "classpath:realworld-api/application.yml" :dev)
       system/defs
       (assoc-in [:system/defs :server :handler] api/app)
       system/start)
```

### Profile selection

`!profile` picks a value or subtree by the active profile (`:dev`,
`:test`, `:prod`). Aero resolves at load time:

```yaml
options:
  port: !profile
    dev: 8080
    default: 0
```

Whole groups can be profile-gated — for example swapping
testcontainers-backed infrastructure in dev/test for production config
in prod:

```yaml
postgres: !profile
  default:
    container: !system/component
      system/component-kind: postgres/container
      exposed-port: 5432
    container-mapped-exposed-port: !system/component
      system/component-kind: postgres/container-mapped-exposed-port
      container: !system/local-ref container
      exposed-port: 5432
  prod: {}
```

### Starting and stopping

The bootstrap pattern (in a base's `main.clj`):

```clojure
(nom-> (env/config config-file profile)
       system/defs
       (assoc-in [:system/defs :server :handler] api/app)
       system/start)
```

- `env/config` loads the YAML, resolving `!profile`, `!include`, `!env`,
  and the system tag literals.
- `system/defs` lifts the parsed config into a donut system map.
- `assoc-in` injects any required-component slots.
- `system/start` walks the dependency graph in reverse-topsort and
  starts each component.

`system/stop` walks topsort and stops them.

At the REPL, the same calls reach the same code through the base's
`main/start`:

```clojure
(comment
  (def sys (main/start "classpath:realworld-api/application-test.yml"
                       :dev))
  (tap> sys)
  (main/stop sys))
```

`tap>` ships the started system to Portal for inspection.

## Rules

**MUST:**

- A system YAML has a top-level `system:` key.
- Each component instance under a group uses `!system/component` with
  a `system/component-kind` value naming a registered kind.
- `!system/required-component` slots are injected by the bootstrap
  before `system/start` is called.
- Wrap a tagged scalar another tag coerces in a one-item sequence —
  `!keyword [!or [!env SMTP_SECURITY, starttls]]` — since YAML allows
  no tag on a tagged scalar.
- Force string keys on a subtree with `!strs` where a config map's
  keys must be strings.

**MUST NOT:**

- Reference an unregistered component kind — `system/defs` refuses it
  as `:system/unknown-component-kind` before anything starts.
- Reference another component by bare string where a `!system/ref` is
  intended; the resolver will not promote strings to refs.

**SHOULD:**

- Split large configurations into per-group sub-files via `!include`.
- Profile-gate testcontainers infrastructure groups so tests and prod
  can share the same top-level configuration.
- Keep a system configuration beside the brick or base it belongs to —
  `resources/<base>/application.yml` at runtime,
  `test-resources/<name>/application-test.yml` for tests — loaded by
  classpath URL.

**MAY:**

- Write a system configuration in EDN; the reader and the tag literals
  are the same.
- Inline a test configuration's testcontainer groups rather than
  `!include` them, where no shared `test-resources` brick exists.

## Discussion

YAML was chosen for the human-friendliness of the form — operators and
reviewers can read a system shape without knowing EDN. The reader
supports EDN equivalently if a project prefers it; the tag literals are
the same.

The `!include` pattern keeps individual files manageable. Including
per-group files at the top level lets each group's configuration evolve
independently. The example's test configuration inlines its
testcontainer groups instead, because the shared `test-resources` brick
is not published and a generated workspace has none of it.

Required-component injection exists because HTTP handlers (and similar
function-valued components) can't be declared in data — they're code.
Treating the slot as data and injecting at bootstrap is the cleanest
seam between the data-driven system definition and the procedural
bootstrap.

The reader is implemented in the `env` and `system` bricks; the
[systems-as-data slides](../../slides/systems-as-data/slides.md) walk
through the assembly mechanics in detail.

## References

- [ADR-0007](../../adr/0007-system-as-data.md) — System as data
- [system-components.md](system-components.md)
- [bases.md](bases.md)
- [donut.system](https://github.com/donut-party/system)
- [aero](https://github.com/juxt/aero)
- [Systems-as-data slides](../../slides/systems-as-data/slides.md)
