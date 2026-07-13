#!/usr/bin/env sh
set -eu

START_STACK=false
API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8080}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-240}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --start-stack) START_STACK=true ;;
    --api-base-url) API_BASE_URL="$2"; shift ;;
    --timeout-seconds) TIMEOUT_SECONDS="$2"; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

REPOSITORY_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
COMPOSE_FILE="$REPOSITORY_ROOT/infra/docker/docker-compose.yml"
FIXTURE="$REPOSITORY_ROOT/apps/worker/src/test/resources/fixtures/fake-scanned-invoice.png"
DEADLINE=$(( $(date +%s) + TIMEOUT_SECONDS ))

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command is missing: $1" >&2; exit 2; }
}

wait_until() {
  description="$1"
  shift
  while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    if "$@"; then return 0; fi
    sleep 2
  done
  echo "Timed out waiting for $description." >&2
  return 1
}

require_command curl
require_command docker
require_command jq
[ -f "$FIXTURE" ] || { echo "Synthetic test-only fixture is missing: $FIXTURE" >&2; exit 2; }

cd "$REPOSITORY_ROOT"
if [ "$START_STACK" = true ]; then
  docker compose -f "$COMPOSE_FILE" up -d --build
fi

wait_until "the API health endpoint" curl --fail --silent --show-error "$API_BASE_URL/health" >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T worker tesseract --version >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T worker tesseract --list-langs | grep -qx "eng"

UPLOAD=$(curl --fail --silent --show-error -F "file=@$FIXTURE;type=image/png" "$API_BASE_URL/api/drive/files")
FILE_ID=$(printf '%s' "$UPLOAD" | jq -er '.fileId')
JOB_ID=""
DETAIL=""

poll_ocr() {
  jobs=$(curl --fail --silent --show-error "$API_BASE_URL/api/media/ocr-jobs")
  JOB_ID=$(printf '%s' "$jobs" | jq -er --arg file_id "$FILE_ID" '.jobs[] | select(.fileId == $file_id) | .jobId' | head -n 1 || true)
  [ -n "$JOB_ID" ] || return 1
  DETAIL=$(curl --fail --silent --show-error "$API_BASE_URL/api/media/ocr-jobs/$JOB_ID")
  status=$(printf '%s' "$DETAIL" | jq -r '.status')
  [ "$status" != "failed" ] || { echo "OCR job failed." >&2; return 2; }
  printf '%s' "$DETAIL" | jq -e '.status == "completed" and .ocrResult != null and .extraction != null' >/dev/null
}

wait_until "real Tesseract OCR and extraction" poll_ocr

printf '%s' "$DETAIL" | jq -e '
  .provider == "tesseract"
  and .ocrResult.provider == "tesseract"
  and .ocrResult.wordCount > 0
  and .extraction.reviewRequired == true
  and ([(.extraction.fields[] | select(.fieldKey == "invoice_number") | .normalizedValue)] | index("TEST-2026-0007"))
  and ([(.extraction.fields[] | select(.fieldKey == "supplier_name") | .normalizedValue)] | index("Example Test Supplies Ltd"))
  and ([(.extraction.fields[] | select(.fieldKey == "total_amount") | .normalizedValue)] | index("123.45"))
  and ([(.extraction.fields[] | select(.fieldKey == "currency") | .normalizedValue)] | index("EUR"))
  and ([(.extraction.fields[] | select(.fieldKey == "issue_date") | .normalizedValue)] | index("2026-07-10"))
' >/dev/null

echo "Real Tesseract OCR smoke passed for the clearly labelled synthetic test invoice."
