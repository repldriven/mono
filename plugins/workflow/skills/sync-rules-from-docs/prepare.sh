#!/usr/bin/env bash
# Reports the source docs a rule file links whose normative content the
# extractor would not reach, so they are fixed before a sync rather than
# reported as untraceable after it.
#
# ADRs: deterministic. An ADR's Decision contributes its lead paragraph
# and one colon-introduced list; a paragraph after that list, or between
# the lead and the list, is invisible to extract.sh. Every non-blank
# Decision line the extractor drops is printed with its line number.
#
# Recipes: only the Rules block is distilled, and whether the Solution or
# Discussion states a norm the Rules omit is a reading, not a match.
# Each linked recipe is listed for that reading, with the sentences in
# its Solution, Failures and Discussion that carry a modal (must, never,
# only, always, not) as a starting point -- a hint, never a finding.
#
#   bash prepare.sh [rule-file]   # default: plugins/idioms/rules/idioms.md

set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

RULE_FILE="${1:-plugins/idioms/rules/idioms.md}"
RULE_DIR="$(dirname "$RULE_FILE")"
[ -f "$RULE_FILE" ] || { echo "No such rule file: $RULE_FILE" >&2; exit 1; }

docs=$(grep -oE '\]\((\.\./)+docs/(adr|recipes)/[^)]+\)' "$RULE_FILE" \
         | tr -d '][()' | awk '!seen[$0]++')

echo "Rule file: $RULE_FILE"
echo
unreached=0
while IFS= read -r rel; do
  [ -z "$rel" ] && continue
  doc_dir="$(cd "$RULE_DIR/$(dirname "$rel")" 2>/dev/null && pwd)" || continue
  doc="${doc_dir#$REPO_ROOT/}/$(basename "$rel")"
  if [[ "$doc" == docs/adr/* ]]; then
    whole=$(adr_decision "$doc")
    [ -z "$whole" ] && { echo "ERROR: $doc has no ## Decision section"; continue; }
    if printf '%s' "$whole" | grep -q '^### '; then continue; fi
    reached=$(extract_adr "$doc")
    # Lines the extractor prints, matched by content; a line of the
    # Decision with no such line is one it dropped.
    missing=$(printf '%s\n' "$whole" | grep -v '^[[:space:]]*$' \
                | grep -nvxF -f <(printf '%s\n' "$reached") || true)
    if [ -n "$missing" ]; then
      n=$(printf '%s\n' "$missing" | wc -l | tr -d ' ')
      unreached=$((unreached + 1))
      printf 'unreached: %s -- %s Decision line(s) the extractor drops:\n' "$doc" "$n"
      printf '%s\n' "$missing" | sed 's/^/    /'
      echo
    fi
  elif [[ "$doc" == docs/recipes/* ]]; then
    hints=$(awk '
      /^## (Solution|Failures|Discussion)[[:space:]]*$/ { keep = 1; next }
      /^## / { keep = 0 }
      keep && !/^```/ && !fence { print }
      /^```/ { fence = !fence }
    ' "$doc" | tr '\n' ' ' | sed 's/  */ /g' | tr '.' '\n' \
      | grep -iE '\b(must|never|only|always|not)\b' | sed 's/^ *//' | head -20 || true)
    printf 'read: %s -- Rules against its Solution, Failures and Discussion' "$doc"
    if [ -n "$hints" ]; then
      printf '; sentences carrying a modal:\n'
      printf '%s.\n' "$hints" | sed 's/^/    /'
    else
      printf '\n'
    fi
    echo
  fi
done <<< "$docs"

echo "ADRs with Decision lines the extractor drops: $unreached"
