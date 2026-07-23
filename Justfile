set shell := ["zsh", "-cu"]

DOMAIN_ALIASES := ":+example"

list:
    just --list

# Generate a throwaway workspace from the template and verify it end to end.
# Uses the working copy rather than a published tag, so it can run before a
# release exists.
template-test name="com.acme/bookmarks" out="/tmp/mono-template-test":
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
    if grep -rq 'com\.repldriven\.mono\.example' .; then
        echo "FAIL: starter namespaces leaked into the generated workspace"; exit 1
    fi
    if ! grep -rq 'com\.repldriven\.mono\.error' .; then
        echo "FAIL: library namespaces were rewritten but should not have been"; exit 1
    fi
    clojure -X:deps prep :aliases '[:dev :+example]'
    clojure -M:poly check
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


# Run all polylith project tests
test: start-docker
    SKIP_META=repl clojure -M:poly test :all

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

# Check dependencies for known CVEs (no args = dev classpath, or pass project name)
nvd project="":
    #!/usr/bin/env zsh
    if [[ -z "{{ project }}" ]]; then
      classpath=$(clojure -Spath -A{{ DOMAIN_ALIASES }}:dev)
    else
      classpath=$(cd projects/{{ project }} && clojure -Spath)
    fi
    clojure -J-Dclojure.main.report=stderr -J-Danalyzer.ossindex.enabled=false -M:nvd "nvd-clojure.edn" "$classpath"

# Linter
lint-eastwood:
    clojure -M{{ DOMAIN_ALIASES }}:dev:test:lint/eastwood
lint-clj-kondo:
    clojure -M:lint/clj-kondo --lint bases components projects template/src deps.edn workspace.edn
lint:
  just lint-eastwood
  just lint-clj-kondo

# Formatter - uses .zprint.edn config in project root
format:
    #!/usr/bin/env bash
    set -e
    echo "Formatting Clojure source files..."
    # template/resources holds files with deps-new placeholders in them, which
    # are not valid Clojure until substituted, so zprint cannot parse them
    files=$(git ls-files '*.clj' '*.cljc' '*.cljs' | grep -v '^template/resources/' | while read f; do [ -f "$f" ] && echo "$f"; done)
    if [ -n "$files" ]; then
        echo "$files" | xargs clojure -M:format/zprint '{:search-config? true}' -w
        echo "✓ Formatting complete"
    else
        echo "No Clojure files found"
    fi

force-prep:
    clojure -X:deps prep :aliases '[{{ DOMAIN_ALIASES }} :dev]' :force true

# Start Docker via Colima
start-docker:
    colima status 2>/dev/null || colima start --arch aarch64 --vm-type vz --vz-rosetta --cpu 6 --memory 12 
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

