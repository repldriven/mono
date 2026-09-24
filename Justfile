set shell := ["zsh", "-cu"]

# The example domain. DOMAIN_ALIASES is concatenated onto -M/-A; POLY_PROFILES
# is the space-separated form poly wants, and poly spells profiles without the
# leading colon.
DOMAIN_ALIASES := ":+realworld"
POLY_PROFILES := "+realworld"

# Cap every test JVM to the CPUs Docker actually has, falling back to the
# host's when Docker is down: an uncapped JVM sizes its pools from the
# host's core count and starves the VM the containers run in.
TEST_OPTS := "JDK_JAVA_OPTIONS=\"-XX:ActiveProcessorCount=$(n=$(docker info --format '{{.NCPU}}' 2>/dev/null); if [ \"${n:-0}\" -gt 0 ] 2>/dev/null; then echo \"$n\"; else nproc 2>/dev/null || sysctl -n hw.ncpu; fi)\""

import 'justfiles/gas.just'
import 'justfiles/gh.just'
import 'justfiles/hooks.just'
import 'justfiles/lint.just'
import 'justfiles/nvd.just'
import 'justfiles/tessl.just'

list:
    just --list

# Prepare a fresh worktree: prep every brick that declares :deps/prep-lib.
setup:
    clojure -X:deps prep :aliases '[{{ DOMAIN_ALIASES }} :dev]'

# Generate a throwaway workspace from the template and verify it end to end.
# Uses the working copy rather than a published tag, so it can run before a
# release exists.

# Generate a throwaway workspace from the template and verify it
#
# A hyphenated name by default: deps-new munges hyphens to underscores for
# {{main/file}} and leaves them alone for {{main}}, so a single-word name
# cannot tell a correct substitution from a wrong one.
template-test name="com.acme/my-blog" out="/tmp/mono-template-test":
    #!/usr/bin/env zsh
    set -e
    rm -rf {{ out }}
    mkdir -p $(dirname {{ out }})
    clojure -Sdeps "{:deps {com.repldriven.mono/template {:local/root \"$PWD/template\"}}}" \
      -Tnew create \
      :template com.repldriven.mono/template \
      :name {{ name }} \
      :target-dir '"{{ out }}"' \
      :mono/dir "\"$PWD\"" \
      :overwrite :delete
    cd {{ out }}
    # the rewrite policy, as an assertion: starter namespaces must be gone,
    # library namespaces must remain
    if grep -rq 'com\.repldriven\.mono\.realworld' .; then
        echo "FAIL: starter namespaces leaked into the generated workspace"; exit 1
    fi
    if ! grep -rq 'com\.repldriven\.mono\.error' .; then
        echo "FAIL: library namespaces were rewritten but should not have been"; exit 1
    fi
    # poly names a project after its directory, so a directory that disagrees
    # with workspace.edn yields a project with no alias, and `poly check` still
    # passes. Assert the two agree, and that the Justfile can find it.
    project=$(basename {{ name }})
    if [ ! -d "projects/$project" ]; then
        echo "FAIL: expected projects/$project, found $(ls projects)"; exit 1
    fi
    if ! grep -q "\"$project\" {:alias" workspace.edn; then
        echo "FAIL: workspace.edn does not declare a project called $project"; exit 1
    fi
    if ! just --dry-run realworld-hurl 2>&1 | grep -q "cd projects/$project "; then
        echo "FAIL: realworld-hurl does not point at projects/$project"; exit 1
    fi
    clojure -M:poly check +realworld
    echo "✓ template generates a workspace that checks"

# Start nREPL server for Conjure connection
repl:
    find . -name .nrepl-port -not -path ./.nrepl-port -delete
    clojure -M{{ DOMAIN_ALIASES }}:dev:test:nrepl -Sforce -P

# Start Rebel Readline REPL with colors and completion
rebel:
    clj -M{{ DOMAIN_ALIASES }}:dev:test:rebel

# Start polylith shell
shell:
    clj -M:poly shell

