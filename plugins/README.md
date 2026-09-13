# Tessl plugins

mono's [Tessl](https://tessl.io) plugins: the rules an agent loads, each
distilled from the ADRs under `docs/adr/` that carry its
`<!-- tessl-plugin: <name> -->` label. A workspace built on these bricks
installs them alongside its own, so the rules travel with the docs they
cite.

- **design** — how a system is built from the bricks: messaging and its
  payloads, system-as-data, code generation.
- **framework** — Polylith itself: one brick per third-party library.
- **idioms** — portable Clojure conventions: kebab-case keyword keys.
- **workflow** — the checked-in git hooks, and the `sync-rules-from-docs`
  skill that keeps a rule traceable to its docs.

`plugins/profiles` names which plugins a profile links. `just
tessl-plugins-install` installs every plugin from the working tree and
lays the active profile down in `.tessl/RULES.md`; `just
tessl-plugins-check` reports an installed copy behind its source.

A workspace that installs these plugins beside its own sets
`TESSL_PLUGIN_ROOTS` to both roots — its `plugins` and the directory it
laid mono's down in — and names both workspaces' plugins in its
`plugins/profiles`. The recipes install and check every root in that
order.
