# Tessl plugins: the rules an agent loads, and the copy it loads them
# from. See plugins/README.md.

# Plugin roots in install order, each holding one directory per plugin.
TESSL_PLUGIN_ROOTS := env_var_or_default("TESSL_PLUGIN_ROOTS", "plugins")
TESSL_PROFILES := "plugins/profiles"

# Install every plugin under each root from the working tree, then re-lay the active profile.
tessl-plugins-install:
    #!/usr/bin/env zsh
    set -e
    for root in {{ TESSL_PLUGIN_ROOTS }}; do
      if [[ ! -d $root ]]; then
        echo "no plugin root $root"
        exit 1
      fi
      for d in $root/*/; do
        tessl install "file:./${d%/}"
      done
    done
    PROFILE=default
    if [[ -f .tessl/PROFILE ]]; then
      PROFILE=$(cat .tessl/PROFILE)
    fi
    # tessl install rewrites RULES.md with every plugin, so the active
    # profile has to be laid back down after it.
    just tessl-profile name="$PROFILE"

# Link one profile's rules from .tessl/RULES.md. name: a profile in plugins/profiles, `default` among them.
tessl-profile name="default":
    #!/usr/bin/env zsh
    set -e
    # `just tessl-profile name=infra` passes the whole `name=infra` as
    # the argument, so both that and the bare `infra` have to land here.
    NAME="{{ name }}"
    NAME="${NAME#name=}"
    LINE=$(grep -E "^${NAME}( |$)" {{ TESSL_PROFILES }} || true)
    if [[ -z "$LINE" ]]; then
      KNOWN=$(cut -d' ' -f1 {{ TESSL_PROFILES }} | tr '\n' ' ')
      echo "unknown profile: $NAME (${KNOWN% })"
      exit 1
    fi
    REST="${LINE#$NAME}"
    PLUGINS=(${=REST})
    if [[ ! -d .tessl/plugins ]]; then
      echo "no installed plugins: run just tessl-plugins-install"
      exit 1
    fi
    MISSING=()
    for P in $PLUGINS; do
      if [[ ! -f .tessl/plugins/$P/rules/${P#*/}.md ]]; then
        MISSING+=($P)
      fi
    done
    if (( ${#MISSING[@]} > 0 )); then
      echo "not installed: $MISSING"
      echo "Run: just tessl-plugins-install"
      exit 1
    fi
    {
      echo "# Agent Rules"
      echo
      echo "This file is updated when running \`tessl install\`. If a linked file is missing, make sure to run the command to download any missing plugins from the registry."
      for P in $PLUGINS; do
        N=${P#*/}
        echo
        echo "## $P — $N"
        echo
        echo "@plugins/$P/rules/$N.md [$N](plugins/$P/rules/$N.md)"
      done
    } > .tessl/RULES.md
    echo "$NAME" > .tessl/PROFILE
    echo "profile $NAME: $PLUGINS"
    echo "Agents pick this up on their next session, not this one."

# Which profile is loaded, and what it costs to load it.
tessl-profile-status:
    #!/usr/bin/env zsh
    set -e
    if [[ ! -f .tessl/PROFILE ]]; then
      echo "no profile: run just tessl-profile name=default"
      exit 0
    fi
    PROFILE=$(cat .tessl/PROFILE)
    echo "profile: $PROFILE"
    TOTAL=0
    for P in $(grep -oE '^## [a-z0-9-]+/[a-z0-9-]+' .tessl/RULES.md | cut -c4-); do
      SIZE=$(wc -c < ".tessl/plugins/$P/rules/${P#*/}.md")
      printf "  %-20s %6d chars\n" "$P" "$SIZE"
      TOTAL=$(( TOTAL + SIZE ))
    done
    printf "  %-20s %6d chars\n" "total" "$TOTAL"

# Whether the installed rules match the tracked sources.
tessl-plugins-check:
    #!/usr/bin/env zsh
    set -e
    # A clone that has never installed has nothing to compare, which is
    # not drift.
    if [[ ! -d .tessl/plugins ]]; then
      echo "no installed plugins: run just tessl-plugins-install"
      exit 0
    fi
    STALE=()
    for root in {{ TESSL_PLUGIN_ROOTS }}; do
      for SRC in $root/*/rules/*.md; do
        NAME=$(basename "$SRC" .md)
        PLUGIN=$(dirname "$(dirname "$SRC")")
        WS=$(sed -n 's/.*"name": *"\([^/"]*\)\/.*/\1/p' "$PLUGIN/.tessl-plugin/plugin.json" | head -1)
        DEST=".tessl/plugins/$WS/$NAME/rules/$NAME.md"
        if [[ ! -f $DEST ]]; then
          STALE+=("$WS/$NAME (never installed)")
        elif ! diff -q "$SRC" "$DEST" >/dev/null; then
          STALE+=("$WS/$NAME")
        fi
      done
    done
    if (( ${#STALE[@]} == 0 )); then
      echo "installed rules match their sources"
      exit 0
    fi
    for X in $STALE; do
      echo "stale: $X"
    done
    echo
    echo "The rules agents load are behind the tree."
    echo "Run: just tessl-plugins-install"
    exit 1
