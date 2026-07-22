# CLAUDE.md

This file provides guidance to Claude Code when working with this Clojure monorepo
that follows the Polylith architecture.

## Polylith Architecture

- **Three artifact types**: Components (reusable), Bases (entry points), Projects (deployable)
- **Components** (`components/`):
  - Reusable building blocks with `interface.clj` defining public API
  - MUST defer implementation in `interface.clj` to other namespaces in the
    component, such as `core.clj`
  - Components that register system components MUST follow one of two patterns:
    1. **Simple** (`system.clj`): a single file containing all `system/defcomponents`
       calls, bare-required directly in `interface.clj`
    2. **Structured** (`system/` folder): component defs split across multiple
       namespaces (use this when there are at least two), with a `system/core.clj`
       aggregating them, bare-required directly in `interface.clj`
  - MUST NOT access other components through internal namespaces — only via
    their `interface.clj`
  - MUST NOT include other components in its `deps.edn` — require other
    components through their interface namespace directly
- **Bases** (`bases/`):
  - Application entry points (e.g., APIs, readers, processors)
  - Have `-main` functions and handle application bootstrap
  - MUST NOT depend on other bases
- **Projects** (`projects/`):
  - Combine bases and components into deployable applications
  - No code, just `deps.edn` files
  - Projects do not have `-main` functions (bases do)
  - `mono-lib` and `mono-test-lib` are **published artifacts**, not deployables.
    They have no base, list curated bricks as `:local/root` deps, and are
    consumed downstream as git deps via `:deps/root`. Adding or changing a brick
    in either is release-visible: consumers pin a sha and there is no snapshot
    channel, so it requires a new tag. Their dep keys are qualified
    (`com.repldriven.mono.components/env`) to avoid colliding with a consumer's
    own keys, and they are listed in `workspace.edn` with `:necessary` because a
    base-less project trips warning 207. A component published in `mono-lib`
    MUST be self-contained: it MUST NOT read files relative to the workspace
    root, since a consuming workspace has no such files
- **Template** (`template/`):
  - A deps-new template that scaffolds a workspace wired to `mono-lib`
  - Sits outside the Polylith directories, so `poly` ignores it
  - Starter bricks are **not** committed here; they are copied from mono at
    generation time and namespace-rewritten. Only segments listed in
    `starter.edn` are rewritten, so references to bricks that come from
    `mono-lib` keep pointing at `com.repldriven.mono.*`
  - Verify with `just template-test`

## Component-Based Infrastructure

- **System-as-data**: Entire systems defined in YAML/EDN configuration files: config -> system definitions -> started system
- **System construction**: Lifecycle management, through `system` component wrapping `donut.system`
- **Testcontainers**: Test infrastructure (DBs, message queues) defined in system
  config. The `testcontainers` component MAY call library methods on a container
  instance during construction (before start) — e.g. builder-pattern calls like
  `.withVaultToken`, `.addEnv`, `.withStartupTimeout`. It MUST NOT call library
  methods on a started container instance to extract runtime information, as this
  creates a hidden dependency on the component's library. Any component group
  that interrogates a running container instance (e.g. extracting a connection
  URL or cluster file path) MUST be defined in the `system/` folder of the
  relevant component (i.e. `system/components.clj`, registered via
  `system/core.clj`), not in `testcontainers`
- **Web Service Interceptors**: Server (`server` component) interceptors inject component instances into request context, such as datasources, MQTT clients, Pulsar consumers/producers
- **Configuration**: Env (`env` component) loading supporting profiles (:dev, :test, :prod)
- **System Multimethods**: New system components registered using `system/defcomponents` to extend system component definitions

## Error Handling

- **Anomaly-based** (nom library via `error` component):

  - Component interface functions MUST NOT throw exceptions — they MUST
    return anomalies if they fail
  - MUST NOT use `try-catch` directly; MUST use `error/try-nom` or
    `error/try-nom-ex` to catch exceptions and convert them to anomalies:

    ```clojure
    ;; Catches all exceptions
    (error/try-nom :http-client/request
                   "Failed to execute request"
                   (do-the-thing))

    ;; Catches a specific exception type
    (error/try-nom-ex :db/query
                      SQLException
                      "Failed to execute query"
                      (do-the-thing))
    ```

  - MAY use `try/finally` in special circumstances where an anomaly is not
    appropriate (e.g. ensuring resource cleanup)
  - Anomaly category reflects the call site, not the failure mode — e.g.
    `:http-client/request` not `:http-client/failed`
  - Anomaly payloads MUST contain a `:message` key. Pass a string as
    shorthand — `(error/fail :ns/x "message")` — or a map for additional
    context — `(error/fail :ns/x {:message "..." :account-id id})`

