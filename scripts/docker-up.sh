#!/usr/bin/env sh
set -e

cd "$(dirname "$0")/.."

if [ ! -f .env.docker ]; then
  echo "Missing .env.docker — copy .env.docker.example and fill in secrets."
  echo "  cp .env.docker.example .env.docker"
  exit 1
fi

docker compose --env-file .env.docker up --build -d "$@"
echo "Frontend: http://localhost:${WEB_PORT:-5173}"
echo "Backend:  http://localhost:${API_PORT:-8080}/api"
