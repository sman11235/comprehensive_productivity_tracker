#!/bin/sh
set -eu

RUN_MODE="${RUN_MODE:-loop}"
PRODUCER_SCRIPT="${PRODUCER_SCRIPT:-github_dev_producer.py}"
API_SCRIPT="${API_SCRIPT:-auth_api.py}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-900}"

if [ "${RUN_MODE}" = "once" ]; then
  exec python "${PRODUCER_SCRIPT}"
fi

if [ "${RUN_MODE}" = "api" ]; then
  exec python "${API_SCRIPT}"
fi

echo "Starting poller for ${PRODUCER_SCRIPT} with interval ${POLL_INTERVAL_SECONDS}s"

while true; do
  echo "Running ${PRODUCER_SCRIPT} at $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  python "${PRODUCER_SCRIPT}"
  echo "Sleeping for ${POLL_INTERVAL_SECONDS}s"
  sleep "${POLL_INTERVAL_SECONDS}"
done
