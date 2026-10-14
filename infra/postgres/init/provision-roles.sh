#!/bin/sh
# Idempotently establish database service accounts. The caller supplies all
# credentials through environment variables; this script never prints them.
set -eu

# Docker's official entrypoint executes this script only on a fresh volume.
# deploy.sh streams the companion SQL to psql in the existing database
# container for an existing volume.
PGHOST=${PGHOST:-/var/run/postgresql}
PGDATABASE=${PGDATABASE:-${POSTGRES_DB:?POSTGRES_DB is required}}
PGUSER=${PGUSER:-${POSTGRES_USER:?POSTGRES_USER is required}}
PGPASSWORD=${PGPASSWORD:-${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}}
export PGHOST PGDATABASE PGUSER PGPASSWORD
: "${DB_RUN_PASSWORD:?DB_RUN_PASSWORD is required}"
: "${DB_BACKUP_PASSWORD:?DB_BACKUP_PASSWORD is required}"

attempt=0
until pg_isready --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname="$PGDATABASE" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo 'PostgreSQL did not become ready for role provisioning.' >&2
    exit 1
  fi
  sleep 2
done

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec psql --no-psqlrc --set=ON_ERROR_STOP=1 --dbname="$PGDATABASE" \
  --file="$SCRIPT_DIR/provision-roles.psql"
