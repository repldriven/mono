FROM debian:13-slim

ARG FDB_VERSION=7.3.75
RUN ARCH=$(uname -m) && \
  TINI_ARCH=$([ "$ARCH" = "aarch64" ] && echo "arm64" || echo "amd64") && \
  FDB_ARCH=$ARCH && \
  apt-get update && \
  apt-get install -y --no-install-recommends curl ca-certificates && \
  rm -rf /var/lib/apt/lists/* && \
  curl -Ls https://github.com/krallin/tini/releases/download/v0.19.0/tini-${TINI_ARCH} \
  -o /usr/bin/tini && \
  chmod +x /usr/bin/tini && \
  curl -Ls https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}/fdbserver.${FDB_ARCH} \
  -o /usr/bin/fdbserver && \
  curl -Ls https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}/fdbcli.${FDB_ARCH} \
  -o /usr/bin/fdbcli && \
  curl -Ls https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}/libfdb_c.${FDB_ARCH}.so \
  -o /usr/lib/libfdb_c.so && \
  chmod +x /usr/bin/fdbserver /usr/bin/fdbcli && \
  mkdir -p /var/fdb/data /var/fdb/logs /usr/local/etc/foundationdb

COPY fdb.bash /var/fdb/scripts/fdb.bash
RUN chmod +x /var/fdb/scripts/fdb.bash

VOLUME /var/fdb/data

EXPOSE 4500

ENTRYPOINT ["/usr/bin/tini", "-g", "--", "/var/fdb/scripts/fdb.bash"]
