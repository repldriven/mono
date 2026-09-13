# 99. Widget eviction runs oldest-first, never by size

## Status

Accepted.

## Context

The widget cache (see the widget-caching recipe) needs a bounded
eviction policy once it fills up. Evicting the largest entries first
sounds appealing for memory pressure, but widgets vary wildly in size
for reasons unrelated to how often they're read, so a size-first policy
tends to evict hot, expensive-to-recompute widgets purely because they
happen to be large — the opposite of what a cache is for.

## Decision

We will evict the oldest entry first whenever the widget cache is
full, never the largest. The rules:

1. Track insertion order per cache entry; evict the single oldest
   entry when a write would exceed the cache's configured capacity.
2. Never evict based on entry size. Size is not a signal the eviction
   policy considers at all.
3. A cache hit does not refresh an entry's age. Only insertion order
   matters, so a hot entry is evicted at the same age as a cold one.

## Consequences

Easier: eviction behaviour is trivial to reason about and test — no
size accounting, no LRU-touch bookkeeping on reads.

Harder: a large, frequently-read widget can still be evicted purely
because it's old, even though evicting it is expensive to recompute.
Acceptable given the widget cache's TTL already bounds staleness
independently of eviction order.
