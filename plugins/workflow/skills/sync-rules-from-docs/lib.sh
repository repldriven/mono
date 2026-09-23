#!/usr/bin/env bash
# The extractors extract.sh and prepare.sh share: what a recipe's Rules
# block and an ADR's Decision contribute to a rule. Sourced, never run.

# --- Extract one recipe'"'"'s Rules block -------------------------------------

extract_recipe() {
  local doc="$1"
  awk '
    /^## Rules[[:space:]]*$/ { in_rules = 1; next }
    in_rules && /^## / { in_rules = 0 }
    in_rules { print }
  ' "$doc"
}

# --- Extract one ADR'"'"'s Decision lead (+ colon-intro'"'"'d list if present) ---

extract_adr() {
  local doc="$1"
  local raw
  raw=$(awk '
    /^## Decision[[:space:]]*$/ { in_decision = 1; next }
    in_decision && /^## / { exit }
    in_decision { print }
  ' "$doc")
  [ -z "$raw" ] && return
  # A Decision made of `###` subsections has no single lead paragraph:
  # print it whole, subheadings included, and leave the judgment of
  # which subsections are normative to the reader.
  if printf '%s' "$raw" | grep -q '^### '; then
    printf '%s\n' "$raw"
    return
  fi
  # Line-by-line state machine, not paragraph mode: a numbered/bulleted
  # list item can have a blank-line-separated continuation paragraph
  # (loose-list markdown) without the list actually ending there — e.g.
  # ADR-0005's item 2 has an indented continuation before item 3. Stay
  # in the list through any blank-line gap followed by either another
  # list marker or indented content; stop only at unindented,
  # non-list-marker content (or a code fence, or end of input).
  printf '%s' "$raw" | awk '
    BEGIN { state = "pre" }
    state == "pre" {
      if ($0 == "") { next }
      state = "lead"
    }
    state == "lead" {
      if ($0 == "") { state = "gap"; next }
      print; next
    }
    state == "gap" {
      if ($0 == "") { next }
      if ($0 ~ /^([0-9]+\.|[-*]) /) { print; state = "list"; next }
      if ($0 ~ /:$/) { print; state = "list"; next }
      exit
    }
    state == "list" {
      if ($0 == "") { state = "listgap"; next }
      print; next
    }
    state == "listgap" {
      if ($0 == "") { next }
      if ($0 ~ /^([0-9]+\.|[-*]) /) { print ""; print; state = "list"; next }
      if ($0 ~ /^[[:space:]]/) { print ""; print; state = "list"; next }
      exit
    }
  '
}

# --- Extract one ADR'"'"'s whole Decision, for prepare.sh to compare ---------

adr_decision() {
  local doc="$1"
  awk '
    /^## Decision[[:space:]]*$/ { in_decision = 1; next }
    in_decision && /^## / { exit }
    in_decision { print }
  ' "$doc"
}
