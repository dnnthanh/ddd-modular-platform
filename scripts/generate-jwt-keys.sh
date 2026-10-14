#!/usr/bin/env bash
# One-time LOCAL/DEV RSA generation. Never overwrite or rotate an existing pair.
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$ROOT/secrets/jwt"
command -v openssl >/dev/null || { echo 'openssl is required' >&2; exit 1; }
mkdir -p "$DIR"
chmod 700 "$DIR"
if [[ -e "$DIR/private.pem" || -e "$DIR/public.pem" ]]; then
  if [[ -f "$DIR/private.pem" && -f "$DIR/public.pem" ]]; then
    echo "Keypair already exists at $DIR; no keys changed."
    exit 0
  fi
  echo 'Partial keypair found; resolve manually (refusing implicit key rotation).' >&2
  exit 1
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out "$DIR/private.pem"
openssl pkey -in "$DIR/private.pem" -pubout -out "$DIR/public.pem"
chmod 600 "$DIR/private.pem"
chmod 644 "$DIR/public.pem"
echo "Generated local-only RSA keys in $DIR"
echo 'Keep private.pem out of Git, Docker images and shared archives.'
