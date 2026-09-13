#!/usr/bin/env bash
# Cross-references docs/recipes/*/*.md + docs/adr/*.md labels
# (`<!-- tessl-plugin: <name> -->` in the front matter, between the
# title and the first section) against a rule file's existing `See
# [...]` links, for one named
# plugin. Deterministic, no judgment — locates and reports, doesn't
# decide anything (same discipline as extract.sh).
#
# Reports two finding types:
#   unlinked   — a doc labeled for this plugin with no rule section
#                referencing it. New-section candidate.
#   mismatched — a doc currently linked from a rule section but labeled
#                for a different plugin, or not labeled at all. Drift
#                candidate — the doc's label or the rule's link is
#                stale; which one is a human call.
#
#   bash discover.sh <plugin-name> [rule-file]
#   e.g. bash discover.sh idioms plugins/idioms/rules/idioms.md
#        (default rule-file: plugins/<plugin-name>/rules/<plugin-name>.md)
#
# A third mode, independent of any one plugin: `discover.sh --unlabeled`
# lists every docs/recipes/*/*.md + docs/adr/*.md with no tessl-plugin
# label at all. This is what backs CLAUDE.md's claim that every recipe
# and ADR is already distilled into some plugin's rule — run it after
# adding a new recipe/ADR to catch one that slipped through unlabeled.

set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

# --- A doc's label, if any -------------------------------------------------

# Anywhere in the front matter -- after the title, before the first
# section -- rather than on a fixed line. A markdown formatter puts a
# blank line after a heading, so a label pinned to line 2 moves to line
# 3 the first time the file is saved in an editor. A parser reading only
# line 2 then finds nothing and reports it as unlabelled, which is
# indistinguishable from a doc nobody has labelled -- so the doc leaves
# the discovery it was written to be found by, silently, for being
# formatted.
doc_label() {
  local doc="$1"
  awk '
    NR == 1 { next }
    /^#/ { exit }
    NR > 8 { exit }
    {
      if (match($0, /tessl-plugin:[[:space:]]*[a-z0-9_-]+/)) {
        s = substr($0, RSTART, RLENGTH)
        sub(/^tessl-plugin:[[:space:]]*/, "", s)
        print s
        exit
      }
    }
  ' "$doc"
}

if [ "${1:-}" = "--unlabeled" ]; then
  unlabeled_count=0
  for f in docs/recipes/*/*.md docs/recipes/*.md docs/adr/*.md; do
  # A chapter index is navigation, not a labelled doc.
  [ "$(basename "$f")" = "readme.md" ] && continue
    [ -f "$f" ] || continue
    if [ -z "$(doc_label "$f")" ]; then
      echo "unlabeled: $f"
      unlabeled_count=$((unlabeled_count + 1))
    fi
  done
  [ "$unlabeled_count" -eq 0 ] && echo "unlabeled: none"
  exit 0
fi

PLUGIN="${1:-}"
if [ -z "$PLUGIN" ]; then
  echo "Usage: discover.sh <plugin-name> [rule-file]" >&2
  echo "       discover.sh --unlabeled" >&2
  exit 1
fi
RULE_FILE="${2:-plugins/$PLUGIN/rules/$PLUGIN.md}"
RULE_DIR="$(dirname "$RULE_FILE")"

[ -f "$RULE_FILE" ] || { echo "No such rule file: $RULE_FILE" >&2; exit 1; }

# --- Every recipe/ADR labeled for this plugin ------------------------------

LABELED=()
for f in docs/recipes/*/*.md docs/recipes/*.md docs/adr/*.md; do
  # A chapter index is navigation, not a labelled doc.
  [ "$(basename "$f")" = "readme.md" ] && continue
  [ -f "$f" ] || continue
  [ "$(doc_label "$f")" = "$PLUGIN" ] && LABELED+=("$f")
done

# --- Every doc currently linked from the rule file, with its section ------
# (reuses the same section-splitting / See-block shape as extract.sh)

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

awk -v outdir="$WORKDIR" '
  /^## / {
    n++
    file = outdir "/" n ".section"
    print $0 > file
    next
  }
  n > 0 { print > (outdir "/" n ".section") }
' "$RULE_FILE"

extract_see_block() {
  local file="$1"
  awk '
    /^See / { collecting = 1 }
    collecting { print; if ($0 ~ /\.$/) exit }
  ' "$file"
}

declare -A LINKED_SECTION   # doc path -> section title

for f in "$WORKDIR"/*.section; do
  [ -f "$f" ] || continue
  title=$(head -1 "$f")
  see_block=$(extract_see_block "$f")
  [ -z "$see_block" ] && continue

  links=$(printf '%s\n' "$see_block" | grep -oE '\]\([^)]+\)' | tr -d '][()')
  while IFS= read -r rel; do
    [ -z "$rel" ] && continue
    doc_dir="$(cd "$RULE_DIR/$(dirname "$rel")" 2>/dev/null && pwd)" || continue
    doc="$doc_dir/$(basename "$rel")"
    doc="${doc#$REPO_ROOT/}"
    [[ "$doc" == docs/recipes/* || "$doc" == docs/adr/* ]] || continue
    LINKED_SECTION["$doc"]="$title"
  done <<< "$links"
done

# --- Report -----------------------------------------------------------------

echo "Plugin: $PLUGIN"
echo "Rule file: $RULE_FILE"
echo

unlinked_count=0
for f in "${LABELED[@]:-}"; do
  [ -z "$f" ] && continue
  if [ -z "${LINKED_SECTION[$f]:-}" ]; then
    echo "unlinked: $f (labeled '$PLUGIN', no rule section links it)"
    unlinked_count=$((unlinked_count + 1))
  fi
done
[ "$unlinked_count" -eq 0 ] && echo "unlinked: none"

echo

mismatched_count=0
for doc in "${!LINKED_SECTION[@]}"; do
  label=$(doc_label "$doc")
  if [ "$label" != "$PLUGIN" ]; then
    shown="${label:-<none>}"
    echo "mismatched: $doc (linked from '${LINKED_SECTION[$doc]}', labeled '$shown')"
    mismatched_count=$((mismatched_count + 1))
  fi
done
[ "$mismatched_count" -eq 0 ] && echo "mismatched: none"

# Findings are report content, not a shell-level failure — this script
# always exits 0 once it has successfully run, the same convention as
# extract.sh's ERROR lines. A non-zero exit above (e.g. the last `[ ]`
# test evaluating false) must not leak out as this script's own status.
exit 0
