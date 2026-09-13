# Document widget cache invalidation

## Background

`inputs/docs/recipes/example-widget-caching.md` is a recipe doc.

## Task

1. In the Solution section, add a paragraph explaining that the
   widget cache is invalidated whenever the underlying record
   changes, and rebuilt on next read — mention in passing that the
   checksum logic used to detect a changed record lives in
   `checksums.md`.
2. Add a note to the existing mermaid sequence diagram describing
   what happens on a cache miss: the cache misses, and the value is
   rebuilt from the store.
3. Add `checksums.md` to the References section, following the
   existing entry's pattern.

Edit `inputs/docs/recipes/example-widget-caching.md` in place.
