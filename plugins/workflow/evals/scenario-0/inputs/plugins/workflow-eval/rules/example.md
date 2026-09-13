# Example idioms (eval fixture)

## Cache widget lookups through the cache component

Cache widget lookups via `widget-cache/get` and evict on every write.
Widget caches must be sharded per-region for latency.
See [example-widget-caching](../../../docs/recipes/example-widget-caching.md).
