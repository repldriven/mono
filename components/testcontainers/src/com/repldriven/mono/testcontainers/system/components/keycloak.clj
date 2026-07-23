(ns com.repldriven.mono.testcontainers.system.components.keycloak
  "Keycloak testcontainer that boots a real Keycloak realm for
  high-fidelity auth tests. Wrapper around dasniko/testcontainers-
  keycloak — the lib handles startup probing and the
  `withRealmImportFile` import hook. The container exposes its
  randomly-mapped HTTP and management ports as data, which the
  generic `testcontainers/mapped-exposed-port` and
  `testcontainers/uri` components turn into the base URLs
  `keycloak/identity-provider` can be pointed at."
  (:require
    [com.repldriven.mono.testcontainers.container :as container]

    [com.repldriven.mono.log.interface :as log]

    [clojure.java.shell :as shell]
    [clojure.string :as str])
  (:import
    (dasniko.testcontainers.keycloak KeycloakContainer)
    (org.testcontainers.images.builder Transferable)
    (org.testcontainers.utility MountableFile)))

(def default-docker-image-name "quay.io/keycloak/keycloak:26.0")

;; Keycloak serves HTTP on 8080 inside the container.
(def default-exposed-port 8080)

;; Keycloak 25+ serves the management endpoints — /health, /health/ready,
;; /metrics — on a second port rather than alongside the application.
;;
;; Exposing the port only maps it; whether an endpoint answers there is a
;; separate matter. /health is on already — KeycloakContainer enables it for
;; its own readiness wait. /metrics needs `:metrics true`, because the
;; container library pins KC_METRICS_ENABLED=false itself. Until then a
;; wired-up management URL resolves fine and then 404s.
(def default-management-port 9000)

;; Files Keycloak expects to find under <theme>/login/. Copied
;; individually as classpath resources because Testcontainers'
;; MountableFile doesn't recursively materialise a classpath
;; directory tree.
(def ^:private theme-files
  ["login/theme.properties"
   "login/resources/css/styles.css"])

(defn- mount-theme!
  [^KeycloakContainer c theme-resource theme-name]
  (doseq [rel theme-files]
    (let [src (str theme-resource "/" rel)
          dst (str "/opt/keycloak/themes/" theme-name "/" rel)]
      (.withCopyFileToContainer c
                                (MountableFile/forClasspathResource src)
                                dst))))

(defn- resolve-secret
  [command]
  (let [{:keys [exit out]} (apply shell/sh command)]
    (when (zero? exit)
      ;; Keycloak's files-plaintext vault reads the file's raw bytes as
      ;; the secret, so a trailing newline would corrupt it. Strip only
      ;; newlines, never significant whitespace.
      (str/trim-newline out))))

