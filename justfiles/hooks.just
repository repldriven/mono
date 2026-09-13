# Git hooks: the tracked scripts under scripts/hooks/, and the copies git
# runs. An extensionless file there is a hook, installed under its own
# name; a `.sh` file is a helper a hook sources, and stays where it is.

# Install the git hooks. Silent when the installed copies already match.
install-hooks:
    #!/usr/bin/env zsh
    set -euo pipefail
    # One hooks directory per clone, in the git dir a linked worktree's
    # .git file points back at: one install covers every worktree, and a
    # worktree is not allowed to be what installs them, since its
    # checkout is whatever commit a work item landed on.
    if [[ "$(git rev-parse --git-dir)" != "$(git rev-parse --git-common-dir)" ]]; then
      echo "install-hooks: skipped — hooks belong to the whole clone."
      echo "install-hooks: run it in $(dirname $(git rev-parse --git-common-dir))"
      exit 0
    fi
    for src in scripts/hooks/*; do
      name=$(basename "$src")
      if [[ "$name" == *.* ]]; then
        continue
      fi
      hook=$(git rev-parse --git-path "hooks/$name")
      mkdir -p "$(dirname "$hook")"
      if cmp -s "$src" "$hook"; then
        continue
      fi
      # A copy, not a symlink: editing scripts/hooks/ changes nothing
      # until this runs again.
      cp "$src" "$hook"
      chmod +x "$hook"
      echo "installed $hook"
    done
