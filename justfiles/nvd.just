# Dependency vulnerability scanning with nvd-clojure. The config, and the
# suppressions it names, are each workspace's own: a downstream never
# inherits what its upstream chose to suppress.

# Where this workspace's nvd-clojure config lives.
NVD_CONFIG := env_var_or_default("NVD_CONFIG", ".config/nvd/nvd-clojure.edn")

# Check dependencies for known CVEs (no args = dev classpath, or pass a project name)
nvd project="":
    #!/usr/bin/env zsh
    set -e
    if [[ -z "{{ project }}" ]]; then
      classpath=$(clojure -Spath -A{{ DOMAIN_ALIASES }}:dev)
    else
      classpath=$(cd projects/{{ project }} && clojure -Spath)
    fi
    # The NVD database defaults to a path under the dependency-check jar
    # in ~/.m2, so it is re-downloaded whenever that dep is bumped; cache
    # it machine-wide instead, without the schema-version subdir so
    # dependency-check can rebuild in place.
    data_dir="${XDG_DATA_HOME:-$HOME/.local/share}/dependency-check"
    clojure -J-Dclojure.main.report=stderr -J-Danalyzer.ossindex.enabled=false \
      -J-Ddata.directory="$data_dir" -M:nvd "{{ NVD_CONFIG }}" "$classpath"