(defn- mount-vault-secrets!
  [^KeycloakContainer c vault-dir secrets]
  ;; Each secret is optional: a missing secret (command exits non-zero, or
  ;; `pass` isn't installed) skips just that IdP rather than aborting
  ;; startup.
  (let [resolved (into {}
                       (keep (fn [[k command]]
                               (if-let [secret (try (resolve-secret command)
                                                    (catch Exception _ nil))]
                                 [k secret]
                                 (do (log/warn
                                      "Skipping optional Keycloak vault"
                                      "secret" (name k)
                                      "- external IdP"
                                      "sign-in will be unavailable until"
                                      "it is provisioned; command:"
                                      command)
                                     nil))))
                       secrets)]
    ;; Only enable the file vault when at least one secret resolved. With
    ;; KC_VAULT=file set but a referenced file absent, Keycloak fails to
    ;; resolve ${vault.<key>} during realm import and never becomes
    ;; healthy; leaving the vault off keeps the expression an inert
    ;; literal, exactly as the default profile behaves.
    (when (seq resolved)
      (.withEnv c "KC_VAULT" "file")
      (.withEnv c "KC_VAULT_DIR" vault-dir)
      (doseq [[k secret] resolved]
        ;; `(name k)` so a YAML map key reaches the container as the exact
        ;; filename the REALM_UNDERSCORE_KEY resolver expects
        ;; (<realm>_<key>); the value runs at boot, never on the host env.
        (.withCopyToContainer c
                              (Transferable/of ^String secret)
                              (str vault-dir "/" (name k)))))))

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or
      instance
      (let [{:keys [docker-image-name realm-import-file realm-import-files
                    host-port theme-resource theme-name vault-dir vault-secrets
                    env metrics]}
            config
            ;; Singular `:realm-import-file` stays supported for
            ;; back-compat; `:realm-import-files` (vector) wins when
            ;; both are set, so a system YAML can mount multiple
            ;; realms into the same container.
            files (or (seq realm-import-files)
                      (when realm-import-file [realm-import-file]))]
        (log/info "Starting keycloak container" docker-image-name)
        (let [c (KeycloakContainer. docker-image-name)]
          (doseq [f files] (.withRealmImportFile c f))
          ;; Arbitrary settings, straight from the YAML, so this
          ;; component never has to learn which flags Keycloak has:
          ;; KC_LOG_LEVEL, KC_FEATURES and the rest are all one config
          ;; key. Keys may be keywords or strings; the name is passed
          ;; through unmunged, so spell the variable exactly.
          ;;
          ;; Applied before the settings below, so the vault wiring
          ;; wins over an entry that would otherwise contradict it.
          ;; The container library likewise overrides the KC_* vars it
          ;; manages itself — KC_METRICS_ENABLED among them, hence
          ;; `metrics` below.
          (doseq [[k v] env] (.withEnv c (name k) (str v)))
          ;; The one flag :env cannot express. KeycloakContainer writes
          ;; KC_METRICS_ENABLED=false during its own configure step,
          ;; which runs at start and so lands after anything set above;
          ;; its builder is the only lever that survives. Setting it
          ;; through :env is therefore a silent no-op — say so, rather
          ;; than leave someone reading a 404 against config that looks
          ;; correct.
          (when (some #(= "KC_METRICS_ENABLED" (name %)) (keys env))
            (log/warn "KC_METRICS_ENABLED in :env is overridden by"
                      "KeycloakContainer - use `metrics: true` instead"))
          (when metrics (.withEnabledMetrics c))
          ;; Optional custom login theme. `theme-resource` is a
          ;; classpath prefix containing `login/theme.properties`
          ;; and `login/resources/css/styles.css`; they get copied
          ;; into the container at `/opt/keycloak/themes/<theme-
          ;; name>/login/...` where Keycloak picks them up.
          (when theme-resource (mount-theme! c theme-resource theme-name))
          ;; Fixed `host-port` pins :8080 to a known host port so a
          ;; host-running SPA can reach it; nil/0 keeps the random
          ;; mapping parallel test runs need.
          (when (and host-port (pos? host-port))
            (.setPortBindings c [(str host-port ":8080")]))
          ;; Optional Keycloak files-plaintext vault. Each secret is
          ;; obtained by running its command at boot and written into
          ;; the container's vault dir, so values referenced from a
          ;; realm as ${vault.<key>} resolve without the secret ever
          ;; touching the host environment or the realm JSON.
          (when (seq vault-secrets)
            (mount-vault-secrets! c vault-dir vault-secrets))
          ;; start! snapshots the mapped ports into the instance map, so
          ;; nothing downstream has to interrogate a running container.
          (container/start! c
                            [default-exposed-port default-management-port])))))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping keycloak container")
                  (container/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :realm-import-file nil
                   :realm-import-files nil
                   :host-port nil
                   :theme-resource nil
                   :theme-name "queenswood"
                   :vault-dir nil
                   :vault-secrets nil
                   :env {}
                   :metrics false}
   :system/instance-schema map?})
