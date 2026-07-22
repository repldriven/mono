#!/bin/bash

set -eo pipefail

FDB_PORT=${FDB_PORT:-4500}
FDB_PROCESS_CLASS=${FDB_PROCESS_CLASS:-unset}
CLUSTER_FILE=/usr/local/etc/foundationdb/fdb.cluster

echo "fdb:fdb@127.0.0.1:${FDB_PORT}" >"$CLUSTER_FILE"

echo "Starting FDB server on 127.0.0.1:${FDB_PORT}"

fdbserver \
  --listen-address 0.0.0.0:${FDB_PORT} \
  --public-address 127.0.0.1:${FDB_PORT} \
  --datadir /var/fdb/data \
  --logdir /var/fdb/logs \
  --cluster-file "$CLUSTER_FILE" \
  --locality-zoneid="$(hostname)" \
  --locality-machineid="$(hostname)" \
  --class "$FDB_PROCESS_CLASS" \
  --knob_disable_posix_kernel_aio=1 &

FDB_PID=$!

for i in $(seq 1 30); do
  if fdbcli -C "$CLUSTER_FILE" --exec "configure new single memory"; then
    echo "FDBD joined cluster."
    break
  fi
  echo "Waiting for FDB server to be ready... (attempt $i/30)"
  sleep 1
done

wait $FDB_PID
