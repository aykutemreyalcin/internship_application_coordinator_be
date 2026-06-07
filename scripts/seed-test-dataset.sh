#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"

echo "Seeding 40-case test dataset..."
curl -sS -X POST "${API_BASE_URL}/internal/test-dataset/seed" | python3 -m json.tool

echo ""
echo "Summary:"
curl -sS "${API_BASE_URL}/internal/test-dataset/summary" | python3 -m json.tool
