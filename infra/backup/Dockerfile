FROM postgres:18.6-alpine

# The notifier uses only Python's standard library. Keeping it in the backup
# image avoids a second always-running container just to send backup status.
RUN apk add --no-cache python3

COPY infra/backup/backup.sh infra/backup/health.sh infra/backup/launcher.sh infra/backup/notifier_loop.py /backup-app/
COPY infra/backup/notifier.py /backup-app/notifier.py
COPY infra/lgtm/resources/backup-notification-template.json /backup-app/backup-notification-template.json

RUN chmod 0555 /backup-app/backup.sh /backup-app/health.sh /backup-app/launcher.sh /backup-app/notifier_loop.py
