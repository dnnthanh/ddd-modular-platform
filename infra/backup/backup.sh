#!/bin/sh
# Publish only complete, readable archives. Failed dumps never prune old backups.
set -eu
umask 077
BACKUP_DIR=${BACKUP_DIR:-/backups}
BACKUP_RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-7}
case "$BACKUP_RETENTION_DAYS" in ''|*[!0-9]*|0) echo 'Invalid backup retention' >&2; exit 1;; esac
mkdir -p "$BACKUP_DIR"
backup_once() (
  # Serialize scheduled/manual calls. flock is included in postgres:18.6-alpine.
  exec 9>"$BACKUP_DIR/.backup.lock"
  flock -x 9
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  name="modulardb-$stamp-$$.dump"
  final="$BACKUP_DIR/$name"
  [ ! -e "$final" ] && [ ! -e "$final.sha256" ] || exit 1
  tmp="$BACKUP_DIR/.$name.$$.partial"
  check="$tmp.sha256"
  marker="$BACKUP_DIR/.last-success.$$.partial"
  outbox="$BACKUP_DIR/outbox"
  mkdir -p "$outbox"
  chmod 700 "$outbox"
  published=0
  completed=0
  write_event() {
    status=$1
    event="$outbox/$(date -u +%Y%m%dT%H%M%SZ)-$$-$status.json"
    event_tmp="$event.partial"
    # name is generated locally and contains no user input or error output.
    printf '{"id":"%s","status":"%s","filename":"%s"}\n' "${event##*/}" "$status" "$name" > "$event_tmp"
    chmod 600 "$event_tmp"
    mv "$event_tmp" "$event"
  }
  # A fresh `sh backup.sh once` runs this trap for every unhandled command
  # failure, including a failed validation, publish, or cleanup operation.
  trap 'rc=$?; rm -f "$tmp" "$check" "$marker"; if [ "$published" = 0 ]; then rm -f "$final" "$final.sha256"; fi; if [ "$completed" = 0 ]; then write_event failure || :; fi; exit "$rc"' EXIT HUP INT TERM
  pg_dump --format=custom --no-owner --no-acl --file="$tmp" || exit 1
  pg_restore --list "$tmp" >/dev/null || exit 1
  hash=$(sha256sum "$tmp" | cut -d ' ' -f 1)
  printf '%s  %s\n' "$hash" "$name" > "$check"
  mv "$tmp" "$final"
  mv "$check" "$final.sha256"
  (cd "$BACKUP_DIR" && sha256sum -c "$name.sha256" >/dev/null)
  printf '%s\n' "$name" > "$marker"
  chmod 600 "$marker"
  mv "$marker" "$BACKUP_DIR/.last-success"
  published=1
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'modulardb-*.dump*' -mmin "+$((BACKUP_RETENTION_DAYS * 1440))" -exec rm -f {} \;
  write_event success
  completed=1
  printf '%s Backup completed: %s\n' "$(date -u +%FT%TZ)" "$name"
)
if [ "${1:-schedule}" = once ]; then backup_once; exit; fi
# Invoke a new shell: OR-tested functions suppress errexit in their whole body.
# Track the absolute next boundary before dumping; catch up if a long dump crosses it.
next=$((($(date +%s) / 3600 + 1) * 3600))
sh "$0" once || echo 'Initial database backup failed' >&2
while :; do
  now=$(date +%s)
  if [ "$now" -lt "$next" ]; then sleep "$((next - now))" & wait $!; fi
  # Missed slots are coalesced into one catch-up rather than overlapping dumps.
  next=$((($(date +%s) / 3600 + 1) * 3600))
  sh "$0" once || echo 'Hourly database backup failed' >&2
done
