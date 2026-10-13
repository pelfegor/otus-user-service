#!/usr/bin/env sh
set -eu

POLL_DELAY_MS=${POLL_DELAY_MS:-1000}
npx --yes newman run postman/stream-processing.postman_collection.json \
  --environment postman/stream-processing.postman_environment.json \
  --delay-request "$POLL_DELAY_MS" \
  --timeout 600000 \
  --reporters cli \
  --reporter-cli-show-headers \
  --reporter-cli-show-body \
  --verbose
