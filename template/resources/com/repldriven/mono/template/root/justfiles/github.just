# Fetch origin and cut a branch off the latest origin/main. name: a bare branch name.
[positional-arguments]
gh-fresh-branch name:
    #!/usr/bin/env zsh
    set -e
    NAME="${1#name=}"
    if [[ ! "$NAME" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*$ ]]; then
      echo "not a bare branch name: '$NAME'"
      exit 1
    fi
    if [[ -n "$(git status --short)" ]]; then
      echo "The working tree is not clean. Commit or discard first:"
      git status --short
      exit 1
    fi
    git fetch origin --prune
    for REF in "refs/heads/$NAME" "refs/remotes/origin/$NAME"; do
      if git rev-parse --verify --quiet "$REF" >/dev/null; then
        echo "branch $NAME already exists (${REF#refs/}). Switch to it instead:"
        echo "  git switch $NAME"
        exit 1
      fi
    done
    # origin/main, never main: main may be checked out in another worktree,
    # and git refuses to check out one branch twice.
    git checkout -b "$NAME" origin/main
    git fetch --quiet origin main:main 2>/dev/null || true
    git log --oneline -3

# Commit everything, push, and open a PR against main or add to its open one. title, body: the PR's; body may span lines.
[positional-arguments]
gh-commit-and-pr title body="":
    #!/usr/bin/env zsh
    set -e
    TITLE="$1"
    BODY="$2"
    if [[ -z "$TITLE" ]]; then
      echo "usage: just gh-commit-and-pr <title> [body]"
      exit 1
    fi
    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    if [[ "$BRANCH" == "main" ]]; then
      echo "On main. Cut a branch first: just gh-fresh-branch <name>"
      exit 1
    fi
    FILES=$(git status --porcelain --untracked-files=all | sed -E 's/^.{3}//; s/^.* -> //')
    if [[ -z "$FILES" ]]; then
      echo "Nothing to commit."
      exit 1
    fi
    SECRETS=$(echo "$FILES" \
      | grep -E '(^|/)(\.env(\..+)?|credentials[^/]*|[^/]+\.(key|pem|pfx|p12))$' \
      | grep -vE '(^|/)\.env\.example$' || true)
    if [[ -n "$SECRETS" ]]; then
      echo "Refusing to commit what looks like a credential:"
      echo "$SECRETS"
      exit 1
    fi
    git add -A
    ARGS=(-m "$TITLE")
    if [[ -n "$BODY" ]]; then
      ARGS+=(-m "$BODY")
    fi
    git commit --quiet "${ARGS[@]}"
    # -u origin HEAD, never a bare push: gh-fresh-branch leaves the upstream
    # at origin/main, which a bare push would aim the work at.
    git push -u origin HEAD
    URL=$(gh pr view --json url,state --jq 'select(.state == "OPEN") | .url' 2>/dev/null || true)
    if [[ -n "$URL" ]]; then
      echo "Pushed to $URL"
      exit 0
    fi
    gh pr create --base main --head "$BRANCH" --title "$TITLE" --body "$BODY"

# Squash-merge the current branch's PR, bypassing the review rule as org admin.
gh-merge:
    #!/usr/bin/env zsh
    set -e
    PR=$(gh pr view --json number,baseRefName,state,title)
    NUM=$(jq -r .number <<<"$PR")
    BASE=$(jq -r .baseRefName <<<"$PR")
    STATE=$(jq -r .state <<<"$PR")
    if [[ "$STATE" != "OPEN" ]]; then
      echo "PR #$NUM is $STATE, nothing to merge."
      exit 1
    fi
    if [[ "$BASE" != "main" ]]; then
      echo "PR #$NUM targets '$BASE', not main. Retarget it before merging:"
      echo "  gh pr edit $NUM --base main"
      exit 1
    fi
    echo "Merging #$NUM — $(jq -r .title <<<"$PR")"
    gh pr merge "$NUM" --squash --admin
    echo "Merged #$NUM into main."

# Delete this owner's orphan container versions from GHCR, multi-arch safe.
gh-prune-untagged:
    #!/usr/bin/env zsh
    set -e
    SCOPES=$(gh api --include / 2>&1 | awk -F': ' 'tolower($1)=="x-oauth-scopes"{print $2}')
    if [[ "$SCOPES" != *delete:packages* ]]; then
      echo "gh token missing delete:packages. Run:"
      echo "  gh auth refresh -h github.com -s delete:packages,read:packages"
      exit 1
    fi
    OWNER=$(gh repo view --json owner --jq '.owner.login')
    TYPE=$(gh api "/users/$OWNER" --jq '.type' 2>/dev/null || echo "User")
    if [[ $TYPE == "Organization" ]]; then
      DELETE_BASE="/orgs/$OWNER/packages/container"
    else
      DELETE_BASE="/user/packages/container"
    fi
    gh api "/users/$OWNER/packages?package_type=container" --paginate \
        --jq '.[].name' | while read -r PKG; do
      # A tagged index's per-platform manifests are themselves untagged, so
      # the keep set comes first: deleting one breaks the index.
      # The registry takes the name as-is, the REST API needs it
      # percent-encoded, or a namespaced package 404s as "not found".
      ENC="${PKG//\//%2F}"
      REGTOKEN=$(curl -sL "https://ghcr.io/token?service=ghcr.io&scope=repository:$OWNER/$PKG:pull" | jq -r .token)
      KEEP=$(gh api "/users/$OWNER/packages/container/$ENC/versions" --paginate \
              --jq '.[] | select(.metadata.container.tags != []) | .name' \
              | while read -r DIGEST; do
                  curl -sL -H "Authorization: Bearer $REGTOKEN" \
                    -H "Accept: application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.list.v2+json" \
                    "https://ghcr.io/v2/$OWNER/$PKG/manifests/$DIGEST" \
                    | jq -r '.manifests[]?.digest // empty'
                done | sort -u)
      TO_DELETE=$(gh api "/users/$OWNER/packages/container/$ENC/versions" --paginate \
                    --jq '.[] | select(.metadata.container.tags == []) | "\(.id) \(.name)"' \
                  | while read -r ID NAME; do
                      echo "$KEEP" | grep -qFx "$NAME" || echo "$ID"
                    done)
      N=$(echo -n "$TO_DELETE" | grep -c '^' || true)
      if [[ $N -eq 0 ]]; then
        printf "  %-45s 0\n" "$PKG"
        continue
      fi
      printf "  %-45s %s" "$PKG" "$N"
      for ID in ${(f)TO_DELETE}; do
        gh api -X DELETE "$DELETE_BASE/$ENC/versions/$ID" --silent || true
        printf "."
      done
      printf "\n"
    done
