#!/bin/sh
set -eu
BACKUP_DIR=${BACKUP_DIR:-/backups}
# Marker is published only after archive + checksum are complete and verified.
test -n "$(find "$BACKUP_DIR/.last-success" -mmin -125 -print 2>/dev/null)"
IFS= read -r name < "$BACKUP_DIR/.last-success"
case "$name" in modulardb-*.dump) ;; *) exit 1;; esac
case "$name" in */*) exit 1;; esac
test -s "$BACKUP_DIR/$name"
test -s "$BACKUP_DIR/$name.sha256"
(cd "$BACKUP_DIR" && sha256sum -c "$name.sha256" >/dev/null)
