#!/bin/sh
# Wait until the legacy app answers on its login page.
# Usage: sh scripts/lab/wait-for-app.sh [base-url] [timeout-seconds]
set -u
BASE_URL="${1:-http://127.0.0.1:8080/wholesale}"
TIMEOUT="${2:-180}"
elapsed=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/login.do" || true)
  if [ "$code" = "200" ]; then
    echo "app ready: $BASE_URL/login.do (${elapsed}s)"
    exit 0
  fi
  sleep 3
  elapsed=$((elapsed + 3))
done
echo "app not ready after ${TIMEOUT}s: $BASE_URL/login.do (last HTTP status: ${code:-none})" >&2
echo "try: sh scripts/lab/reset-lab.sh" >&2
exit 1
