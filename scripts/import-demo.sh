#!/usr/bin/env bash
# Loads the 2027 national/regional holidays (BOE) and then the Catalan local holidays.
# Usage: LABORABLE_ADMIN_API_KEY=... ./scripts/import-demo.sh [base-url]
# Note: Catalan local holidays for 2027 are published later in the year; use year 2026 if 2027 is empty.
set -euo pipefail
BASE="${1:-http://localhost:8080}"
: "${LABORABLE_ADMIN_API_KEY:?set LABORABLE_ADMIN_API_KEY}"

post() {
  curl -fsS -X POST "$BASE/admin/holidays/import" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $LABORABLE_ADMIN_API_KEY" \
    -d "$1"
  echo
}

post '{"source":"BOE","year":2027,"boeId":"BOE-A-2026-21254","dryRun":false}'
post '{"source":"CATALONIA_LOCAL","year":2027,"dryRun":false}' || echo "Catalan local holidays for 2027 are not published yet"
