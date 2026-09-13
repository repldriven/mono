# Lint scoped to what changed. The hook sees one commit's staged files
# and a branch review sees the whole branch; both run the same checks.

# Semgrep rule files, space-separated. A workspace built on these bricks adds its own after mono's.
SEMGREP_CONFIGS := env_var_or_default("SEMGREP_CONFIGS", ".config/semgrep/semgrep.yml")
# Guardrail scripts that take --branch, space-separated. None here.
GUARDRAILS := env_var_or_default("GUARDRAILS", "")

# Scan for semgrep guardrails (no args = all bricks, or pass paths).
semgrep *paths="bases components projects":
    #!/usr/bin/env bash
    set -euo pipefail
    configs=""
    for c in {{ SEMGREP_CONFIGS }}; do
      configs="$configs --config $c"
    done
    semgrep $configs --error --disable-version-check {{ paths }}

# Lint, semgrep- and guardrail-check the branch's Clojure changes against origin/<base>.
lint-branch base="main":
    #!/usr/bin/env bash
    set -euo pipefail
    for g in {{ GUARDRAILS }}; do
      bash "$g" --branch
    done
    # Deletions are excluded by --diff-filter, and `|| true` absorbs
    # grep's empty-match 1: a branch with no Clojure changes passes.
    files=$(git diff --name-only --diff-filter=ACMR origin/{{ base }}...HEAD \
              -- '*.clj' '*.cljc' '*.cljs' \
            | grep -Ev '(^|/)evals/' || true)
    if [ -z "$files" ]; then
      echo "No Clojure files changed against origin/{{ base }}."
      exit 0
    fi
    echo "$files" | xargs clojure -M:lint/clj-kondo --lint
    configs=""
    for c in {{ SEMGREP_CONFIGS }}; do
      configs="$configs --config $c"
    done
    echo "$files" | xargs semgrep $configs --error --disable-version-check
