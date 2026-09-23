{
  description = "Clojure monorepo development environment";

  # A JDK, Clojure, the lint and format tools, and Docker. That is the whole
  # toolchain.
  #
  # Earlier versions of this flake built a FoundationDB client from the
  # vendor's macOS .pkg, pinned protoc and the protojure plugin to exact
  # versions in versions.json, and wrapped clojure/clj so the JVM could find
  # libfdb_c. All of that went with the fdb component: nothing here is native
  # any more, and nothing generates code, so there is no versions.json and no
  # prep step to run before the workspace resolves.

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnsupportedSystem = true;
        };

        # Tessl CLI (plugin rules and skills). Pre-built binary from
        # install.tessl.io; darwin-arm64 to match this workspace's dev
        # machines.
        tessl = pkgs.stdenv.mkDerivation rec {
          pname = "tessl";
          version = "0.90.0";
          src = pkgs.fetchurl {
            url = "https://install.tessl.io/binaries/${version}/tessl-${version}-darwin-arm64.tar.gz";
            sha256 = "1v42hrlk0gfqr098b7irhdnmz72dvab8r58dskpmf257lfykf7x3";
          };
          sourceRoot = ".";
          installPhase = ''
            mkdir -p $out/bin
            install -m 755 tessl-${version}-darwin-arm64 $out/bin/tessl
          '';
        };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            pkgs.babashka
            pkgs.clojure
            pkgs.clj-kondo
            pkgs.clojure-lsp
            pkgs.colima
            pkgs.docker
            pkgs.docker-credential-helpers
            # for the RealWorld conformance suite
            pkgs.hurl
            pkgs.jdk25
            pkgs.jq
            pkgs.just
            tessl
            pkgs.k6
            pkgs.openssl
            pkgs.pnpm
            pkgs.semgrep
            pkgs.zprint
          ];

          shellHook = ''
            # Colima/Docker configuration for testcontainers
            export DOCKER_HOST="unix://$HOME/.config/colima/default/docker.sock"
            export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
            export TESTCONTAINERS_REUSE_ENABLE="TRUE"

            if ! colima status &>/dev/null; then
              echo "Docker not running — use 'just start-docker' to start"
            fi
            echo "Clojure monorepo environment loaded"
          '';
        };
      }
    );
}
