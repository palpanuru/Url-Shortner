#!/usr/bin/env bash
# End-to-end smoke test against a running instance (default http://localhost:8080).
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8080}"
KEY="${API_KEY:-demo-key-change-me}"
H=(-H "X-API-Key: $KEY" -H "Content-Type: application/json")

step() { printf '\n== %s\n' "$1"; }

step "health"
curl -fsS "$BASE/actuator/health" && echo

step "create (random code)"
CREATED=$(curl -fsS "${H[@]}" -X POST "$BASE/api/v1/links" -d '{"url":"https://example.com/docs?ref=smoke"}')
echo "$CREATED"
CODE=$(echo "$CREATED" | sed -E 's/.*"code":"([^"]+)".*/\1/')

step "create (custom alias + expiry)"
curl -fsS "${H[@]}" -X POST "$BASE/api/v1/links" \
  -d "{\"url\":\"https://example.org\",\"alias\":\"smoke-$RANDOM\",\"expiresAt\":\"$(date -u -d '+7 days' +%Y-%m-%dT%H:%M:%SZ)\"}" && echo

step "redirect /$CODE (expect 302)"
curl -sS -o /dev/null -w '%{http_code} -> %{redirect_url}\n' -A "Mozilla/5.0 Firefox/121.0" -e "https://news.example.com/a" "$BASE/$CODE"
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/$CODE"

step "rejected inputs (expect 422, 401)"
curl -sS -o /dev/null -w 'javascript: URL -> %{http_code}\n' "${H[@]}" -X POST "$BASE/api/v1/links" -d '{"url":"javascript:alert(1)"}'
curl -sS -o /dev/null -w 'private IP     -> %{http_code}\n' "${H[@]}" -X POST "$BASE/api/v1/links" -d '{"url":"http://169.254.169.254/"}'
curl -sS -o /dev/null -w 'no API key     -> %{http_code}\n' -X POST "$BASE/api/v1/links" -H "Content-Type: application/json" -d '{"url":"https://example.com"}'

sleep 1   # analytics are written asynchronously
step "stats"
curl -fsS -H "X-API-Key: $KEY" "$BASE/api/v1/links/$CODE/stats?days=3" && echo

step "delete, then redirect (expect 204, 404)"
curl -sS -o /dev/null -w '%{http_code}\n' -H "X-API-Key: $KEY" -X DELETE "$BASE/api/v1/links/$CODE"
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/$CODE"
echo; echo "smoke test OK"
