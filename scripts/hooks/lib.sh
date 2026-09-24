#!/usr/bin/env bash
# The pre-commit steps as shell functions. This repository's pre-commit
# sources this file and runs them in order; a workspace built on these
# bricks sources the same file from its own hook and puts its own steps
# around them, in its own order. Nothing here runs on source.

# Staged Clojure files, one per line: added, copied or modified, and
# still in the tree. A plugin's evals carry fixture sources that are
# not this repository's code, so they are left out.
hook_staged_clojure_files() {
  git diff --cached --name-only --diff-filter=ACM \
    | grep -E '\.(clj|cljc|cljs)$' \
    | grep -Ev '(^|/)evals/' \
    | while read -r f; do
        if [ -f "$f" ]; then
          echo "$f"
        fi
      done
}

# Format the given files in place and restage them: lay out cond pairs with the
# script beside this file, then zprint.
hook_format() {
  echo "Formatting Clojure files..."
  echo "$1" | xargs bb "$(dirname "${BASH_SOURCE[0]}")/cond_pairs.clj"
  echo "$1" | xargs clojure -M:format/zprint '{:search-config? true}' -w
  echo "$1" | xargs git add
}

# Lint the given files with clj-kondo. A lint error fails the commit.
hook_lint() {
  echo "Linting Clojure files..."
  echo "$1" | xargs clojure -M:lint/clj-kondo --lint
}

# Scan the given files with the semgrep rule files named after them. A
# finding fails the commit unless its site carries `nosemgrep: <rule>`.
hook_semgrep() {
  local files=$1
  shift
  local configs=()
  for c in "$@"; do
    configs+=(--config "$c")
  done
  echo "Scanning with semgrep..."
  echo "$files" | xargs semgrep "${configs[@]}" --error --disable-version-check
}
