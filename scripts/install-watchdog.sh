#!/usr/bin/env bash
# Optional Linux installation; run explicitly with sudo. Never uses a mutable checkout at runtime.
set -Eeuo pipefail
[[ $(id -u) == 0 ]] || { echo 'Run with sudo on Linux.' >&2; exit 1; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
install -d -o root -g root -m 0755 /usr/local/libexec/ddd-platform
install -o root -g root -m 0755 "$ROOT/scripts/health-watchdog.py" /usr/local/libexec/ddd-platform/health-watchdog.py
install -o root -g root -m 0644 "$ROOT/infra/systemd/ddd-watchdog.service.example" /etc/systemd/system/ddd-watchdog.service
systemctl daemon-reload
systemctl enable --now ddd-watchdog.service
