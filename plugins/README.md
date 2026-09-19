# Tessl plugins

mono's [Tessl](https://tessl.io) plugins: the rules an agent loads, each
distilled from the ADRs under `docs/adr/` and the recipes under `docs/recipes/`
that carry its `<!-- tessl-plugin: <name> -->` label. A workspace built on these
bricks installs them alongside its own, so the rules travel with the docs they
cite.

- **design** — how a system is built from the bricks: messaging and its
  payloads, system-as-data, testcontainers, code generation.
- **docs** — how to write or check anything under `docs/`: the recipe
  shape, wrap-80, link hygiene, mermaid and tone, and the `check-docs`
  skill that verifies them.
- **framework** — Polylith itself: components, bases, projects, and one
  brick per third-party library.
- **idioms** — portable Clojure conventions: anomalies, kebab-case
  keyword keys, `utility` helpers, require order and style, comments,
  `with-test-system`.
- **workflow** — pulling `main` and letting Renovate own bumps, the
  checked-in git hooks, how a `just` recipe is written, and the
  `sync-rules-from-docs` skill that keeps a rule traceable to its docs.

`plugins/profiles` names which plugins a profile links. `just
tessl-plugins-install` installs every plugin from the working tree in
one pass and lays the active profile down in `.tessl/RULES.md`, and
does nothing where no file under a root has changed since the last
install, which it records as a hash in `.tessl/SOURCES`; `just
tessl-plugins-check` reports an installed copy behind its source.

A workspace that installs these plugins beside its own sets
`TESSL_PLUGIN_ROOTS` to both roots — its `plugins` and the directory it
laid mono's down in — and names both workspaces' plugins in its
`plugins/profiles`. The recipes install and check every root in that
order.
