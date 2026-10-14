#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="http://127.0.0.1:${EDGE_PORT:-8088}"
[[ -f "$ROOT/.env" ]] || { echo 'Run ./scripts/deploy.sh init first' >&2; exit 1; }
# Parse dotenv as data. Never source it as executable shell.
PAYLOAD="$(python3 - "$ROOT/.env" <<'PYCODE'
import json,sys
values={}
for line in open(sys.argv[1],encoding='utf-8'):
  if '=' in line and not line.lstrip().startswith('#'):
    k,v=line.rstrip('\n').split('=',1)
    values[k]=v
print(json.dumps({'username': values.get('BOOTSTRAP_ADMIN_USERNAME','admin'),
                  'password': values.get('BOOTSTRAP_ADMIN_PASSWORD','')}))
PYCODE
)"
[[ -n "$PAYLOAD" ]] || exit 1
printf '1) No token should return 401: '
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/api/private/catalog"
echo '2) Login against Identity module using DB-backed roles/permissions'
REPLY="$(curl -fsS "$BASE/api/public/auth/login" -H 'Content-Type: application/json' \
    --data-binary "$PAYLOAD")"
TOKEN="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])' <<< "$REPLY")"
echo '3) Create catalog item (201)'
SKU="DEMO$(date +%s)"
REPLY="$(curl -fsS -X POST "$BASE/api/private/catalog" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' --data-binary "{\"sku\":\"$SKU\",\"name\":\"  A trimmed item  \"}")"
ID="$(python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["id"])' <<< "$REPLY")"
printf 'Item ID: %s\n' "$ID"
echo '4) Query Page<T> and inspect pagination'
curl -fsS "$BASE/api/private/catalog?page=0&size=5" -H "Authorization: Bearer $TOKEN"; echo
echo '5) Logout; previous bearer should be rejected by backend'
curl -fsS -X POST "$BASE/api/private/auth/logout" -H "Authorization: Bearer $TOKEN" >/dev/null
curl -sS -o /dev/null -w 'After logout http=%{http_code}\n' \
    "$BASE/api/private/catalog/$ID" -H "Authorization: Bearer $TOKEN"
