#!/bin/sh
set -e

export UPLOAD_DIR="${UPLOAD_DIR:-/app/uploads}"
mkdir -p "$UPLOAD_DIR"

if [ -n "${GCP_SERVICE_ACCOUNT_JSON:-}" ]; then
  printf '%s' "$GCP_SERVICE_ACCOUNT_JSON" > /app/credentials/service-account.json
  export GOOGLE_APPLICATION_CREDENTIALS=/app/credentials/service-account.json
fi

exec java -jar /app/app.jar
