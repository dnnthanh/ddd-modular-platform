#!/bin/sh
# Run the scheduler and its small notifier in one container. Both processes run
# as the configured container user, so the notifier state stays in /backups.
set -eu

python3 -u /backup-app/notifier_loop.py &
notifier_pid=$!
sh /backup-app/backup.sh &
backup_pid=$!

stop() {
  kill -TERM "$backup_pid" "$notifier_pid" 2>/dev/null || :
  wait "$backup_pid" 2>/dev/null || :
  wait "$notifier_pid" 2>/dev/null || :
  exit 0
}
trap stop INT TERM

if wait "$backup_pid"; then
  backup_status=0
else
  backup_status=$?
fi
kill -TERM "$notifier_pid" 2>/dev/null || :
wait "$notifier_pid" 2>/dev/null || :
exit "$backup_status"
