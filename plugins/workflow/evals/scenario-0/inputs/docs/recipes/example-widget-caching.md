# Widget caching

## Problem

You want to cache an expensive widget lookup without serving stale or
leaked data.

## Solution

We cache widget lookups behind a small cache component, invalidated on
every write, with an unconditional TTL as a backstop.

## Rules

**MUST:**

- Cache widget lookups behind `widget-cache/get`.
- Invalidate the cache entry via `widget-cache/evict!` on every write
  to that widget.
- Set a TTL on every cache entry; never cache indefinitely.

**MUST NOT:**

- Read directly from the widget store when a cached value is
  available.
- Cache a widget lookup that depends on request-scoped auth context.
