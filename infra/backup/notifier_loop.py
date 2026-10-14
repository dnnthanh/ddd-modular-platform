"""Run the shared backup outbox notifier beside the backup scheduler."""
import os
import time
import subprocess
from pathlib import Path

from notifier import BackupNotifier


def main():
    notifier = BackupNotifier(
        outbox=os.environ.get("BACKUP_OUTBOX", "/backups/outbox"),
        state_dir=os.environ.get("BACKUP_NOTIFIER_STATE_DIR", "/backups/notifier-state"),
        token=os.environ.get("TELEGRAM_BOT_TOKEN", ""),
        chat_id=os.environ.get("TELEGRAM_CHAT_ID", ""),
        template_path=os.environ.get(
            "BACKUP_NOTIFICATION_TEMPLATE", "/backup-app/backup-notification-template.json"
        ),
        api_base=os.environ.get("TELEGRAM_API_BASE", "https://api.telegram.org"),
    )
    # Verify health separately from sending events. No metrics/trace dependency.
    # A long dump gets the same 125-minute grace as the existing backup health policy.
    last_health_check = time.monotonic()
    while True:
        notifier.poll()
        if time.monotonic() - last_health_check >= 900:
            last_health_check = time.monotonic()
            try:
                result = subprocess.run(["sh", "/backup-app/health.sh"],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                        timeout=60, check=False)
                if result.returncode:
                    marker = Path(os.environ.get("BACKUP_DIR", "/backups")) / ".last-success"
                    filename = marker.read_text().strip() if marker.exists() else ""
                    if filename.startswith("modulardb-") and filename.endswith(".dump") and "/" not in filename:
                        notifier.notify_stale(filename)
                    else:
                        notifier.notify_missing()
            except (OSError, subprocess.TimeoutExpired):
                notifier.notify_missing()
        time.sleep(15)


if __name__ == "__main__":
    main()
