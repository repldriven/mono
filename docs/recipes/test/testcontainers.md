# Testcontainers

<!-- tessl-plugin: design -->

## Problem

You want to run brick infrastructure (postgres, Kafka, Vault, and so
on) in tests, using the same configuration shape that production uses.

## Solution

We treat testcontainers as a *source* of connection details — mostly
host and port, sometimes a bootstrap address or similar token — not as
a parallel test-only runtime. The high-level components that consume
those details (the Kafka client, the JDBC datasource, the Vault client)
see the same configuration shape regardless of whether the source is a
testcontainer or a literal value in a production YAML.

This means the same brick interfaces work for both deployment modes,
and a `!profile` swap is enough to move between them.

### The three-layer pattern

A testcontainer-backed group has three layers, all declared in YAML,
wired by `!system/local-ref`:

1. **The container itself.** Started by the `testcontainers` brick.
   Builder-pattern setup methods (image name, env vars, startup
   timeout) happen at construction time.
2. **The extractor.** Lives in the *relevant* brick's `system/` folder.
   Reads runtime values (host, port, bootstrap servers) from the
   started container and exposes them.
3. **The high-level component.** The Kafka admin / JDBC datasource /
   Vault client / etc. Consumes the extracted values the same way
   production would consume them from a YAML literal or `!env`.

Adapted from
`components/test-resources/test-resources/testcontainers/kafka-test.yml`:

```yaml
container: !system/component
  system/component-kind: kafka/container
  exposed-ports: [9092]

bootstrap-servers: !system/component
  system/component-kind: kafka/bootstrap-servers
  container: !system/local-ref container

admin: !system/component
  system/component-kind: kafka/admin
  bootstrap-servers: !system/local-ref bootstrap-servers
```

`kafka/container` is declared by the `testcontainers` brick.
`kafka/bootstrap-servers` is declared in
`components/kafka/.../system/components.clj` — the extractor that reads
the bootstrap address off the started container. `kafka/admin` is the
production-shaped admin client; it consumes a `bootstrap-servers` value
and doesn't know or care that the value came from a testcontainer.

The generic extractors — `container-mapped-exposed-port`,
`container-uri` — are the exception that lives in `testcontainers`
itself, because a mapped port is the container library's own
vocabulary and no brick understands it better.

### Production analog

The same `kafka/admin` component runs unchanged in production — it
just gets its `bootstrap-servers` from a literal value or `!env`
instead of an extractor. With profile-gating, both shapes live in the
same configuration:

```yaml
kafka: !profile
  default:                                # dev / test
    container: !system/component
      system/component-kind: kafka/container
      exposed-ports: [9092]
    bootstrap-servers: !system/component
      system/component-kind: kafka/bootstrap-servers
      container: !system/local-ref container
    admin: !system/component
      system/component-kind: kafka/admin
      bootstrap-servers: !system/local-ref bootstrap-servers
  prod:
    admin: !system/component
      system/component-kind: kafka/admin
      bootstrap-servers: !env KAFKA_BOOTSTRAP_SERVERS
```

`admin` doesn't change shape. Neither does any consumer referencing
`kafka.admin` from another group.

### Construction vs runtime

The `testcontainers` brick MAY call builder-pattern setup methods
during container *construction* (`.withVaultToken`, `.addEnv`,
`.withStartupTimeout`). It MUST NOT call library methods on a *running*
container instance to extract runtime values.

If `testcontainers` reached into a started Kafka container to pull a
bootstrap address, it would acquire a hidden dependency on the Kafka
container's API — coupling `testcontainers` to libraries it shouldn't
know about and breaking
[ADR-0011](../../adr/0011-one-component-per-third-party-library.md).

Extraction goes in the relevant brick's `system/` folder because that
brick already legitimately depends on the library. The `kafka` brick
depends on the Kafka client; reading a bootstrap address is a natural
extension of what it does.

### Reuse across boots

A container component takes `reuse`, and a rig sets it from the
variable the library itself reads:

```yaml
container: !system/component
  system/component-kind: mailpit/container
  reuse: !env TESTCONTAINERS_REUSE_ENABLE
```

With the variable set, the first boot marks the container reusable
and every later boot with the same configuration, in this JVM or the
next, finds it running rather than starting another; the component's
stop leaves it, since the library stops a reusable container when
asked. Without the variable the value is nil and nothing changes. A
container carries its state across boots, so a component MUST NOT take
`reuse` unless every rig that shares it keeps its own data apart —
FoundationDB's per-boot keyspace prefix is the model. Kafka takes none:
a rig's topics and consumer groups carry fixed names, and two rigs on
one broker would be one consumer group taking each other's commands.

## Rules

**MUST:**

- Testcontainer-backed infrastructure follows the three-layer pattern:
  container, extractor, high-level component, all declared in YAML and
  wired by `!system/local-ref`.
- Extractor components — those that interrogate a running container —
  live in the relevant brick's `system/` folder.
- High-level components consume extracted values the same way they
  consume production literals; the consumer's shape does not vary by
  deployment.

**MUST NOT:**

- Call library methods on a *started* container instance from the
  `testcontainers` brick, beyond the generic mapped-port and URI
  extractors it owns.
- Give a container component `reuse` unless every rig sharing the
  container keeps its own data apart, since a reused container carries
  its state across boots and runs; Kafka's fixed topic and group names
  rule it out there.
- Make high-level components testcontainer-aware. They should not
  branch on whether they're running against a container or a real
  cluster.

**MAY:**

- Call builder-pattern setup methods on a container instance during
  construction (before start).
- Set a container component's `reuse` from
  `!env TESTCONTAINERS_REUSE_ENABLE`, so one variable both asks the
  library to reuse and tells the component not to stop what it finds.
- Profile-gate whole infrastructure groups so a single configuration
  serves dev/test and prod.

## Discussion

The orthogonality principle is what makes the rest of the testing story
work. If high-level components branched on "am I in a test or in prod,"
every brick would have a test path and a prod path that could quietly
diverge. Forcing testcontainers to look the same as a real deployment
from the consumer's perspective leaves the production code path as the
only code path; tests just feed it different connection details.

The three-layer split is a direct consequence. The `testcontainers`
brick is generic — it knows nothing about what's inside the container.
The extractor is where library knowledge lives, and it lives in the
brick that already has that library knowledge. The high-level component
is library-naïve and works the same in either world.

The `system/` folder pattern — described in
[components.md](../code/components.md) and
[system-components.md](../code/system-components.md) — exists
specifically for this kind of cross-cutting registration: multiple
component kinds related to the same library, one of which interrogates
running infrastructure.

The [systems-as-data slides](../../slides/systems-as-data/slides.md)
walk through the testcontainer construction in more detail.

## References

- [ADR-0007](../../adr/0007-system-as-data.md) — System as data
- [ADR-0011](../../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library
- [components.md](../code/components.md)
- [system-components.md](../code/system-components.md)
- [system-configurations.md](../code/system-configurations.md)
- [test-system.md](test-system.md)
- [Systems-as-data slides](../../slides/systems-as-data/slides.md)