- **Common functions**:
  - _Predicates and construction_:
    - `error/anomaly?` - check if value is anomaly
    - `error/fail` - create anomaly with category and details map
  - _Exception catching_:
    - `error/try-nom` - wrap body, catching all exceptions as anomalies
    - `error/try-nom-ex` - wrap body, catching a specific exception type
  - _Let-style bindings_:
    - `error/let-nom>` - monadic let, short-circuits on first binding anomaly
  - _Threading and side effects_:
    - `error/nom->` - threading macro, short-circuits on anomalies
    - `error/nom-do>` - execute operations sequentially, short-circuit and
      call error-fn on first anomaly

## Testing

### Running Tests

- **Default project**: Always use `project:dev` unless a specific project is
  requested
- **Running all tests**:

  ```bash
  clojure -M:poly test project:dev
  ```

- **Testing specific bricks**:

  ```bash
  clojure -M:poly test brick:<brick-name> project:dev
  ```

  Multiple bricks can be tested in one pass using colon-separated names:

  ```bash
  clojure -M:poly test brick:<brick1>:<brick2> project:dev
  ```

### Writing Tests

- **No test fixtures**: Do not use `use-fixtures` — manage lifecycle explicitly
  with `with-test-system` instead
- **Test resources**: Shared config lives in the `test-resources` component.
  Each brick combines this with its own
  `test-resources/<brick>/application-test.yml`
- **with-test-system**: Starts a test system from config, asserts it started,
  and stops it after the body. The optional second element of the binding
  vector is a patch-fn applied to the system defs before start:

  ```clojure
  ;; Simple form
  (with-test-system [sys "classpath:my-component/application-test.yml"]
    (let [component (system/instance sys [:path :to :component])]
      ;; test code
      ))

  ;; With patch-fn to inject a handler before start
  (with-test-system [sys ["classpath:server/application-test.yml"
                          #(assoc-in % [:system/defs :server :handler] app)]]
    ;; test code
    )
  ```

- **nom-test>**: Chain operations as let-style bindings, failing fast on any
  anomaly and asserting none occurred. Use `_` for bindings whose values are
  only needed for their side effects (e.g. `is` assertions):

  ```clojure
  (nom-test> [result1 (operation1)
              _ (is (= expected result1))
              result2 (operation2 result1)
              _ (is (some? result2))])
  ```

  For a single anomaly check with no further bindings:

  ```clojure
  (nom-test> [_ (operation-that-must-not-fail)])
  ```

- **Test runner**: eftest runs tests in parallel out of process. Mark expensive
  infrastructure tests with `^:eftest/synchronized` to prevent too many from
  overwhelming CPU/memory

## Code Generation

- All code generation MUST use Clojure's standard prepping libraries
  support, using `:deps/prep-lib` in the appropriate `deps.edn`, with
  implementation through a co-located `build.clj` file
- **Prep all libraries**:

  ```bash
  clj -X:deps prep :aliases '[:dev]'
  ```

- **Force preparation** after a code change (e.g. changing a src file in
  the brick or project):

  ```bash
  clj -X:deps prep :aliases '[:dev]' :force true
  ```

- Generated code MUST follow Polylith naming conventions and remain
  inside the brick src tree in a `gen` folder
- Generated code MUST NOT be committed to git — use a locally scoped
  `.gitignore` file within the brick to exclude it

## Database Patterns

- **db component**: PostgreSQL integration
- **migrator component**: Liquibase-based migrations
- **Connection pooling**: Managed by system component lifecycle
- **Testcontainers**: Spin up PostgreSQL in tests via system config

## Message Queue Patterns

- **pulsar component**: Apache Pulsar integration with Avro serialization
- **Stopping**: Send to `:stop` channel to stop receiving/reading
- **mqtt component**: MQTT client integration for request-reply patterns
- **command component**: Higher-level command processing over Pulsar/MQTT
  - **Channel-based async**: Both `receive` (consumer) and `read` (reader)
    return `{:c chan :stop chan}`
  - **Message format**: `{:message <Message> :data <deserialized-data>}` on
    `:c` channel
  - `command/process` - consumes commands from Pulsar, dispatches to a
    process-fn, publishes replies via MQTT. Returns `{:stop chan}`
  - `command/send` - sends a command via Pulsar, awaits reply via MQTT.
    Returns response map or anomaly
  - `command/req->command-request` - builds wire command map from HTTP request
  - `command/req->command-response` - builds command response from HTTP request
    and result (anomaly-aware)

