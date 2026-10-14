#!/usr/bin/env bash
# Local source -> Docker image -> Compose containers.
# Docker health works independently; file logs are an optional fallback profile.
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ENV_FILE="$ROOT/.env"
STATE_DIR="$ROOT/.deploy"
STATE_FILE="$STATE_DIR/state.env"
HISTORY_DIR="$STATE_DIR/history"

help() { cat <<'USAGE'
DDD Modular Monolith — source-based deployment (Docker Compose)
  ./scripts/deploy.sh init                 prepare .env only (NOT RSA keys)
  ./scripts/deploy.sh alerts               reload notification resources without Java build
  ./scripts/deploy.sh up [N]                 build CURRENT SOURCE and start N app instances
  ./scripts/deploy.sh scale N                change replica count (no source rebuild)
  ./scripts/deploy.sh restart app|gateway|eureka|all
  ./scripts/deploy.sh down                   stop services, preserve volumes
  ./scripts/deploy.sh help

When code changes: run './scripts/deploy.sh up' again. No image-tag deploy or rollback.
Operations: docker compose ps; docker compose logs. Monitoring is enabled by .env.
Optional file fallback: Spring profile file-logs writes under .logs/.
USAGE
}
err(){ echo "ERROR: $*" >&2; exit 1; }
need(){ command -v "$1" >/dev/null || err "missing command: $1"; }
valid_num(){ [[ "$1" =~ ^[1-9][0-9]*$ ]] && (( 10#$1 <= 50 )) || err 'replica count must be 1..50'; }
setup_dirs(){
  local log_dir
  mkdir -p "$HISTORY_DIR" "$ROOT/.backups/postgres" \
    "$ROOT/.logs/gateway" "$ROOT/.logs/eureka" "$ROOT/.logs/modular-app"
  chmod 700 "$STATE_DIR" "$HISTORY_DIR" "$ROOT/.backups/postgres"
  for log_dir in "$ROOT/.logs/gateway" "$ROOT/.logs/eureka" "$ROOT/.logs/modular-app"; do
    chmod 700 "$log_dir"
  done
}

init(){
  need python3
  setup_dirs
  if [[ ! -f "$ENV_FILE" ]]; then
    cp .env.example "$ENV_FILE"
    echo 'Created .env with local-only defaults.'
  fi
  python3 - "$ENV_FILE" <<'PY'
import secrets,sys
p=sys.argv[1]
with open(p, encoding='utf-8') as f: s=f.read()
keys={
  'DB_PASSWORD':secrets.token_urlsafe(36),
  'DB_RUN_PASSWORD':secrets.token_urlsafe(36),
  'DB_BACKUP_PASSWORD':secrets.token_urlsafe(36),
  'GRAFANA_ADMIN_PASSWORD':secrets.token_urlsafe(36),
  'BOOTSTRAP_ADMIN_PASSWORD':secrets.token_urlsafe(24),
}
lines=s.splitlines()
placeholders={'', 'CHANGE_ME', 'example-change-me-2026'}
found=set()
for i,line in enumerate(lines):
  for name,secret in keys.items():
    if line.startswith(name+'='):
      found.add(name)
      if line.partition('=')[2] in placeholders:
        lines[i]=name+'='+secret
for name,secret in keys.items():
  if name not in found: lines.append(name+'='+secret)
deprecated={
  'DB_CLUSTER_ADMIN_PASSWORD', 'DB_MIGRATOR_PASSWORD', 'DB_APP_PASSWORD',
  'DB_EXPORTER_PASSWORD',
}
lines=[line for line in lines if line.partition('=')[0] not in deprecated]
with open(p,'w',encoding='utf-8') as f: f.write('\n'.join(lines)+'\n')
PY
  # A 0600 private PEM file is not readable by a different UID inside a
  # rootless Java container. Map dev container user to the host key owner.
  # Production should use a dedicated secret mount with explicit ownership/ACL.
  python3 - "$ENV_FILE" "$(id -u)" "$(id -g)" <<'PYUID'
import sys
path, uid, gid = sys.argv[1:]
with open(path, encoding='utf-8') as f:
    lines = f.read().splitlines()
for name, value in [('APP_UID', uid), ('APP_GID', gid)]:
    matches = [i for i, line in enumerate(lines) if line.startswith(name + '=')]
    if not matches:
        lines.append(name + '=' + value)
    elif not lines[matches[-1]].partition('=')[2].strip():
        lines[matches[-1]] = name + '=' + value
with open(path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
PYUID
  chmod 600 "$ENV_FILE"
  if [[ ! -f "$STATE_FILE" ]]; then
    printf 'APP_REPLICAS=3\n' > "$STATE_FILE"
  fi
  chmod 600 "$STATE_FILE"
  echo 'Initialized .env. First run: ./scripts/generate-jwt-keys.sh; then ./scripts/deploy.sh up 3.'
}

load(){
  [[ -f "$ENV_FILE" ]] || err 'Missing .env; run ./scripts/deploy.sh init first.'
  [[ -f "$STATE_FILE" ]] || err 'Missing state.env; run init first.'
  # Read only an integer. Never execute operator-provided state as shell code.
  APP_REPLICAS="$(python3 - "$ENV_FILE" "$STATE_FILE" <<'PY_REPLICAS'
import pathlib, sys
for name in sys.argv[1:]:
    matches = [line.split('=', 1)[1].strip().strip("\"'")
               for line in pathlib.Path(name).read_text().splitlines()
               if line.startswith('APP_REPLICAS=')]
    if matches:
        print(matches[-1])
        break
PY_REPLICAS
)"
  valid_num "$APP_REPLICAS"
  export APP_REPLICAS
  GIT_REVISION="$(git rev-parse --short HEAD 2>/dev/null || printf 'unversioned')"
  if command -v git >/dev/null && git rev-parse --is-inside-work-tree >/dev/null 2>&1 \
      && [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
    GIT_REVISION="${GIT_REVISION}-dirty"
  fi
  export GIT_REVISION
}
compose(){ docker compose --project-name ddd-platform --env-file "$ENV_FILE" -f "$ROOT/compose.yaml" "$@"; }
write_replicas(){
  valid_num "$1"
  printf 'APP_REPLICAS=%s\n' "$1" > "${STATE_FILE}.tmp"
  mv "${STATE_FILE}.tmp" "$STATE_FILE"
  chmod 600 "$STATE_FILE"
  python3 - "$ENV_FILE" "$1" <<'PY_SAVE_REPLICAS'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
lines = path.read_text().splitlines()
lines = [line for line in lines if not line.startswith('APP_REPLICAS=')]
lines.append('APP_REPLICAS=' + sys.argv[2])
path.write_text('\n'.join(lines) + '\n')
path.chmod(0o600)
PY_SAVE_REPLICAS
  APP_REPLICAS="$1"
  export APP_REPLICAS
}
snapshot(){
  local out="$1" ids
  ids="$(compose ps -aq 2>/dev/null || true)"
  if [[ -z "$ids" ]]; then
    echo '[]' > "$out"
  else
    # Inspect is piped ONLY to a strict whitelist scrubber; env/secrets are not persisted.
    # shellcheck disable=SC2086
    docker inspect $ids | python3 "$ROOT/scripts/safe_snapshot.py" > "$out"
  fi
}
change(){
  local action="$1" detail="$2"; shift 2
  local stamp before after
  stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
  before="$HISTORY_DIR/${stamp}-before.json"
  after="$HISTORY_DIR/${stamp}-after.json"
  snapshot "$before"
  "$@"
  snapshot "$after"
  # Local deployment history keeps these minimal lifecycle records.
  python3 "$ROOT/scripts/deployment_event.py" "$action" "$detail" "$before" "$after" "$HISTORY_DIR/events.jsonl"
}
configure_telegram(){
  # Only examines two named values; never evaluates arbitrary .env shell content.
  python3 - "$ENV_FILE" "$ROOT/infra/lgtm/telegram.yaml.example" \
      "$ROOT/infra/lgtm/provisioning/alerting/telegram.yaml" <<'PY_TELEGRAM'
import pathlib
import shutil
import sys

env_file, template, target = (pathlib.Path(item) for item in sys.argv[1:])
# Resource is authoritative; generated provisioning file stays out of Git.
shutil.copyfile(template.parent / "resources/notification-templates.yaml",
                target.parent / "notification-templates.yaml")
values = {}
for line in env_file.read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        name, value = line.split("=", 1)
        if name in {"TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID"}:
            values[name] = value.strip().strip("\"'")
token = values.get("TELEGRAM_BOT_TOKEN", "")
chat = values.get("TELEGRAM_CHAT_ID", "")
if bool(token) != bool(chat):
    raise SystemExit("Set both TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID (or neither) in .env")
if token:
    shutil.copyfile(template, target)  # Template has references ONLY, not secrets.
    print("Telegram alerting enabled via Grafana provisioning.")
else:
    target.unlink(missing_ok=True)
    print("Telegram alerting disabled (no credentials provided).")
PY_TELEGRAM
}

do_up(){
  configure_telegram
  local n="${1:-$APP_REPLICAS}"
  valid_num "$n"
  [[ -f "$ROOT/secrets/jwt/private.pem" && -f "$ROOT/secrets/jwt/public.pem" ]] \
    || err 'JWT keys missing. Run ./scripts/generate-jwt-keys.sh once before deploy.'
  # Build first so a build failure does not change the desired replica count.
  # Re-using the stable :local image tag is intentional; Grafana compares real image SHA.
  compose build gateway eureka modular-app postgres-backup redis-exporter
  # An existing PostgreSQL volume does not rerun docker-entrypoint-initdb.d.
  # Provision accounts through the database container; passwords stay in its
  # environment and never appear in a host command line.
  compose up -d postgres
  compose exec -T postgres sh -c 'exec psql --no-psqlrc --set=ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
    < "$ROOT/infra/postgres/init/provision-roles.psql"
  # Preserve legacy revoked tokens before switching the shared typed cache.
  compose up -d redis
  compose stop modular-app
  "$ROOT/scripts/migrate-revoked-tokens.sh"
  write_replicas "$n"
  change up "source-build replicas=$n revision=$GIT_REVISION" compose up -d --no-build --scale "modular-app=$n"
}
do_scale(){
  local n="${1:?usage: scale N}"
  valid_num "$n"
  docker image inspect ddd/modular-app:local >/dev/null 2>&1 || err 'No local modular-app image; run up first.'
  write_replicas "$n"
  change scale "replicas=$n" compose up -d --no-build --no-deps --scale "modular-app=$n" modular-app
}
service_name(){
  case "$1" in
    app) echo modular-app;;
    gateway|eureka) echo "$1";;
    *) err 'expected app|gateway|eureka|all';;
  esac
}
do_restart(){
  local svc="${1:-app}"
  if [[ "$svc" == all ]]; then
    change restart all compose restart
  else
    local name
    name="$(service_name "$svc")"
    change restart "$name" compose restart "$name"
  fi
}
main(){
  local cmd="${1:-help}"; shift || true
  if [[ "$cmd" == help ]]; then help; exit 0; fi
  if [[ "$cmd" == init ]]; then init; exit 0; fi
  need docker; need python3
  docker compose version >/dev/null 2>&1 || err 'Docker Compose v2 required'
  setup_dirs
  load
  case "$cmd" in
    up) do_up "$@";;
    alerts) configure_telegram; compose up -d --no-deps --force-recreate lgtm;;
    scale) do_scale "$@";;
    restart) do_restart "$@";;
    down) change down 'keep volumes' compose down;;
    *) help; err "unknown command: $cmd";;
  esac
}
main "$@"