# Build all polylith projects as uberjars
build snapshot="true":
    #!/usr/bin/env zsh
    for project in projects/*/; do
        name=${project:t}
        # Library projects are published as git deps, not uberjars: they have
        # no base, no -main and no :build alias.
        if [[ "$name" == *-lib ]]; then
            echo "Skipping library project $name"
            continue
        fi
        echo "Building $name..."
        (cd "$project" && clojure -X:build uber :snapshot {{ snapshot }})
    done

# Run the official RealWorld conformance suite against a live service.
#
# Not part of `just test`: it needs a real server on a fixed port and a real
# postgres, which is a different shape of thing from a brick test. The suite
# is the contract — where our own tests and these disagree, these win.
#
# 8091 rather than 8080 because 8080 is the dev profile's, and a stray dev
# server answering instead is a confusing way to fail: every request 404s
# and nothing says why.

# Run the official RealWorld conformance suite against a live service
realworld-hurl port="8091":
    #!/usr/bin/env bash
    set -uo pipefail
    root="$PWD"
    hurl_dir="$root/bases/realworld-api/test-resources/realworld-api/hurl"
    log="$root/target/realworld-hurl.log"
    mkdir -p "$(dirname "$log")"

    cleanup() {
      [ -n "${service_pid:-}" ] && kill "$service_pid" 2>/dev/null
      docker rm -f realworld-pg >/dev/null 2>&1
    }
    trap cleanup EXIT

    if lsof -nP -iTCP:{{ port }} -sTCP:LISTEN >/dev/null 2>&1; then
      echo "port {{ port }} is already in use; pass another, e.g."
      echo "  just realworld-hurl 8099"
      exit 1
    fi

    docker rm -f realworld-pg >/dev/null 2>&1
    docker run -d --name realworld-pg -p 55432:5432 \
      -e POSTGRES_USER=realworld -e POSTGRES_PASSWORD=realworld \
      -e POSTGRES_DB=realworld postgres:16.2 >/dev/null
    echo "waiting for postgres..."
    for _ in $(seq 1 60); do
      docker exec realworld-pg pg_isready -U realworld >/dev/null 2>&1 && break
      sleep 1
    done

    export REALWORLD_DB_HOST=localhost REALWORLD_DB_PORT=55432
    export REALWORLD_DB_NAME=realworld REALWORLD_DB_USER=realworld
    export REALWORLD_DB_PASSWORD=realworld
    export REALWORLD_JWT_SECRET=conformance-secret-not-for-production
    export REALWORLD_PORT={{ port }}

    # From the project rather than the dev alias: :dev carries :main-opts
    # for portal, which fights with -m. This is also the classpath a
    # deployment actually uses.
    (cd projects/realworld-service && \
      clojure -M -m com.repldriven.mono.realworld-api.main \
        --config-file classpath:realworld-api/application.yml \
        --profile default) >"$log" 2>&1 &
    service_pid=$!

    echo "waiting for the service on {{ port }}..."
    ready=false
    for _ in $(seq 1 120); do
      if curl -fsS "http://localhost:{{ port }}/api/tags" >/dev/null 2>&1; then
        ready=true; break
      fi
      kill -0 "$service_pid" 2>/dev/null || break
      sleep 1
    done

    if [ "$ready" != true ]; then
      echo "the service never became ready; its output was:"
      echo "----------------------------------------------------------------"
      tail -40 "$log"
      exit 1
    fi

    hurl --test --jobs 1 \
      --variable host="http://localhost:{{ port }}" \
      --variable uid="$(date +%s)" \
      "$hurl_dir"/*.hurl

# Run all polylith project tests; Docker is started once with start-docker
test:
    {{ TEST_OPTS }} SKIP_META=repl JUNIT_NS_PREFIX=com.repldriven.mono. \
      clojure -M:poly test :all {{ POLY_PROFILES }}

# Check test failures from last test run
poly-test-check:
    #!/usr/bin/env python3
    import xml.etree.ElementTree as ET
    import sys
    from pathlib import Path

    xml_file = Path("./target/test-results/junit.xml")

    if not xml_file.exists():
        print("❌ No test results found. Run 'just test' first.")
        sys.exit(1)

    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()

        failures = []
        for testsuite in root.findall('.//testsuite'):
            for testcase in testsuite.findall('.//testcase'):
                failure = testcase.find('failure')
                if failure is not None:
                    failures.append({
                        'package': testsuite.get('package', ''),
                        'test': testcase.get('name', ''),
                        'class': testcase.get('classname', ''),
                        'message': failure.text or ''
                    })

        if failures:
            print("\n=== Failed Tests ===\n")
            for f in failures:
                print(f"❌ {f['package']}/{f['test']}")
                print(f"   {f['message'].strip()[:200]}")
                print()
            print(f"Total failures: {len(failures)}")
        else:
            print("✅ All tests passed!")

    except Exception as e:
        print(f"Error reading test results: {e}")
        sys.exit(1)

# Linter
lint:
    clojure -M:lint/clj-kondo --lint bases components projects template/src deps.edn workspace.edn

# Formatter - lays out cond pairs, then zprint with the .zprint.edn in project root
format:
    #!/usr/bin/env bash
    set -e
    echo "Formatting Clojure source files..."
    # template/resources holds files with deps-new placeholders in them, which
    # are not valid Clojure until substituted, so neither pass can parse them
    files=$(git ls-files '*.clj' '*.cljc' '*.cljs' | grep -v '^template/resources/' | while read f; do [ -f "$f" ] && echo "$f"; done)
    if [ -n "$files" ]; then
        echo "$files" | xargs bb scripts/hooks/cond_pairs.clj
        echo "$files" | xargs clojure -M:format/zprint '{:search-config? true}' -w
        echo "✓ Formatting complete"
    else
        echo "No Clojure files found"
    fi

# Start Docker via Colima
start-docker:
    colima status 2>/dev/null || colima start --arch aarch64 --vm-type vz --vz-rosetta --cpu 9 --memory 24
    docker context use colima

# Stop Docker via Colima
stop-docker:
    colima stop

start-telemetry:
  docker run -d --name jaeger \
    -p 16686:16686 \
    -p 4318:4318 \
    jaegertracing/jaeger:latest \
    --set receivers.otlp.protocols.http.endpoint=0.0.0.0:4318

stop-telemetry:
  docker stop jaeger && docker rm jaeger

