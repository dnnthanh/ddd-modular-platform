#!/usr/bin/env bash
# Run with app replicas stopped before switching from the legacy blacklist to CacheHelper.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LUA="$(cat <<'LUA'
local cursor='0'; local n=0
repeat
  local r=redis.call('SCAN',cursor,'MATCH','auth:revoked:*','COUNT',100)
  cursor=r[1]
  for _,k in ipairs(r[2]) do
    local ttl=redis.call('PTTL',k)
    if ttl>0 then
      local jti=string.sub(k,string.len('auth:revoked:')+1)
      redis.call('SET','modular-app:auth:revoked-jwt:'..jti,'true','PX',ttl,'NX')
      n=n+1
    end
  end
until cursor=='0'
return n
LUA
)"
docker compose --project-name ddd-platform --env-file "$ROOT/.env" -f "$ROOT/compose.yaml" exec -T redis redis-cli EVAL "$LUA" 0