## Code Formatting

- **zprint**: All Clojure source is formatted with zprint, configured in `.zprint.edn`
- **Width**: 80 characters
- **Git hook**: `scripts/hooks/pre-commit` automatically formats staged Clojure
  files before each commit. Install it once with:

  ```bash
  cp scripts/hooks/pre-commit .git/hooks/pre-commit
  chmod +x .git/hooks/pre-commit
  ```

- **Namespaces**: MUST `:require` entries innermost to outermost —
  excepting indirect interfaces which extend multi-methods MUST take precedence
  (removing [] to make it obvious) unless they need to required by alias too -
  then internal namespaces, then other component interfaces (and interfaces **ONLY**),
  then external libraries, then standard libraries, separated by line-breaks. For
  component interface tests, use MUST use the `SUT` alias for the component interface,
  and MUST NOT include any other namespaces from the component.

  ```clojure
  (ns ^:eftest/synchronized com.repldriven.mono.processor.interface-test
    (:require
      com.repldriven.mono.testcontainers.interface  ;; extends `system/components`

      [com.repldriven.mono.processor.interface :as SUT]

      [com.repldriven.mono.error.interface :as error]
      [com.repldriven.mono.system.interface :as system]
      [com.repldriven.mono.test-system.interface :refer [with-test-system nom-test>]]
      [com.repldriven.mono.db.interface :as sql]
      [com.repldriven.mono.env.interface :as env]
      [com.repldriven.mono.json.interface :as json]

      [clojure.test :refer [deftest is testing]]))
  ```

- **Destructuring**: MUST destructure one level at a time in `let`, not nested
  in function args. Take the full request as a plain argument and bind each
  level separately:

  ```clojure
  (defn create
    [request]
    (let [{:keys [datasource parameters]} request
          {:keys [body path]} parameters
          {:keys [project-id]} path
          {:strs [account-id service-account]} body
          {:strs [display-name description]} service-account]
      ...))
  ```

- **Docstrings**: zprint does not reflow string content, so docstrings must be
  manually wrapped at 80 characters. Write multi-line docstrings like:

  ```clojure
  (defn my-fn
    "First line of docstring, kept within 80 characters.

    Further detail on subsequent lines, also wrapped at 80 chars. Use
    blank lines to separate paragraphs."
    [args]
    body)
  ```

## Code Linting

- **clj-kondo**: Configured in `.clj-kondo/config.edn` with lint-as mappings for macros
- **Git hook**: `scripts/hooks/pre-commit` also runs clj-kondo against the full
  `bases`, `components`, and `projects` directories when any Clojure files
  are staged, blocking the commit if lint errors are found

## Coding Guidelines

- **Naming**: Naming is hard, so try not to name at all by using thread macros.
  Names MUST be narrow, for example, functions in command would be named
  `processs`, `send`, etc and not `process-command`, `send-command`, etc.
  "Elements of Clojure" by Zachary Tellman gets _everything_ right about names,
  in particular, "if a function crosses data scope boundaries, there should
  be a verb in the name. If it pulls data from another scope, it should
  describe the datatype it returns. If it pushes data into another scope,
  it should describe the effect it has.
- **Referential transparency**: For an expression to be referentially
  transparent, we must be able to bind the expression to a name, substitute
  that name for any or all occurrences of the original expression (within the
  same context), and nothing should have changed (except perhaps the execution
  time). Prefer pure functions that return the same value for the same inputs,
  with no observable side effects. Name functions after what they return, not
  what they do.
- **Keyword keys throughout**: All data — including from external systems
  (Pulsar, MQTT, HTTP) — uses kebab-case keyword keys. Avro (Lancaster)
  and Protojure both use keyword keys natively. Muuntaja decodes JSON
  request bodies with `keyword` decode-key-fn. Use `{:keys [...]}`
  destructuring everywhere. Exception: explicit JSON parsing via
  `json/read-str` or `http-client/res->body` returns string keys
  (clojure.data.json default) — callers of those functions check with
  string keys.

## Git Workflow

- **Merge from main before committing**: Renovate automatically merges
  dependency updates to `main`. Before committing work on a branch (or
  on `main` directly), pull/merge from `main` to avoid conflicts with
  Renovate's `deps.edn` and GitHub Actions updates.
- **Dependency management**: Renovate (`renovate.json`) handles all
  dependency updates — Clojure `deps.edn` and GitHub Actions. PRs are
  created automatically on a weekly schedule. Do NOT manually bump
  dependency versions that Renovate manages.
