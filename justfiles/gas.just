# The commands a Gas City polecat lane runs against a work branch. Each
# runs inside the nix devshell — clojure, clj-kondo and semgrep live only
# there — through `nix develop` rather than direnv: an agent's shell has
# no direnv hook, and a fresh agent worktree carries an untrusted .envrc
# that direnv refuses anyway. The lane takes a setup, lint and test
# command from the rig's formula vars; the city names these three, so the
# command set stays versioned with the code it checks.

# Prepare the worktree: whatever `setup` does in this workspace.
gas-setup:
    nix develop . --command just setup

# Run the test default.
gas-test:
    nix develop . --command just test

# Lint, semgrep- and guardrail-check the branch's Clojure changes against origin/<base>.
gas-lint base="main":
    nix develop . --command just lint-branch {{ base }}
