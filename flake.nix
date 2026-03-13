{
  description = "Clojure monorepo development environment";

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

        # Fetch pre-built FDB binary directly from GitHub releases
        fdbArch = if pkgs.stdenv.isAarch64 then "arm64" else "x86_64";
        fdbBinary = pkgs.stdenv.mkDerivation {
          name = "foundationdb-7.3.27";
          src = pkgs.fetchurl {
            url = "https://github.com/apple/foundationdb/releases/download/7.3.27/FoundationDB-7.3.27_${fdbArch}.pkg";
            sha256 =
              if pkgs.stdenv.isAarch64 then
                "sha256-WFEDEjy4rbDygTxHwbEuxnV5JLSoiA8Asc0D0e0lVv0="
              else
                "sha256-Vyh8Peqxgk9/G7w3KKRTjRcdqdWjY5dYE77weozxVlM="; # x86 hash
          };
          buildInputs = [
            pkgs.xar
            pkgs.cpio
          ];
          unpackPhase = ''
            xar -xf $src
            cat FoundationDB-clients.pkg/Payload | gunzip -dc | cpio -i
          '';
          installPhase = ''
            mkdir -p $out/lib $out/bin
            cp -r usr/local/lib/* $out/lib/
            cp -r usr/local/bin/* $out/bin/
          '';
        };

        protocGenClojure = pkgs.stdenv.mkDerivation {
          name = "protoc-gen-clojure-2.1.2";
          src = pkgs.fetchurl {
            url = "https://github.com/protojure/protoc-plugin/releases/download/v2.1.2/protoc-gen-clojure";
            sha256 = "0vzz78fd4awbsc5iykych9yqd86yab18f8fbbgydrw556lmhv8hh";
          };
          dontUnpack = true;
          installPhase = ''
            mkdir -p $out/bin
            cp $src $out/bin/protoc-gen-clojure
            chmod +x $out/bin/protoc-gen-clojure
          '';
        };

        protocArch = if pkgs.stdenv.isAarch64 then "aarch_64" else "x86_64";
        protocBinary = pkgs.stdenv.mkDerivation {
          name = "protoc-25.8";
          src = pkgs.fetchurl {
            url = "https://github.com/protocolbuffers/protobuf/releases/download/v25.8/protoc-25.8-osx-${protocArch}.zip";
            sha256 =
              if pkgs.stdenv.isAarch64 then
                "sha256-UIIE7oJiT01MQUF81XNyHzqZWkzqTzCD+wYp7Gk2ZxI="
              else
                "sha256-J2NjPhXHFDEra/dW+H1YGaXypsbc6291RPDht9bblm0=";
          };
          sourceRoot = ".";
          nativeBuildInputs = [ pkgs.unzip ];
          installPhase = ''
            mkdir -p $out/bin $out/include
            cp bin/protoc $out/bin/
            cp -r include/* $out/include/
          '';
        };

        libPath = pkgs.lib.makeLibraryPath [ fdbBinary ];

        # Wrap clojure/clj to always set DYLD_LIBRARY_PATH for the FDB native
        # library. DYLD_* vars are stripped by macOS SIP when launching
        # restricted processes (e.g. Claude Code), so env inheritance is
        # unreliable — the wrapper bakes the path in at the binary level.
        # Both binaries are wrapped: clojure (raw CLI) and clj (rlwrap
        # variant for interactive REPLs).
        clojureWithFdb = pkgs.writeShellScriptBin "clojure" ''
          export DYLD_LIBRARY_PATH="${libPath}:$DYLD_LIBRARY_PATH"
          exec ${pkgs.clojure}/bin/clojure "$@"
        '';
        cljWithFdb = pkgs.writeShellScriptBin "clj" ''
          export DYLD_LIBRARY_PATH="${libPath}:$DYLD_LIBRARY_PATH"
          exec ${pkgs.clojure}/bin/clj "$@"
        '';

      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            pkgs.babashka
            cljWithFdb
            clojureWithFdb
            pkgs.clj-kondo
            pkgs.clojure-lsp
            pkgs.colima
            pkgs.docker
            pkgs.docker-credential-helpers
            fdbBinary
            pkgs.jdk21
            pkgs.just
            pkgs.k6
            pkgs.openssl
            protocBinary
            protocGenClojure
            pkgs.semgrep
            pkgs.zprint
          ];

          shellHook = ''
            # Make libfdb_c findable by the JVM's JNI loader
            export LD_LIBRARY_PATH="${libPath}:$LD_LIBRARY_PATH"
            export DYLD_LIBRARY_PATH="${libPath}:$DYLD_LIBRARY_PATH"

            # Colima/Docker configuration for testcontainers
            export DOCKER_HOST="unix://$HOME/.config/colima/default/docker.sock"
            export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
            export TESTCONTAINERS_REUSE_ENABLE="TRUE"

            echo "FDB libs: ${libPath}"
            echo "fdbcli: $(command -v fdbcli || echo 'not found')"
            echo "protoc-gen-clojure: $(protoc-gen-clojure -v 2>&1 || echo 'not found')"
            if ! colima status &>/dev/null; then
              echo "Docker not running — use 'just start-docker' to start"
            fi
            echo "Clojure monorepo environment loaded"
          '';
        };
      }
    );
}
