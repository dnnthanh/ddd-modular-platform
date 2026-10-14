#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
case "${1:-format}" in
  format|apply)
    exec mvn -f "$ROOT/pom.xml" spotless:apply
    ;;
  check)
    exec mvn -f "$ROOT/pom.xml" spotless:check
    ;;
  -h|--help|help)
    printf '%s\n' 'Usage: ./scripts/format.sh [format|check]' \
      'format -> mvn spotless:apply; check -> mvn spotless:check' \
      'Spotless uses Google Java Format; IDEA XML remains for IDE formatting.'
    ;;
  *)
    printf '%s\n' 'Unknown mode (expected format or check)' >&2
    exit 2
    ;;
esac
