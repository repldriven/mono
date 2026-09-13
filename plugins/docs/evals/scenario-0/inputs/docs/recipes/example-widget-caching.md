# Widget caching

## Problem

You want to read a widget without hitting the record store every
time.

## Solution

We keep a per-widget in-memory cache in front of the record store.

```mermaid
sequenceDiagram
  participant Caller
  participant Cache
  participant Store
  Caller->>Cache: get-widget
  Cache->>Store: load (on miss)
  Store-->>Cache: widget
  Cache-->>Caller: widget
```

## References

- [system-components.md](system-components.md) — component
  registration pattern the cache uses
