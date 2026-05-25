#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
WORKSPACE_ID="${WORKSPACE_ID:-wrk_dev_placeholder}"
ACTOR_ID="${ACTOR_ID:-usr_dev_placeholder}"

echo "Resetting fake/test invoice automation demo data against ${API_BASE_URL}"
curl --fail --silent --show-error \
  --request POST \
  --header "X-Workspace-Id: ${WORKSPACE_ID}" \
  --header "X-Actor-Id: ${ACTOR_ID}" \
  "${API_BASE_URL}/api/demo/invoice-automation/reset"
echo
